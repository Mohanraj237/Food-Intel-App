package com.familyfood.nutrition.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One member's share of the meal. {@code 1.0} is all of it, {@code 0.5} half.
 *
 * <p>Shares across a meal are not required to sum to 1 — see {@code MealPortion}.
 */
public record MealPortionCommand(UUID householdMemberId, BigDecimal portionFraction) {
}
