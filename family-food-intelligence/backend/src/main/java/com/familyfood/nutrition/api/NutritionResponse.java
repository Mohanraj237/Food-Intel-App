package com.familyfood.nutrition.api;

import com.familyfood.nutrition.domain.NutritionAmounts;

import java.math.BigDecimal;

/**
 * Absolute amounts — for a whole item, a whole meal, or one member's share.
 *
 * <p>An unknown amount is null and, with the application's {@code non_null} inclusion, simply
 * absent from the JSON. That is the point: a client can render "not known" only if the API has
 * not already turned the unknown into a zero.
 */
public record NutritionResponse(BigDecimal energyKcal,
                                BigDecimal proteinG,
                                BigDecimal carbohydrateG,
                                BigDecimal fatG,
                                BigDecimal fibreG,
                                BigDecimal sugarG,
                                BigDecimal sodiumMg) {

    static NutritionResponse from(NutritionAmounts amounts) {
        return new NutritionResponse(
                amounts.energyKcal(),
                amounts.proteinG(),
                amounts.carbohydrateG(),
                amounts.fatG(),
                amounts.fibreG(),
                amounts.sugarG(),
                amounts.sodiumMg());
    }
}
