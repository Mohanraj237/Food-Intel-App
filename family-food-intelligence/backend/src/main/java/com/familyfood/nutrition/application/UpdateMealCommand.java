package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.MealType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Correct a meal. The items and portions given replace the existing ones wholesale rather
 * than being merged in: "we actually shared it three ways" is one edit to one event, and a
 * patch-shaped API would turn it into a sequence of add/remove calls with a half-apportioned
 * meal visible in between.
 *
 * <p>No {@code householdId}: a meal does not move household, which would strand its portions.
 */
public record UpdateMealCommand(MealType mealType,
                                String name,
                                Instant consumedAt,
                                List<MealItemCommand> items,
                                List<MealPortionCommand> portions) {
}
