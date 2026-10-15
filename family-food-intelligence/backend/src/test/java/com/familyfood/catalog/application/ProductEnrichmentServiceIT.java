package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.ProductChangeLog;
import com.familyfood.catalog.domain.ProductIngredient;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.infra.BrandAliasRepository;
import com.familyfood.catalog.infra.BrandRepository;
import com.familyfood.catalog.infra.ProductChangeLogRepository;
import com.familyfood.catalog.infra.ProductIngredientRepository;
import com.familyfood.catalog.infra.ProductNutrientRepository;
import com.familyfood.catalog.infra.ProductSourceRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The null → value enrichment contract (D-014, D-015, D-059): a fact the version holds is
 * never overwritten, every fill carries its own source row and a change-log entry, additive
 * tokens are marked, and a second pass with nothing new writes nothing.
 */
class ProductEnrichmentServiceIT extends IntegrationTestBase {

    @Autowired private ProductIngestionService ingestion;
    @Autowired private ProductEnrichmentService enrichment;
    @Autowired private ProductQueryService productQuery;
    @Autowired private ProductVersionRepository versions;
    @Autowired private ProductNutrientRepository nutrients;
    @Autowired private ProductIngredientRepository ingredients;
    @Autowired private ProductSourceRepository sources;
    @Autowired private ProductChangeLogRepository changeLog;
    @Autowired private BrandRepository brands;
    @Autowired private BrandAliasRepository brandAliases;

    @Test
    @DisplayName("ingestion marks INS tokens as additives and links the ones the reference table knows")
    void ingestionMarksAdditives() {
        UUID productId = ingest("IT Additive Biscuit " + UUID.randomUUID(), null, DietFlag.UNKNOWN,
                List.of("REFINED WHEAT FLOUR (MAIDA) 68%", "SUGAR", "RAISING AGENTS [INS 503(ii), 500(ii)]",
                        "EMULSIFIER (INS 999999)", "ACIDITY REGULATOR (E330)"));
        UUID versionId = versions.findByProductIdAndCurrentTrue(productId).orElseThrow().getId();

        List<ProductIngredient> rows = ingredients.findByProductVersionIdOrderByPositionAsc(versionId);

        assertThat(rows).extracting(ProductIngredient::isAdditive).containsExactly(false, false, true, false, true);
        assertThat(rows.get(2).getAdditiveCode()).isEqualTo("503(ii)");
        assertThat(rows.get(4).getAdditiveCode()).isEqualTo("330");

        var detail = productQuery.getProductUncached(productId);
        assertThat(detail.ingredients().get(2).additiveName()).isEqualTo("Ammonium hydrogen carbonate");
        assertThat(detail.ingredients().get(2).additiveFunction()).contains("raising agent");
        assertThat(detail.ingredients().get(4).additiveName()).isEqualTo("Citric acid");
        assertThat(detail.ingredients().get(0).additiveCode()).isNull();
    }

    @Test
    @DisplayName("enrichment fills only what is missing, records provenance and a non-material change, and is idempotent")
    void enrichmentFillsNullToValueOnly() {
        UUID productId = ingest("IT Enrich " + UUID.randomUUID(), 3, DietFlag.UNKNOWN, List.of("SUGAR"));
        ProductVersion before = versions.findByProductIdAndCurrentTrue(productId).orElseThrow();
        assertThat(before.getNovaGroup()).isEqualTo(3);
        assertThat(before.getDietFlag()).isEqualTo(DietFlag.UNKNOWN);
        int sourcesBefore = sources.findByProductVersionId(before.getId()).size();

        EnrichmentFacts facts = new EnrichmentFacts(
                4,                         // must NOT overwrite the stored 3
                DietFlag.VEG, true,        // UNKNOWN → VEG is a fill
                new BigDecimal("25"), "4 biscuits (25 g)",
                new BigDecimal("12.5"),    // FVNL estimate
                List.of(nutrient("ENERGY_KCAL", "999", "kcal"),   // already declared → untouched
                        nutrient("FIBRE_G", "2.1", "g")),          // new → added
                SourceType.OPEN_FOOD_FACTS, "https://world.openfoodfacts.org/product/it", "attribution");

        List<String> filled = enrichment.enrichCurrentVersion(productId, facts);

        assertThat(filled).containsExactly("diet_flag", "serving_size", "nutrient:FIBRE_G", "nutrient:FVNL_PCT");

        ProductVersion after = versions.findByProductIdAndCurrentTrue(productId).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getNovaGroup()).isEqualTo(3);                    // not overwritten
        assertThat(after.getDietFlag()).isEqualTo(DietFlag.VEG);
        assertThat(after.isDietFlagEstimated()).isTrue();
        assertThat(after.getDietSourceId()).isNotNull();
        assertThat(after.getServingSizeG()).isEqualByComparingTo("25");

