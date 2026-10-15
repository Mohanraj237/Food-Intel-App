package com.familyfood.scoring.application;

import com.familyfood.catalog.application.EnrichmentFacts;
import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductEnrichmentService;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.scoring.domain.ProductScore;
import com.familyfood.scoring.domain.RescoreRun;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.ScoringAlgorithm;
import com.familyfood.scoring.infra.ProductScoreRepository;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.scoring.infra.ScoreComponentRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Persisted scores end to end (D-059): a DRAFT {@code POINTS_NET_V2} rule set carrying the
 * transcribed fixture components ({@link PointsNetFixtures}) is scored against real ingested
 * products; the stored row is served while its input hash matches and recomputed when the
 * facts change (enrichment). The ACTIVE {@code v1} set stays empty throughout — the public
 * endpoint keeps answering {@code NOT_YET_SCORED} and never persists a row (D-024).
 */
class ProductScoreServiceIT extends IntegrationTestBase {

    @Autowired private ProductIngestionService ingestion;
    @Autowired private ProductEnrichmentService enrichment;
    @Autowired private ProductQueryService productQuery;
    @Autowired private ProductScoreService productScoreService;
    @Autowired private ProductScoreStore store;
    @Autowired private ProductScoreRepository productScores;
    @Autowired private RuleSetRepository ruleSets;
    @Autowired private ScoreComponentRepository scoreComponents;
    @Autowired private AssessmentOrchestrator orchestrator;
    @Autowired private RescoreService rescoreService;
    @Autowired private Clock clock;

    private RuleSet draftV2;

    @BeforeEach
    void draftRuleSetWithFixtureComponents() {
        // A fresh DRAFT set per test class run — never ACTIVE (uq_rule_set_active), never
        // referenced by production reads. Its components are the transcribed fixture tables.
        draftV2 = ruleSets.save(RuleSet.draft("it-points-net-" + UUID.randomUUID(), ScoringAlgorithm.POINTS_NET_V2,
                PointsNetFixtures.algorithmConfig(),
                "INTEGRATION TEST FIXTURE — transcribed band tables for engine proof only, never activated",
                clock.instant()));
        scoreComponents.saveAll(PointsNetFixtures.allComponents(draftV2.getId()));
    }

