package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Direction;
import com.familyfood.scoring.domain.ScoreComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit coverage of the {@code WEIGHTED_MEAN_V1} aggregation math in
 * {@link WeightedMeanAlgorithm}, using hand-built {@link ScoreComponent} fixtures that are
 * never persisted and never routed through any real rule_set row — inserting a synthetic
 * component under the ACTIVE "v1" rule_set would falsely imply a reviewed component set
 * exists. Behaviour is unchanged from the pre-D-059 {@code ProductHealthScoreServiceImpl.aggregate}.
 */
class WeightedMeanAlgorithmTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final UUID RULE_SET_ID = UUID.randomUUID();

    @Test
    @DisplayName("blends a NEGATIVE and a POSITIVE component into a single weighted 0-100 score")
    void aggregatesWeightedComponents() {
        ScoreComponent sugar = component("SUGAR", "ADDED_SUGAR_G", Direction.NEGATIVE,
                "1", "0", "50"); // value 25 of [0,50] negative -> 50
        ScoreComponent fibre = component("FIBRE", "FIBRE_G", Direction.POSITIVE,
                "1", "0", "10"); // value 8 of [0,10] positive -> 80

        ProductAssessmentInput input = input(
                fact("ADDED_SUGAR_G", "25"),
                fact("FIBRE_G", "8"));

        ProductHealthView view = WeightedMeanAlgorithm.aggregate(input, "test-fixture", List.of(sugar, fibre), NOW);

        assertThat(view.status()).isEqualTo("SCORED");
        // equal weights: (50 + 80) / 2 = 65
        assertThat(view.score()).isEqualTo(65);
        assertThat(view.grade()).isEqualTo("B");
        assertThat(view.stars()).isNull();
        assertThat(view.ratingScheme()).isEqualTo("WEIGHTED_MEAN_V1");
        assertThat(view.reasons()).isEmpty();
        assertThat(view.ruleSetVersion()).isEqualTo("test-fixture");
        assertThat(view.computedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a missing nutrient excludes only that component's weight, not the whole score")
    void excludesComponentWithMissingData() {
        ScoreComponent sugar = component("SUGAR", "ADDED_SUGAR_G", Direction.NEGATIVE,
                "1", "0", "50");
        ScoreComponent sodium = component("SODIUM", "SODIUM_MG", Direction.NEGATIVE,
                "1", "0", "1000"); // no fact supplied for this one

        ProductAssessmentInput input = input(fact("ADDED_SUGAR_G", "0")); // best possible sugar value

        ProductHealthView view = WeightedMeanAlgorithm.aggregate(input, "test-fixture", List.of(sugar, sodium), NOW);

        assertThat(view.status()).isEqualTo("SCORED");
        assertThat(view.score()).isEqualTo(100); // only the sugar component counted, at its best value
        assertThat(view.reasons()).containsExactly(
                new Reason("MISSING_NUTRIENT_DATA", "SODIUM_MG", null, null, null, "WHO_SODIUM_2012"));
    }

    @Test
    @DisplayName("every component missing data yields NOT_YET_SCORED, never a guessed number")
    void allComponentsMissingYieldsNotYetScored() {
        ScoreComponent sugar = component("SUGAR", "ADDED_SUGAR_G", Direction.NEGATIVE, "1", "0", "50");

        ProductHealthView view = WeightedMeanAlgorithm.aggregate(input(), "test-fixture", List.of(sugar), NOW);

        assertThat(view.status()).isEqualTo("NOT_YET_SCORED");
        assertThat(view.score()).isNull();
        assertThat(view.grade()).isNull();
        assertThat(view.reasons()).hasSize(1);
        assertThat(view.reasons().get(0).code()).isEqualTo("MISSING_NUTRIENT_DATA");
    }

    @Test
    @DisplayName("componentScoreFor clamps out-of-range values instead of extrapolating")
    void componentScoreClampsOutOfRangeValues() {
        ScoreComponent negative = component("X", "N", Direction.NEGATIVE, "1", "10", "20");

        assertThat(WeightedMeanAlgorithm.componentScoreFor(negative, new BigDecimal("5")))
                .isEqualByComparingTo(new BigDecimal("100")); // below min clamps to min -> best score for NEGATIVE
        assertThat(WeightedMeanAlgorithm.componentScoreFor(negative, new BigDecimal("25")))
                .isEqualByComparingTo(new BigDecimal("0")); // above max clamps to max -> worst score for NEGATIVE
        assertThat(WeightedMeanAlgorithm.componentScoreFor(negative, new BigDecimal("15")))
                .isEqualByComparingTo(new BigDecimal("50")); // midpoint
    }

    @Test
    @DisplayName("gradeFor bands are deterministic at every boundary")
    void gradeBoundaries() {
        assertThat(WeightedMeanAlgorithm.gradeFor(100)).isEqualTo("A");
        assertThat(WeightedMeanAlgorithm.gradeFor(80)).isEqualTo("A");
        assertThat(WeightedMeanAlgorithm.gradeFor(79)).isEqualTo("B");
        assertThat(WeightedMeanAlgorithm.gradeFor(60)).isEqualTo("B");
        assertThat(WeightedMeanAlgorithm.gradeFor(59)).isEqualTo("C");
        assertThat(WeightedMeanAlgorithm.gradeFor(40)).isEqualTo("C");
        assertThat(WeightedMeanAlgorithm.gradeFor(39)).isEqualTo("D");
        assertThat(WeightedMeanAlgorithm.gradeFor(20)).isEqualTo("D");
        assertThat(WeightedMeanAlgorithm.gradeFor(19)).isEqualTo("E");
        assertThat(WeightedMeanAlgorithm.gradeFor(0)).isEqualTo("E");
    }

    // ------------------------------------------------------------------------------ fixtures

    private static ScoreComponent component(String code, String nutrientCode, Direction direction,
                                             String weight, String min, String max) {
        String standardRef = nutrientCode.equals("SODIUM_MG") ? "WHO_SODIUM_2012" : "WHO_FREE_SUGARS_2015";
        return ScoreComponent.create(RULE_SET_ID, code, nutrientCode, direction,
                new BigDecimal(weight), Map.of("min", min, "max", max), null, standardRef);
    }

    private static NutrientFact fact(String code, String amount) {
        return new NutrientFact(code, null, new BigDecimal(amount), "g");
    }

    private static ProductAssessmentInput input(NutrientFact... facts) {
        return new ProductAssessmentInput(UUID.randomUUID(), null, List.of(facts), List.of());
    }
}
