package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.FoodType;
import com.familyfood.scoring.domain.ScoreComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.familyfood.scoring.application.PointsNetFixtures.estimatedFact;
import static com.familyfood.scoring.application.PointsNetFixtures.fact;
import static com.familyfood.scoring.application.PointsNetFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit coverage of {@link PointsNetAlgorithm} against components transcribed from the
 * FSSAI draft INR tables ({@link PointsNetFixtures}). Every expected point value below is
 * read off the transcribed table in {@code evidence/source-002}, not derived by any other
 * formula; the 0–100 figure is the documented display rescale (methodology §2.6).
 */
class PointsNetAlgorithmTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final UUID RULE_SET_ID = UUID.randomUUID();
    private static final List<ScoreComponent> ALL = PointsNetFixtures.allComponents(RULE_SET_ID);
    private static final Map<String, Object> CONFIG = PointsNetFixtures.algorithmConfig();

    @Test
    @DisplayName("a biscuit-shaped solid product: baseline bands add, protein is capped, net → stars → rescale")
    void scoresSolidProductFromTranscribedBands() {
        // Values chosen to land unambiguously inside printed bands of Table 2:
        //   energy 454 kcal  → >400 (band 5)          sat fat 6.5 g → >6.0 (band 6)
        //   total sugar 25.5 → >25.2 (band 6)         sodium 500 mg → >450 (band 5)
        // baseline = 22 (> 20, so Table 4's stricter caps apply: protein 7, others 5)
        //   protein 7.0 g   → not >7, so >5 (band 5) → capped at 7 → 5
        //   FVNL 0 %        → band 0                  fibre undeclared → ASSUMED_ZERO
        // net = 22 − 5 = 17 → Table 6 solid 16–20 → 1.5★ ; rescale 100·(25−17)/36 = 22
        ProductAssessmentInput in = input("BISCUITS", FoodType.SOLID, "PER_100G", 4,
                fact("ENERGY_KCAL", "454"), fact("SAT_FAT_G", "6.5"), fact("TOTAL_SUGAR_G", "25.5"),
                fact("SODIUM_MG", "500"), fact("PROTEIN_G", "7.0"), estimatedFact("FVNL_PCT", "0"));

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.status()).isEqualTo("SCORED");
        assertThat(view.stars()).isEqualByComparingTo("1.5");
        assertThat(view.score()).isEqualTo(22);
        assertThat(view.grade()).isEqualTo("D");
        assertThat(view.foodType()).isEqualTo("SOLID");
        assertThat(view.ratingScheme()).isEqualTo("FSSAI_INR_DRAFT_2022_DERIVED");
        assertThat(view.ruleSetVersion()).isEqualTo("fixture-v2");
        assertThat(view.computedAt()).isEqualTo(NOW);

        assertThat(view.drivers()).extracting(ScoreDriver::code, ScoreDriver::points).containsExactly(
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_ENERGY", 5),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_SATFAT", 6),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_TOTALSUGAR", 6),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_SODIUM", 5),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_FV", 0),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_FIBRE", 0),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_PROTEIN", 5));

        ScoreDriver sugar = view.drivers().get(2);
        assertThat(sugar.direction()).isEqualTo("NEGATIVE");
        assertThat(sugar.threshold()).isEqualByComparingTo("25.2");   // the band edge crossed
        assertThat(sugar.maxPoints()).isEqualTo(20);
        assertThat(sugar.standardRef()).isEqualTo("FSSAI_INR");
        assertThat(sugar.basis()).isEqualTo("PER_100G");

        ScoreDriver fv = view.drivers().get(4);
        assertThat(fv.isEstimated()).isTrue();                         // OFF estimate, never a label fact
        assertThat(fv.threshold()).isNull();                           // band 0: no edge crossed

        ScoreDriver fibre = view.drivers().get(5);
        assertThat(fibre.assumed()).isTrue();
        assertThat(fibre.value()).isNull();

        assertThat(view.reasons()).extracting(Reason::code)
                .contains("POINTS_BAND", "ASSUMED_ZERO")
                .doesNotContain("MISSING_NUTRIENT_DATA");
        assertThat(view.coverage().required()).containsExactly("ENERGY_KCAL", "SAT_FAT_G", "TOTAL_SUGAR_G",
                "SODIUM_MG", "FVNL_PCT", "FIBRE_G", "PROTEIN_G");
        assertThat(view.coverage().missing()).containsExactly("FIBRE_G");
        assertThat(view.processingLevel()).isEqualTo(new ProcessingLevel(4, true, "OPEN_FOOD_FACTS"));
    }

    @Test
    @DisplayName("the looser caps apply when baseline points are at or below 20")
    void looserCapsBelowTwenty() {
        // energy 100 → 1, sat fat 0.5 → 0, sugar 2 → 0, sodium 50 → 0  ⇒ baseline 1 (≤ 20)
        // protein 26 g → >25 (band 10), cap 15 → 10 ; fibre 10 g → >9 (band 3), cap 10 → 3
        // net = 1 − 13 = −12 → ≤ −11 → 5★ → rescale clamps at 100
        ProductAssessmentInput in = input(null, FoodType.SOLID, "PER_100G", null,
                fact("ENERGY_KCAL", "100"), fact("SAT_FAT_G", "0.5"), fact("TOTAL_SUGAR_G", "2"),
                fact("SODIUM_MG", "50"), fact("PROTEIN_G", "26"), fact("FIBRE_G", "10"));

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.stars()).isEqualByComparingTo("5");
        assertThat(view.score()).isEqualTo(100);
        assertThat(view.processingLevel()).isNull();
    }

    @Test
    @DisplayName("a value exactly on a band edge does not exceed it — '>' means strictly greater, as printed")
    void bandEdgesAreStrict() {
        // energy 400 is not >400 → band 4 (>320); sugar 4.2 is not >4.2 → band 0
        ProductAssessmentInput in = input(null, FoodType.SOLID, "PER_100G", null,
                fact("ENERGY_KCAL", "400"), fact("SAT_FAT_G", "0"), fact("TOTAL_SUGAR_G", "4.2"),
                fact("SODIUM_MG", "0"));

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.drivers()).extracting(ScoreDriver::code, ScoreDriver::points).contains(
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_ENERGY", 4),
                org.assertj.core.groups.Tuple.tuple("INR_SOLID_TOTALSUGAR", 0));
    }

    @Test
    @DisplayName("a liquid uses the liquid band tables only — no saturated fat or sodium component")
    void liquidUsesLiquidTables() {
        // energy 45 kcal/100 mL → >42 (band 7); sugar 10.9 g → >10.6 (band 8) ⇒ baseline 15 (> 10)
        // protein 0.5 → band 0, FV undeclared → 0 ; net 15 → liquid 13–15 → 2★ ; rescale 100·(20−15)/20 = 25
        ProductAssessmentInput in = input("BEVERAGES", FoodType.LIQUID, "PER_100ML", null,
                fact("ENERGY_KCAL", "45"), fact("TOTAL_SUGAR_G", "10.9"), fact("PROTEIN_G", "0.5"),
                fact("SODIUM_MG", "40"));

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.status()).isEqualTo("SCORED");
        assertThat(view.foodType()).isEqualTo("LIQUID");
        assertThat(view.stars()).isEqualByComparingTo("2");
        assertThat(view.score()).isEqualTo(25);
        assertThat(view.drivers()).extracting(ScoreDriver::code)
                .containsExactly("INR_LIQUID_ENERGY", "INR_LIQUID_TOTALSUGAR", "INR_LIQUID_FV", "INR_LIQUID_PROTEIN");
        assertThat(view.drivers().get(1).basis()).isEqualTo("PER_100ML");
    }

    @Test
    @DisplayName("the printed liquid overlap at 18 points resolves to the band listed first (1.5★), never both")
    void liquidOverlapResolvesToFirstListedBand() {
        StarBands liquid = StarBands.from(CONFIG, FoodType.LIQUID);
        assertThat(liquid.starsFor(18)).isEqualByComparingTo("1.5");
        assertThat(liquid.starsFor(19)).isEqualByComparingTo("1");
        assertThat(liquid.starsFor(20)).isEqualByComparingTo("1");   // 18–20 before ≥ 20 in list order
        assertThat(liquid.starsFor(21)).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("any undeclared baseline factor makes the whole score INSUFFICIENT_DATA — nothing is assumed")
    void missingBaselineIsInsufficientData() {
        ProductAssessmentInput in = input("BISCUITS", FoodType.SOLID, "PER_100G", null,
                fact("ENERGY_KCAL", "454"), fact("TOTAL_SUGAR_G", "25.5"), fact("PROTEIN_G", "7"));  // no sat fat, no sodium

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(view.score()).isNull();
        assertThat(view.stars()).isNull();
        assertThat(view.coverage().missing()).containsExactly("SAT_FAT_G", "SODIUM_MG", "FVNL_PCT", "FIBRE_G");
        assertThat(view.coverage().present()).containsExactly("ENERGY_KCAL", "TOTAL_SUGAR_G", "PROTEIN_G");
        assertThat(view.reasons()).filteredOn(r -> r.code().equals("MISSING_NUTRIENT_DATA"))
                .extracting(Reason::nutrientCode).containsExactly("SAT_FAT_G", "SODIUM_MG");
        // The factors that were evaluated still explain themselves.
        assertThat(view.drivers()).extracting(ScoreDriver::code).containsExactly("INR_SOLID_ENERGY", "INR_SOLID_TOTALSUGAR");
    }

    @Test
    @DisplayName("only per-100 values count — a per-serving-only fact is treated as undeclared")
    void perServingOnlyIsUndeclared() {
        ProductAssessmentInput in = new ProductAssessmentInput(UUID.randomUUID(), null,
                List.of(new NutrientFact("ENERGY_KCAL", new BigDecimal("120"), null, "kcal"),
                        fact("SAT_FAT_G", "1"), fact("TOTAL_SUGAR_G", "1"), fact("SODIUM_MG", "1")),
                List.of(), "PER_100G", null, null, "SOLID", Map.of());

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(view.coverage().missing()).contains("ENERGY_KCAL");
    }

    @Test
    @DisplayName("fats and oils are exempt from a rating: INSUFFICIENT_DATA with SCORE_NOT_APPLICABLE, never a number")
    void fatOilIsNotApplicable() {
        ProductAssessmentInput in = input("EDIBLE_OIL", FoodType.FAT_OIL, "PER_100G", null,
                fact("ENERGY_KCAL", "900"), fact("SAT_FAT_G", "50"), fact("TOTAL_SUGAR_G", "0"), fact("SODIUM_MG", "0"));

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG, ALL, NOW);

        assertThat(view.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(view.foodType()).isEqualTo("FAT_OIL");
        assertThat(view.reasons()).containsExactly(new Reason("SCORE_NOT_APPLICABLE", null, null, null, null, "FSSAI_INR"));
        assertThat(view.drivers()).isEmpty();
    }

    @Test
    @DisplayName("no component in scope for the food type → NOT_YET_SCORED, the same honest state as an empty rule set")
    void noComponentsForFoodTypeIsNotYetScored() {
        ProductAssessmentInput in = input(null, FoodType.LIQUID, "PER_100ML", 3, fact("ENERGY_KCAL", "10"));

        ProductHealthView view = PointsNetAlgorithm.score(in, "fixture-v2", CONFIG,
                PointsNetFixtures.solidComponents(RULE_SET_ID), NOW);

        assertThat(view.status()).isEqualTo("NOT_YET_SCORED");
        assertThat(view.reasons()).extracting(Reason::code).containsExactly("NO_ACTIVE_RULES");
        assertThat(view.processingLevel().novaGroup()).isEqualTo(3);
    }

    @Test
    @DisplayName("the 0–100 number is anchored on the star table's own edges and clamped")
    void rescaleAnchors() {
        StarBands solid = StarBands.from(CONFIG, FoodType.SOLID);
        assertThat(solid.scoreFor(-11)).isEqualTo(100);
        assertThat(solid.scoreFor(-30)).isEqualTo(100);
        assertThat(solid.scoreFor(25)).isEqualTo(0);
        assertThat(solid.scoreFor(40)).isEqualTo(0);
        assertThat(solid.scoreFor(7)).isEqualTo(50);   // midpoint of [-11, 25]
    }

    @Test
    @DisplayName("a rule set whose configuration lacks the star bands fails loudly rather than guessing")
    void missingStarBandsFailLoudly() {
        assertThatThrownBy(() -> StarBands.from(Map.of(), FoodType.SOLID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("starBandsSolid");
        assertThatThrownBy(() -> BandTable.from(Map.of("min", 0, "max", 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("band tables read every number from the row: JSON integers, decimals and strings alike")
    void bandTableParsesJsonShapes() {
        BandTable table = BandTable.from(Map.of("type", "BANDS",
                "bands", List.of(Map.of("gt", 80, "points", 1), Map.of("gt", "160.0", "points", 2.0),
                        Map.of("gt", 240.5, "points", 3)),
                "cap", Map.of("baselineLte", 20, "within", 10, "above", 5)));

        assertThat(table.place(new BigDecimal("80")).points()).isZero();
        assertThat(table.place(new BigDecimal("80.01")).points()).isEqualTo(1);
        assertThat(table.place(new BigDecimal("240.5")).points()).isEqualTo(2);
        assertThat(table.place(new BigDecimal("999")).threshold()).isEqualByComparingTo("240.5");
        assertThat(table.maxPoints()).isEqualTo(3);
        assertThat(table.cap().limitFor(20)).isEqualTo(10);
        assertThat(table.cap().limitFor(21)).isEqualTo(5);
    }
}
