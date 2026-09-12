package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealItemView;

import java.math.BigDecimal;
import java.util.UUID;

/** Mirrors {@link MealItemView}. Amounts are for the whole item, before any portion split. */
public record MealItemResponse(UUID id,
                               UUID productId,
                               UUID productVersionId,
                               String customName,
                               BigDecimal quantityG,
                               NutritionResponse nutrition) {

    static MealItemResponse from(MealItemView view) {
        return new MealItemResponse(
                view.id(),
                view.productId(),
                view.productVersionId(),
                view.customName(),
                view.quantityG(),
                NutritionResponse.from(view.nutrition()));
    }
}