        var rows = nutrients.findByProductVersionId(after.getId());
        assertThat(rows).filteredOn(n -> n.getNutrientCode().equals("ENERGY_KCAL"))
                .singleElement().extracting(n -> n.getAmountPer100().stripTrailingZeros().toPlainString()).isEqualTo("454");
        assertThat(rows).filteredOn(n -> n.getNutrientCode().equals("FVNL_PCT"))
                .singleElement().satisfies(n -> {
                    assertThat(n.isEstimated()).isTrue();
                    assertThat(n.getUnit()).isEqualTo("%");
                });
        assertThat(rows).extracting(n -> n.getNutrientCode()).contains("FIBRE_G");

        assertThat(sources.findByProductVersionId(after.getId())).hasSize(sourcesBefore + 1);
        List<ProductChangeLog> log = changeLog.findByProductIdOrderByDetectedAtDesc(productId);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).isMateriallyChanged()).isFalse();
        assertThat(log.get(0).getChangeReason()).isEqualTo("OFF_ENRICHMENT");
        assertThat(log.get(0).getChangedFields()).containsKeys("diet_flag", "nutrient:FIBRE_G");
        assertThat(log.get(0).getFromVersionId()).isEqualTo(after.getId());

        // Second pass with the same facts: nothing left to fill, nothing written.
        assertThat(enrichment.enrichCurrentVersion(productId, facts)).isEmpty();
        assertThat(sources.findByProductVersionId(after.getId())).hasSize(sourcesBefore + 1);
        assertThat(changeLog.findByProductIdOrderByDetectedAtDesc(productId)).hasSize(1);

        var detail = productQuery.getProductUncached(productId);
        assertThat(detail.currentVersion().dietFlag()).isEqualTo("VEG");
        assertThat(detail.currentVersion().dietFlagEstimated()).isTrue();
        assertThat(detail.currentVersion().novaGroup()).isEqualTo(3);
        assertThat(detail.currentVersion().novaSource()).isEqualTo("OPEN_FOOD_FACTS");
    }

    @Test
    @DisplayName("a brand created by ingestion gets its seeded aliases; an existing brand is reused")
    void brandAliasesSeededOnCreate() {
        // "Yoga Bar" is used by no other test class. The shared container keeps rows across
        // classes, and ProductControllerIT inserts "britannia" directly, so this test must
        // never create a brand another test creates.
        assertThat(brands.findByNormalizedName("yoga bar")).isEmpty();
        ingest("IT Alias Product " + UUID.randomUUID(), null, DietFlag.UNKNOWN, List.of("SUGAR"), "Yoga Bar");
        var created = brands.findByNormalizedName("yoga bar").orElseThrow();

        assertThat(brandAliases.findByBrandId(created.getId())).extracting(a -> a.getNormalizedAlias())
                .containsExactly("yogabar");

        ingest("IT Alias Product " + UUID.randomUUID(), null, DietFlag.UNKNOWN, List.of("SUGAR"), "Yoga  Bar");
        assertThat(brands.findByNormalizedName("yoga bar")).get().extracting(b -> b.getId()).isEqualTo(created.getId());
        assertThat(brandAliases.findByBrandId(created.getId())).hasSize(1);
    }

    // ------------------------------------------------------------------------------ fixtures

    private UUID ingest(String name, Integer nova, DietFlag dietFlag, List<String> tokens) {
        return ingest(name, nova, dietFlag, tokens, "TestBrand");
    }

    private UUID ingest(String name, Integer nova, DietFlag dietFlag, List<String> tokens, String brand) {
        IngestProductCommand cmd = new IngestProductCommand(
                name, brand, "BISCUITS", null, null, null, null, null, null,
                String.join(", ", tokens), tokens,
                List.of(nutrient("ENERGY_KCAL", "454", "kcal"), nutrient("TOTAL_SUGAR_G", "25.5", "g")), List.of(),
                SourceType.OPEN_FOOD_FACTS, CaptureMethod.OFF_IMPORT, null, "https://world.openfoodfacts.org/product/it",
                "attribution", VersionStatus.NEEDS_VERIFICATION, DataQuality.RECENT, null, null,
                nova, dietFlag, true, null);
        return ingestion.ingestNewProduct(cmd);
    }

    private static IngestProductCommand.NutrientValue nutrient(String code, String per100, String unit) {
        return new IngestProductCommand.NutrientValue(code, new BigDecimal(per100), unit, NutrientBasis.PER_100G);
    }
}
