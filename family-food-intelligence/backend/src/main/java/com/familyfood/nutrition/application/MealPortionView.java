package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.NutritionAmounts;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One member's share, and what that share came to.
 *
 * <p>{@code nutrition} is the meal total multiplied by {@code portionFraction} — the same
 * arithmetic that produced this member's observations, shown so a client does not have to
 * reimplement it and reach a different answer. It is not a second source of truth: the
 * observations written through {@code WellnessAccess} are, and reading those back is subject
 * to that module's visibility rules (D-038).
 */
public record MealPortionView(UUID id,
                              UUID householdMemberId,
                              BigDecimal portionFraction,
                              NutritionAmounts nutrition) {
}
