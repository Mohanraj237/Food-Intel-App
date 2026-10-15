package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.MealType;
import com.familyfood.nutrition.domain.NutritionAmounts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One meal as the API returns it: what was eaten, what it came to, and who had what share.
 *
 * <p>Deliberately carries no assessment of the meal — no score, no target comparison, no
 * "high in" flag. Summing what was logged is arithmetic; calling the result good or bad is a
 * claim that needs a reviewed standard (D-037), and this module does not make it.
 */
public record MealView(UUID id,
                       UUID householdId,
                       MealType mealType,
                       String name,
                       Instant consumedAt,
                       NutritionAmounts totalNutrition,
                       List<MealItemView> items,
                       List<MealPortionView> portions,
                       /** Who logged it — the only member who may edit or delete it (D-041).
                        *  An account id household members can already see via the member
                        *  list, so exposing it here reveals nothing new. */
                       UUID loggedByUserId) {
}
