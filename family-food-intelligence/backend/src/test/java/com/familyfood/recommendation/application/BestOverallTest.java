package com.familyfood.recommendation.application;

import com.familyfood.scoring.application.Coverage;
import com.familyfood.scoring.application.PersonalFitView;
import com.familyfood.scoring.application.ProductHealthView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The deterministic best-overall rule (D-066): a winner only when the data really separates them. */
class BestOverallTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();

    @Test
    @DisplayName("without a household the higher star band wins, then the higher score")
    void starsThenScore() {
        BestOverall best = BestOverall.decide(List.of(entry(A, "3", 55), entry(B, "3.5", 60), entry(C, "3.5", 58)), false);
        assertThat(best.outcome()).isEqualTo("WINNER");
        assertThat(best.productId()).isEqualTo(B);
        assertThat(best.reasons()).containsExactly("HIGHEST_RATING");
    }

    @Test
    @DisplayName("an exact tie on every key is no winner, not a coin toss")
    void tie() {
        BestOverall best = BestOverall.decide(List.of(entry(A, "3", 55), entry(B, "3", 55)), false);
        assertThat(best.outcome()).isEqualTo("NO_WINNER_TIE");
        assertThat(best.productId()).isNull();
    }

    @Test
    @DisplayName("one unrated product means no winner")
    void insufficient() {
        BestOverall best = BestOverall.decide(List.of(entry(A, "4", 80), unrated(B)), false);
        assertThat(best.outcome()).isEqualTo("NO_WINNER_INSUFFICIENT_DATA");
    }

    @Test
    @DisplayName("with a household an allergen match removes a product before anything is compared")
    void allergenExcluded() {
        BestOverall best = BestOverall.decide(List.of(
                entry(A, "5", 95, fit("EVERYDAY", List.of("PEANUT"))),
                entry(B, "3", 50, fit("OCCASIONAL", List.of()))), true);
        assertThat(best.productId()).isEqualTo(B);
        assertThat(best.excludedForAllergens()).containsExactly(A);
        assertThat(best.reasons()).contains("OTHERS_ALLERGEN_CONFLICT");
    }

    @Test
    @DisplayName("every product conflicting is its own outcome")
    void allConflict() {
        BestOverall best = BestOverall.decide(List.of(
                entry(A, "5", 95, fit("EVERYDAY", List.of("MILK"))),
                entry(B, "3", 50, fit("OCCASIONAL", List.of("MILK")))), true);
        assertThat(best.outcome()).isEqualTo("NO_WINNER_ALL_CONFLICT");
    }

    @Test
    @DisplayName("with real verdicts the least severe worst verdict beats a higher rating")
    void verdictFirst() {
        BestOverall best = BestOverall.decide(List.of(
                entry(A, "4", 75, fit("LIMIT", List.of())),
                entry(B, "3", 55, fit("OCCASIONAL", List.of()))), true);
        assertThat(best.productId()).isEqualTo(B);
        assertThat(best.reasons()).containsExactly("LEAST_SEVERE_VERDICT", "HIGHEST_RATING");
    }

    @Test
    @DisplayName("members without a real verdict leave the rating to decide")
    void noVerdictsFallBackToRating() {
        BestOverall best = BestOverall.decide(List.of(
                entry(A, "4", 75, fit("INSUFFICIENT_DATA", List.of())),
                entry(B, "3", 55, fit("OCCASIONAL", List.of()))), true);
        assertThat(best.productId()).isEqualTo(A);
        assertThat(best.reasons()).containsExactly("HIGHEST_RATING");
    }

    // ------------------------------------------------------------------------------ fixtures

    private static BestOverall.Entry entry(UUID id, String stars, int score, PersonalFitView... members) {
        ProductHealthView health = new ProductHealthView(id, "v2", "SCORED", score, "C", new BigDecimal(stars),
                "FSSAI_INR_DRAFT_2022_DERIVED", "SOLID", List.of(), null, Coverage.empty(), List.of(), Instant.EPOCH);
        return new BestOverall.Entry(id, health, List.of(members));
    }

    private static BestOverall.Entry unrated(UUID id) {
        return new BestOverall.Entry(id, ProductHealthView.notYetScored(id, "v1", null, Instant.EPOCH), List.of());
    }

    private static PersonalFitView fit(String verdict, List<String> allergens) {
        return new PersonalFitView(ME, "ASSESSED", verdict, List.of(), allergens, "v2", Instant.EPOCH);
    }
}
