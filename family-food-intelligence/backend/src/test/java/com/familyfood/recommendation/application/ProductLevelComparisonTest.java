package com.familyfood.recommendation.application;

import com.familyfood.scoring.application.AssessmentContext;
import com.familyfood.scoring.application.Coverage;
import com.familyfood.scoring.application.ProductAssessmentInput;
import com.familyfood.scoring.application.ProductHealthView;
import com.familyfood.scoring.application.ScoreDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product-level differences between a product and a candidate (D-060): a real difference is a
 * different star band, a different points band for a factor, or a different NOVA group; the
 * direction is the rating method's own, never judged here.
 */
class ProductLevelComparisonTest {

    @Test
    @DisplayName("a lower sugar band is better, a lower protein band is worse, a higher star band is better")
    void bandsAndDirections() {
        AssessmentContext current = context("2", 4,
                driver("INR_SOLID_TOTALSUGAR", "NEGATIVE", "TOTAL_SUGAR_G", "25.5", 6, false),
                driver("INR_SOLID_PROTEIN", "POSITIVE", "PROTEIN_G", "7", 5, false),
                driver("INR_SOLID_SODIUM", "NEGATIVE", "SODIUM_MG", "300", 3, false));
        AssessmentContext candidate = context("3", 3,
                driver("INR_SOLID_TOTALSUGAR", "NEGATIVE", "TOTAL_SUGAR_G", "9", 2, false),
                driver("INR_SOLID_PROTEIN", "POSITIVE", "PROTEIN_G", "2.2", 2, false),
                driver("INR_SOLID_SODIUM", "NEGATIVE", "SODIUM_MG", "320", 3, false));

        List<AlternativeReason> better = new ArrayList<>();
        List<AlternativeReason> worse = new ArrayList<>();
        AlternativesServiceImpl.compareProductLevel(current, candidate, better, worse);

        assertThat(better).extracting(AlternativeReason::code)
                .containsExactlyInAnyOrder("HIGHER_RATING", "LOWER_TOTAL_SUGAR_G", "LESS_PROCESSED");
        assertThat(worse).extracting(AlternativeReason::code).containsExactly("LOWER_PROTEIN_G");
        AlternativeReason sugar = better.stream().filter(r -> r.code().equals("LOWER_TOTAL_SUGAR_G")).findFirst().orElseThrow();
        assertThat(sugar.candidateValue()).isEqualByComparingTo("9");
        assertThat(sugar.currentValue()).isEqualByComparingTo("25.5");
        // Same sodium band (300 vs 320 mg both 3 points): not a difference.
        assertThat(better).noneMatch(r -> r.code().contains("SODIUM"));
        assertThat(worse).noneMatch(r -> r.code().contains("SODIUM"));
    }

    @Test
    @DisplayName("an undeclared factor on either side is not compared; unrated products compare only on NOVA")
    void undeclaredAndUnrated() {
        AssessmentContext current = context("2", null,
                driver("INR_SOLID_FIBRE", "POSITIVE", "FIBRE_G", null, 0, true));
        AssessmentContext candidate = context("2", null,
                driver("INR_SOLID_FIBRE", "POSITIVE", "FIBRE_G", "8", 2, false));
        List<AlternativeReason> better = new ArrayList<>();
        List<AlternativeReason> worse = new ArrayList<>();
        AlternativesServiceImpl.compareProductLevel(current, candidate, better, worse);
        assertThat(better).isEmpty();
        assertThat(worse).isEmpty();

        AssessmentContext unratedA = unrated(4);
        AssessmentContext unratedB = unrated(2);
        AlternativesServiceImpl.compareProductLevel(unratedA, unratedB, better, worse);
        assertThat(better).extracting(AlternativeReason::code).containsExactly("LESS_PROCESSED");
    }

    // ------------------------------------------------------------------------------ fixtures

    private static ScoreDriver driver(String code, String direction, String nutrient, String value, int points, boolean assumed) {
        return new ScoreDriver(code, direction, nutrient, value == null ? null : new BigDecimal(value), "g", "PER_100G",
                points, 10, null, "FSSAI_INR", "score.driver", false, assumed);
    }

    private static AssessmentContext context(String stars, Integer nova, ScoreDriver... drivers) {
        UUID id = UUID.randomUUID();
        ProductHealthView health = new ProductHealthView(id, "v2", "SCORED", 50, "C", new BigDecimal(stars),
                "FSSAI_INR_DRAFT_2022_DERIVED", "SOLID", List.of(drivers), null, Coverage.empty(), List.of(), Instant.EPOCH);
        return new AssessmentContext(health, input(id, nova));
    }

    private static AssessmentContext unrated(Integer nova) {
        UUID id = UUID.randomUUID();
        return new AssessmentContext(ProductHealthView.notYetScored(id, "v1", null, Instant.EPOCH), input(id, nova));
    }

    private static ProductAssessmentInput input(UUID id, Integer nova) {
        return new ProductAssessmentInput(id, "BISCUITS", List.of(), List.of(), "PER_100G", nova,
                nova == null ? null : "OPEN_FOOD_FACTS", "SOLID", Map.of());
    }
}
