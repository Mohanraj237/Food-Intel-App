package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.MealItem;
import com.familyfood.nutrition.domain.NutritionAmounts;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item as stored, with the absolute amounts it contributed to the whole meal.
 *
 * <p>An absent amount stays absent in the view. A client that needs to say "we do not know"
 * cannot do so if the API has already turned the unknown into a zero.
 */
public record MealItemView(UUID id,
                           UUID productId,
                           UUID productVersionId,
                           String customName,
                           BigDecimal quantityG,
                           NutritionAmounts nutrition) {

    static MealItemView from(MealItem item) {
        return new MealItemView(
                item.getId(),
                item.getProductId(),
                item.getProductVersionId(),
                item.getCustomName(),
                item.getQuantityG(),
                item.nutrition());
    }
}
