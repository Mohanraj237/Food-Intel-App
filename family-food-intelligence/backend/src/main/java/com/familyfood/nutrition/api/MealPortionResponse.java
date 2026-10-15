package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealPortionView;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mirrors {@link MealPortionView}: one member's share, and what that share came to.
 *
 * <p>{@code nutrition} is the meal total times {@code portionFraction} — the same arithmetic
 * that produced this member's observations, returned so a client does not reimplement it and
 * arrive at a different number. It is absent, and {@code nutritionWithheld} is true, when the
 * reader may not see this member's NUTRITION data (D-044).
 *
 * <p>{@code inMemberLog} is false while another adult has yet to confirm their share (D-041).
 * It states only <em>that</em> they confirmed, never what their values are, so it is safe to
 * show to everyone at the table.
 */
public record MealPortionResponse(UUID id,
                                  UUID householdMemberId,
                                  BigDecimal portionFraction,
                                  NutritionResponse nutrition,
                                  boolean inMemberLog,
                                  boolean nutritionWithheld) {

    static MealPortionResponse from(MealPortionView view) {
        return new MealPortionResponse(
                view.id(),
                view.householdMemberId(),
                view.portionFraction(),
                view.nutrition() == null ? null : NutritionResponse.from(view.nutrition()),
                view.inMemberLog(),
                view.nutritionWithheld());
    }
}
