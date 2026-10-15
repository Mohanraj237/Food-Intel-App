package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.Brand;
import com.familyfood.catalog.domain.BrandAlias;
import com.familyfood.catalog.domain.Category;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductAllergen;
import com.familyfood.catalog.domain.ProductBarcode;
import com.familyfood.catalog.domain.ProductIngredient;
import com.familyfood.catalog.domain.ProductNutrient;
import com.familyfood.catalog.domain.ProductSource;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.infra.AdditiveRepository;
import com.familyfood.catalog.infra.BrandAliasRepository;
import com.familyfood.catalog.infra.BrandRepository;
import com.familyfood.catalog.infra.CategoryRepository;
import com.familyfood.catalog.infra.ProductAllergenRepository;
import com.familyfood.catalog.infra.ProductBarcodeRepository;
import com.familyfood.catalog.infra.ProductIngredientRepository;
import com.familyfood.catalog.infra.ProductNutrientRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductSourceRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The single write path into the catalogue (D-014/D-015): creates a new {@link Product}
 * and its first {@link ProductVersion}, with per-field provenance on every nutrient,
 * ingredient and allergen row. Used by the Open Food Facts bulk seed
 * ({@code catalog.seed.OpenFoodFactsSeedRunner}) and by ops-review contribution approval
 * ({@code ops.application.ContributionReviewService}) — one implementation, so both honour
 * the same invariants rather than each hand-writing the entity graph.
 *
 * <p>This service only ever creates a brand-new product. It does not attempt to match an
 * existing product by GTIN/name and add a superseding version — every caller today
 * (a first-time bulk import into an empty catalogue; an ops approval whose precondition is
 * "no active product already exists for this barcode") only ever needs a new product.
 * Filling previously-absent facts into an existing current version is
 * {@link ProductEnrichmentService}'s job; a correction/merge flow is separate, more careful
 * work and out of scope here.
 *
 * <p>Since V15 (D-059) it also marks additive tokens ({@link AdditiveDetector}), records the
 * processing level / diet flag / FVNL estimate the source supplied, seeds the known aliases
 * for a brand it creates, and publishes {@link ProductVersionCreated} after commit so
 * derived data (scores) is recomputed.
 */
@Service
public class ProductIngestionService {

    static final String NUTRIENT_FVNL_PCT = "FVNL_PCT";
    static final String REASON_INGESTED = "INGESTED";

    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final ProductSourceRepository sources;
    private final ProductNutrientRepository nutrients;
    private final ProductIngredientRepository ingredients;
    private final ProductAllergenRepository allergens;
    private final ProductBarcodeRepository barcodes;
    private final BrandRepository brands;
    private final BrandAliasRepository brandAliases;
    private final CategoryRepository categories;
    private final AdditiveRepository additives;
    private final ApplicationEventPublisher events;

    public ProductIngestionService(ProductRepository products,
                                    ProductVersionRepository versions,
                                    ProductSourceRepository sources,
                                    ProductNutrientRepository nutrients,
                                    ProductIngredientRepository ingredients,
                                    ProductAllergenRepository allergens,
                                    ProductBarcodeRepository barcodes,
                                    BrandRepository brands,
                                    BrandAliasRepository brandAliases,
                                    CategoryRepository categories,
                                    AdditiveRepository additives,
                                    ApplicationEventPublisher events) {
        this.products = products;
        this.versions = versions;
        this.sources = sources;
        this.nutrients = nutrients;
        this.ingredients = ingredients;
        this.allergens = allergens;
        this.barcodes = barcodes;
        this.brands = brands;
        this.brandAliases = brandAliases;
        this.categories = categories;
        this.additives = additives;
        this.events = events;
    }