    @Test
    @DisplayName("computeAndStore persists a SCORED row with stars, hash and processing level; getOrCompute then serves it")
    void storesAndServesScore() {
        UUID productId = ingestBiscuit("IT Biscuit " + UUID.randomUUID(), true);
        ProductAssessmentInput input = resolvedInput(productId);

        ProductHealthView computed = productScoreService.computeAndStore(input, draftV2);

        assertThat(computed.status()).isEqualTo("SCORED");
        assertThat(computed.stars()).isEqualByComparingTo("1.5");
        assertThat(computed.score()).isEqualTo(22);
        Optional<ProductScore> stored = productScores.findByProductVersionIdAndRuleSetId(input.productVersionId(), draftV2.getId());
        assertThat(stored).isPresent();
        assertThat(stored.get().getStars()).isEqualByComparingTo("1.5");
        assertThat(stored.get().getStatus()).isEqualTo("SCORED");
        assertThat(stored.get().getProcessingLevel()).isEqualTo(4);
        assertThat(stored.get().getInputHash()).isEqualTo(ProductScoreService.inputHash(input));

        ProductHealthView served = productScoreService.getOrCompute(input, draftV2);
        assertThat(served.computedAt()).isEqualTo(computed.computedAt()); // the stored row, not a recompute
        assertThat(served.drivers()).hasSize(7);
        assertThat(served.coverage().missing()).containsExactly("FIBRE_G");
        assertThat(served.processingLevel()).isEqualTo(new ProcessingLevel(4, true, "OPEN_FOOD_FACTS"));
        assertThat(productScores.countByRuleSetId(draftV2.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("an undeclared baseline nutrient is stored as INSUFFICIENT_DATA with the missing inputs named")
    void storesInsufficientData() {
        UUID productId = ingestBiscuit("IT Sparse Biscuit " + UUID.randomUUID(), false);
        ProductAssessmentInput input = resolvedInput(productId);

        ProductHealthView view = productScoreService.computeAndStore(input, draftV2);

        assertThat(view.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(view.coverage().missing()).contains("SAT_FAT_G", "SODIUM_MG");
        ProductScore stored = productScores.findByProductVersionIdAndRuleSetId(input.productVersionId(), draftV2.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(stored.getScore()).isNull();
        assertThat(stored.getStars()).isNull();
    }

    @Test
    @DisplayName("filling a missing fact by enrichment changes the input hash, so the next read recomputes — one row, updated in place")
    void enrichmentTriggersRecompute() {
        UUID productId = ingestBiscuit("IT Enriched Biscuit " + UUID.randomUUID(), false);
        ProductAssessmentInput before = resolvedInput(productId);
        assertThat(productScoreService.getOrCompute(before, draftV2).status()).isEqualTo("INSUFFICIENT_DATA");

        List<String> filled = enrichment.enrichCurrentVersion(productId, new EnrichmentFacts(
                null, DietFlag.UNKNOWN, true, null, null, null,
                List.of(nutrient("SAT_FAT_G", "6.5", "g"), nutrient("SODIUM_MG", "500", "mg")),
                SourceType.OPEN_FOOD_FACTS, "https://world.openfoodfacts.org/product/it", "test attribution"));
        assertThat(filled).containsExactly("nutrient:SAT_FAT_G", "nutrient:SODIUM_MG");

        ProductAssessmentInput after = resolvedInput(productId);
        assertThat(after.productVersionId()).isEqualTo(before.productVersionId()); // same version, filled in place
        ProductHealthView view = productScoreService.getOrCompute(after, draftV2);

        assertThat(view.status()).isEqualTo("SCORED");
        assertThat(view.stars()).isEqualByComparingTo("1.5");
        assertThat(productScores.countByRuleSetId(draftV2.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("rescoreAll walks every ACTIVE product, stores a row per scorable one and records the run")
    void rescoreRunCoversCatalogue() {
        UUID scorable = ingestBiscuit("IT Rescore A " + UUID.randomUUID(), true);
        UUID sparse = ingestBiscuit("IT Rescore B " + UUID.randomUUID(), false);

        UUID runId = rescoreService.rescoreAllBlocking(draftV2.getVersion(), "integration-test");

        RescoreRun run = store.findRun(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RescoreRun.COMPLETED);
        assertThat(run.getTotal()).isGreaterThanOrEqualTo(2);
        assertThat(run.getDone()).isEqualTo(run.getTotal());
        assertThat(run.getFailed()).isZero();
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getTriggeredBy()).isEqualTo("integration-test");

        UUID scorableVersion = productQuery.getProduct(scorable).currentVersion().id();
        UUID sparseVersion = productQuery.getProduct(sparse).currentVersion().id();
        assertThat(productScores.findByProductVersionIdAndRuleSetId(scorableVersion, draftV2.getId()))
                .isPresent().get().extracting(ProductScore::getStatus).isEqualTo("SCORED");
        assertThat(productScores.findByProductVersionIdAndRuleSetId(sparseVersion, draftV2.getId()))
                .isPresent().get().extracting(ProductScore::getStatus).isEqualTo("INSUFFICIENT_DATA");
        assertThat(productScoreService.activeSetNeedsInitialRescore()).isFalse(); // ACTIVE v1 has no components
    }

    @Test
    @DisplayName("the public endpoint still answers NOT_YET_SCORED against the empty ACTIVE v1 and persists nothing")
    void activeRuleSetStaysHonestlyEmpty() throws Exception {
        UUID productId = ingestBiscuit("IT Public Biscuit " + UUID.randomUUID(), true);
        UUID versionId = productQuery.getProduct(productId).currentVersion().id();
        UUID activeId = productScoreService.activeRuleSet().getId();

        mockMvc.perform(get("/api/v1/products/{id}/assessment", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_YET_SCORED")))
                .andExpect(jsonPath("$.ruleSetVersion", is("v1")))
                // null fields are omitted from every response (spring.jackson.default-property-inclusion: non_null)
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.stars").doesNotExist())
                .andExpect(jsonPath("$.processingLevel.novaGroup", is(4)))
                .andExpect(jsonPath("$.processingLevel.isEstimated", is(true)))
                .andExpect(jsonPath("$.drivers").isEmpty())
                .andExpect(jsonPath("$.reasons[0].code", is("NO_ACTIVE_RULES")));

        // Ingestion published ProductVersionCreated; the listener ran against v1 and stored nothing.
        assertThat(productScores.findByProductVersionIdAndRuleSetId(versionId, activeId)).isEmpty();
    }

    // ------------------------------------------------------------------------------ fixtures

    private ProductAssessmentInput resolvedInput(UUID productId) {
        return orchestrator.resolve(AssessmentInputMapper.from(productQuery.getProductUncached(productId)));
    }

    /** A biscuit with the fixture values from PointsNetAlgorithmTest; {@code complete=false} omits sat fat and sodium. */
    private UUID ingestBiscuit(String name, boolean complete) {
        List<IngestProductCommand.NutrientValue> nutrients = complete
                ? List.of(nutrient("ENERGY_KCAL", "454", "kcal"), nutrient("SAT_FAT_G", "6.5", "g"),
                          nutrient("TOTAL_SUGAR_G", "25.5", "g"), nutrient("SODIUM_MG", "500", "mg"),
                          nutrient("PROTEIN_G", "7.0", "g"))
                : List.of(nutrient("ENERGY_KCAL", "454", "kcal"), nutrient("TOTAL_SUGAR_G", "25.5", "g"),
                          nutrient("PROTEIN_G", "7.0", "g"));
        IngestProductCommand cmd = new IngestProductCommand(
                name, "TestBrand", "BISCUITS", null, null, null, null, null, null,
                "WHEAT FLOUR, SUGAR", List.of("WHEAT FLOUR", "SUGAR"), nutrients, List.of(),
                SourceType.OPEN_FOOD_FACTS, CaptureMethod.OFF_IMPORT, null, "https://world.openfoodfacts.org/product/it",
                "test attribution", VersionStatus.NEEDS_VERIFICATION, DataQuality.RECENT, null, null,
                4, DietFlag.VEG, true, new BigDecimal("0"));
        return ingestion.ingestNewProduct(cmd);
    }

    private static IngestProductCommand.NutrientValue nutrient(String code, String per100, String unit) {
        return new IngestProductCommand.NutrientValue(code, new BigDecimal(per100), unit, NutrientBasis.PER_100G);
    }
}
