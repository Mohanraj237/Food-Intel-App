package com.familyfood.nutrition.api;

import com.familyfood.nutrition.domain.NutritionAmounts;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * Per-100 amounts for a custom food, as declared by whoever is logging it.
 *
 * <p>Every field is optional and an omitted field stays unknown — it is never read as zero. A
 * home-cooked dal with only an energy figure is an ordinary thing to log, and filling the rest
 * with zeroes would turn "we do not know" into a claim about the food.
 *
 * <p>Ignored for a catalogue product: those amounts come from the product's current version, so
 * that a meal log cannot restate what a label says.
 */
public record NutritionPer100Request(
        // Structural bounds only — non-negative, and a number the numeric(10,2) column can
        // hold. Not plausibility limits: deciding a value is implausible is an interpretation.
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal energyKcal,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal proteinG,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateG,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal fatG,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal fibreG,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal sugarG,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal sodiumMg) {

    NutritionAmounts toAmounts() {
        return new NutritionAmounts(energyKcal, proteinG, carbohydrateG, fatG, fibreG, sugarG, sodiumMg);
    }
}
