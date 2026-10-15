package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@link MealView}.
 *
 * <p>Carries no assessment of the meal — no score, no target, no "high in" flag. Summing what
 * was logged is arithmetic; calling the result good or bad is a claim that needs a citable
 * reviewed standard (D-037), so there is no field for one.
 */
public record MealResponse(UUID id,
                           UUID householdId,
                           String mealType,
                           String name,
                           Instant consumedAt,
                           NutritionResponse totalNutrition,
                           List<MealItemResponse> items,
                           List<MealPortionResponse> portions,
                           UUID loggedByUserId) {

    static MealResponse from(MealView view) {
        return new MealResponse(
                view.id(),
                view.householdId(),
                view.mealType().name(),
                view.name(),
                view.consumedAt(),
                NutritionResponse.from(view.totalNutrition()),
                view.items().stream().map(MealItemResponse::from).toList(),
                view.portions().stream().map(MealPortionResponse::from).toList(),
                view.loggedByUserId());
    }
}