    @Transactional
    public UUID ingestNewProduct(IngestProductCommand cmd) {
        UUID brandId = cmd.brandName() == null || cmd.brandName().isBlank()
                ? null : resolveBrand(cmd.brandName());
        UUID categoryId = cmd.categoryCode() == null
                ? null
                : categories.findByCode(cmd.categoryCode()).map(Category::getId).orElse(null);

        Product product = Product.create(
                cmd.name(), normalize(cmd.name()), brandId,
                cmd.packSizeValue(), cmd.packSizeUnit(), categoryId, ProductStatus.ACTIVE);
        products.save(product);

        ProductVersion version = ProductVersion.create(
                product.getId(), 1, cmd.versionStatus(), true,
                cmd.servingSizeG(), cmd.servingSizeDeclared(), null, null,
                cmd.ingredientsRawText(), cmd.ingredientsRawText() == null ? null : "en",
                null, cmd.captureMethod(), cmd.capturedByUserId(), cmd.dataQuality());
        if (cmd.verifiedByUserId() != null) {
            version.markVerified(cmd.verifiedByUserId(), cmd.verifiedAt());
        }

        ProductSource source = ProductSource.create(
                version.getId(), cmd.sourceType(), cmd.sourceRef(), cmd.licenseNote(),
                cmd.capturedByUserId(), null);

        // Enrichment facts carry the same provenance row as the rest of this version's facts.
        version.fillNovaGroup(cmd.novaGroup(), source.getId());
        version.fillDietFlag(cmd.dietFlag(), cmd.dietFlagEstimated(), source.getId());
        versions.save(version);
        sources.save(source);

        int position = 1;
        for (String token : cmd.ingredientTokens()) {
            Optional<String> additiveCode = AdditiveDetector.primaryCode(token);
            String linkedCode = additiveCode.filter(additives::existsById).orElse(null);
            ingredients.save(ProductIngredient.create(
                    version.getId(), null, token, position++, additiveCode.isPresent(), linkedCode, source.getId()));
        }

        NutrientBasis basis = null;
        for (IngestProductCommand.NutrientValue n : cmd.nutrients()) {
            nutrients.save(ProductNutrient.create(
                    version.getId(), n.nutrientCode(), n.amountPer100(), null, n.unit(),
                    n.basisPer100(), n.basisPer100(), false, null, source.getId()));
            if (basis == null) {
                basis = n.basisPer100();
            }
        }
        if (cmd.fvnlPctEstimated() != null) {
            // Always an estimate from the ingredient list, never a declared label value.
            NutrientBasis fvnlBasis = basis == null ? NutrientBasis.PER_100G : basis;
            nutrients.save(ProductNutrient.create(
                    version.getId(), NUTRIENT_FVNL_PCT, cmd.fvnlPctEstimated(), null, "%",
                    fvnlBasis, fvnlBasis, true, null, source.getId()));
        }

        for (IngestProductCommand.AllergenValue a : cmd.allergens()) {
            allergens.save(ProductAllergen.create(
                    version.getId(), a.allergenCode(), a.declarationType(), source.getId(), null));
        }

        if (cmd.gtin() != null && !cmd.gtin().isBlank() && cmd.gtinType() != null) {
            barcodes.save(ProductBarcode.create(
                    product.getId(), cmd.gtin(), cmd.gtinType(), true, cmd.sourceType().name()));
        }

        events.publishEvent(new ProductVersionCreated(product.getId(), version.getId(), REASON_INGESTED));
        return product.getId();
    }

    private UUID resolveBrand(String rawName) {
        String trimmed = rawName.trim();
        String normalized = normalize(trimmed);
        return brands.findByNormalizedName(normalized)
                .map(Brand::getId)
                .orElseGet(() -> {
                    Brand brand = brands.save(Brand.create(trimmed, normalized, null, "IN"));
                    for (String alias : BrandAliases.aliasesFor(normalized)) {
                        brandAliases.save(BrandAlias.create(brand.getId(), alias, normalize(alias)));
                    }
                    return brand.getId();
                });
    }

    /** Mirrors the lower-case/unaccented matching form the database uses for brand names. */
    public static String normalize(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFKD);
        String stripped = decomposed.replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
