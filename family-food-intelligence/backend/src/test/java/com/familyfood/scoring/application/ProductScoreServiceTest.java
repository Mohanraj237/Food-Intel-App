package com.familyfood.scoring.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The input hash that decides whether a stored {@code product_score} row is still fresh (D-059). */
class ProductScoreServiceTest {

    @Test
    @DisplayName("the hash is stable across fact order and numeric formatting, and blind to allergens and derived facts")
    void hashIsCanonical() {
        UUID version = UUID.randomUUID();
        ProductAssessmentInput a = new ProductAssessmentInput(version, "BISCUITS",
                List.of(fact("TOTAL_SUGAR_G", "25.50"), fact("ENERGY_KCAL", "454")), List.of("MILK"),
                "PER_100G", 4, "OPEN_FOOD_FACTS", "SOLID", Map.of("INR_STARS", BigDecimal.ONE));
        ProductAssessmentInput b = new ProductAssessmentInput(UUID.randomUUID(), "BISCUITS",
                List.of(fact("ENERGY_KCAL", "454.0"), fact("TOTAL_SUGAR_G", "25.5")), List.of(),
                "PER_100G", 4, "OPEN_FOOD_FACTS", "SOLID", Map.of());

        assertThat(ProductScoreService.inputHash(a)).isEqualTo(ProductScoreService.inputHash(b));
    }

    @Test
    @DisplayName("a changed fact, basis, food type, category or processing level changes the hash")
    void hashTracksScoreInputs() {
        ProductAssessmentInput base = new ProductAssessmentInput(UUID.randomUUID(), "BISCUITS",
                List.of(fact("TOTAL_SUGAR_G", "25.5")), List.of(), "PER_100G", 4, "OPEN_FOOD_FACTS", "SOLID", Map.of());
        String hash = ProductScoreService.inputHash(base);

        assertThat(ProductScoreService.inputHash(new ProductAssessmentInput(base.productVersionId(), "BISCUITS",
                List.of(fact("TOTAL_SUGAR_G", "25.6")), List.of(), "PER_100G", 4, "OPEN_FOOD_FACTS", "SOLID", Map.of())))
                .isNotEqualTo(hash);
        assertThat(ProductScoreService.inputHash(base.withFoodType("LIQUID"))).isNotEqualTo(hash);
        assertThat(ProductScoreService.inputHash(new ProductAssessmentInput(base.productVersionId(), "CHIPS",
                base.nutrients(), List.of(), "PER_100G", 4, "OPEN_FOOD_FACTS", "SOLID", Map.of()))).isNotEqualTo(hash);
        assertThat(ProductScoreService.inputHash(new ProductAssessmentInput(base.productVersionId(), "BISCUITS",
                base.nutrients(), List.of(), "PER_100G", 3, "OPEN_FOOD_FACTS", "SOLID", Map.of()))).isNotEqualTo(hash);
        assertThat(ProductScoreService.inputHash(new ProductAssessmentInput(base.productVersionId(), "BISCUITS",
                List.of(new NutrientFact("TOTAL_SUGAR_G", null, new BigDecimal("25.5"), "g", true)), List.of(),
                "PER_100G", 4, "OPEN_FOOD_FACTS", "SOLID", Map.of()))).isNotEqualTo(hash);
    }

    private static NutrientFact fact(String code, String per100) {
        return new NutrientFact(code, null, new BigDecimal(per100), "g");
    }
}
