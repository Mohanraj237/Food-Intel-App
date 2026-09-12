package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealItemCommand;
import com.familyfood.nutrition.domain.NutritionAmounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One food in the meal. Give either {@code productId} or {@code customName} — the service
 * refuses both and neither, because a row that did either could not identify what was eaten.
 *
 * @param quantityG how much of it went into the meal, for everybody, before any portion split
 */
public record MealItemRequest(
        UUID productId,

        @Size(min = 1, max = 120) String customName,

        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal quantityG,

        @Valid NutritionPer100Request nutritionPer100) {

    MealItemCommand toCommand() {
        return new MealItemCommand(productId, customName, quantityG,
                nutritionPer100 == null ? NutritionAmounts.NONE : nutritionPer100.toAmounts());
    }
}
