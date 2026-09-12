package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.MealType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Log one eating occasion: what was eaten and who ate what share of it, in a single call.
 *
 * <p>Items and portions arrive together rather than through separate endpoints because a meal
 * with items and no portions records nothing for anyone, and a meal with portions and no items
 * records a share of nothing. Neither is a state worth being able to persist.
 *
 * @param consumedAt when it was eaten; null means now
 */
public record CreateMealCommand(UUID householdId,
                                MealType mealType,
                                String name,
                                Instant consumedAt,
                                List<MealItemCommand> items,
                                List<MealPortionCommand> portions) {
}
