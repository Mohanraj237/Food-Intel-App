package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.ProductChangeLog;
import com.familyfood.catalog.domain.ProductIngredient;
import com.familyfood.catalog.domain.ProductNutrient;
import com.familyfood.catalog.domain.ProductSource;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.infra.AdditiveRepository;
import com.familyfood.catalog.infra.ProductChangeLogRepository;
import com.familyfood.catalog.infra.ProductIngredientRepository;
import com.familyfood.catalog.infra.ProductNutrientRepository;
import com.familyfood.catalog.infra.ProductSourceRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fills previously-absent facts into a product's <em>current</em> version (D-059) — the one
 * sanctioned way to add to an existing version:
 *
 * <ul>
 *   <li><b>null → value only.</b> A fact the version already holds is never touched; a fact
 *       the source does not supply stays absent. No value is ever overwritten (D-014).
 *   <li><b>Provenance per fill.</b> Every filled fact points at one new
 *       {@code product_source} row for this pass (D-015), and one {@code product_change_log}
 *       row lists exactly which fields were filled ({@code materially_changed = false} — a
 *       fill is not a reformulation).
 *   <li><b>Derived data follows.</b> The product's cached detail is evicted and
 *       {@link ProductVersionCreated} is published so the score is recomputed after commit.
 * </ul>
 *
 * Returns the list of field names it filled; an empty list means the call was a no-op and
 * nothing was written (no source row, no change-log row, no event).
 */
@Service
public class ProductEnrichmentService {

    private final ProductVersionRepository versions;
    private final ProductSourceRepository sources;
    private final ProductNutrientRepository nutrients;
    private final ProductIngredientRepository ingredients;
    private final ProductChangeLogRepository changeLog;
    private final AdditiveRepository additives;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProductEnrichmentService(ProductVersionRepository versions,
                                    ProductSourceRepository sources,
                                    ProductNutrientRepository nutrients,
                                    ProductIngredientRepository ingredients,
                                    ProductChangeLogRepository changeLog,
                                    AdditiveRepository additives,
                                    ApplicationEventPublisher events,
                                    Clock clock) {
        this.versions = versions;
        this.sources = sources;
        this.nutrients = nutrients;
        this.ingredients = ingredients;
        this.changeLog = changeLog;
        this.additives = additives;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(cacheNames = CatalogCaches.PRODUCT, key = "#productId")
    public List<String> enrichCurrentVersion(UUID productId, EnrichmentFacts facts) {
        Optional<ProductVersion> maybeVersion = versions.findByProductIdAndCurrentTrue(productId);
        if (maybeVersion.isEmpty()) {
            return List.of();
        }
        ProductVersion version = maybeVersion.get();

        // The source row is created up front so filled facts can point at it, and discarded
        // (never saved) if nothing ends up being filled.
        ProductSource source = ProductSource.create(version.getId(), facts.sourceType(), facts.sourceRef(),
                facts.licenseNote(), null, null);
        List<String> filled = new ArrayList<>();

        if (version.fillNovaGroup(facts.novaGroup(), source.getId())) {
            filled.add("nova_group");
        }
        if (version.fillDietFlag(facts.dietFlag(), facts.dietFlagEstimated(), source.getId())) {
            filled.add("diet_flag");
        }
        if (version.fillServing(facts.servingSizeG(), facts.servingSizeDeclared())) {
            filled.add("serving_size");
        }

        List<ProductNutrient> existing = nutrients.findByProductVersionId(version.getId());
        Set<String> existingCodes = existing.stream().map(ProductNutrient::getNutrientCode).collect(Collectors.toSet());
        NutrientBasis basis = existing.stream().map(ProductNutrient::getBasisPer100).filter(b -> b != null).findFirst()
                .orElse(null);
        List<ProductNutrient> added = new ArrayList<>();
        for (IngestProductCommand.NutrientValue n : facts.nutrients()) {
            if (existingCodes.contains(n.nutrientCode()) || n.amountPer100() == null) {
                continue;
            }
            // A version's facts share one per-100 basis; a source declaring on the other
            // basis cannot be merged into it without a conversion this project does not do.
            if (basis != null && n.basisPer100() != null && basis != n.basisPer100()) {
                continue;
            }
            NutrientBasis rowBasis = n.basisPer100() == null ? (basis == null ? NutrientBasis.PER_100G : basis) : n.basisPer100();
            added.add(ProductNutrient.create(version.getId(), n.nutrientCode(), n.amountPer100(), null, n.unit(),
                    rowBasis, rowBasis, false, null, source.getId()));
            existingCodes.add(n.nutrientCode());
            filled.add("nutrient:" + n.nutrientCode());
        }
        if (facts.fvnlPctEstimated() != null && !existingCodes.contains(ProductIngestionService.NUTRIENT_FVNL_PCT)) {
            NutrientBasis rowBasis = basis == null ? NutrientBasis.PER_100G : basis;
            added.add(ProductNutrient.create(version.getId(), ProductIngestionService.NUTRIENT_FVNL_PCT,
                    facts.fvnlPctEstimated(), null, "%", rowBasis, rowBasis, true, null, source.getId()));
            filled.add("nutrient:" + ProductIngestionService.NUTRIENT_FVNL_PCT);
        }

        List<ProductIngredient> marked = new ArrayList<>();
        for (ProductIngredient ingredient : ingredients.findByProductVersionIdOrderByPositionAsc(version.getId())) {
            Optional<String> code = AdditiveDetector.primaryCode(ingredient.getRawText());
            if (code.isEmpty()) {
                continue;
            }
            String linked = code.filter(additives::existsById).orElse(null);
            if (ingredient.markAdditive(linked)) {
                marked.add(ingredient);
            }
        }
        if (!marked.isEmpty()) {
            filled.add("additives:" + marked.size());
        }

        if (filled.isEmpty()) {
            return List.of();
        }

        sources.save(source);
        versions.save(version);
        nutrients.saveAll(added);
        ingredients.saveAll(marked);

        Map<String, Object> changedFields = new LinkedHashMap<>();
        for (String field : filled) {
            changedFields.put(field, "filled");
        }
        changeLog.save(ProductChangeLog.record(productId, version.getId(), version.getId(), changedFields,
                ProductChangeLog.REASON_OFF_ENRICHMENT, false, null, clock.instant()));

        events.publishEvent(new ProductVersionCreated(productId, version.getId(), ProductChangeLog.REASON_OFF_ENRICHMENT));
        return List.copyOf(filled);
    }
}
