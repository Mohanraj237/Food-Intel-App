package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealPortionView;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mirrors {@link MealPortionView}: one member's share, and what that share came to.
 *
 * <p>{@code nutrition} is the meal total times {@code portionFraction} — the same arithmetic
 * that produced this member's observations, returned so a client does not reimplement it and
 * arrive at a different number.
 */
public record MealPortionResponse(UUID id,
                                  UUID householdMemberId,
                                  BigDecimal portionFraction,
                                  NutritionResponse nutrition) {

    static MealPortionResponse from(MealPortionView view) {
        return new MealPortionResponse(
                view.id(),
                view.householdMemberId(),
                view.portionFraction(),
                NutritionResponse.from(view.nutrition()));
    }
}
