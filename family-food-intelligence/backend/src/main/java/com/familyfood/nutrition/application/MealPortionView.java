package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.NutritionAmounts;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One member's share, and — when the reader may see it — what that share came to.
 *
 * <p>{@code nutrition} is the meal total multiplied by {@code portionFraction}, the same
 * arithmetic that produced this member's observations, shown so a client does not have to
 * reimplement it and reach a different answer.
 *
 * <p><b>It is that member's NUTRITION data (D-044).</b> Who was served, and how much of the dish,
 * is a fact among the people at the table; what the share amounted to is the member's own
 * record. So {@code nutrition} is present only for the reader themselves, a guardian of a child,
 * or someone the member has shared NUTRITION with — and otherwise {@code null} with
 * {@code nutritionWithheld} set, so a client can say "not shared with you" rather than show an
 * empty share as if it were nothing (D-042, defect 1).
 */
public record MealPortionView(UUID id,
                              UUID householdMemberId,
                              BigDecimal portionFraction,
                              NutritionAmounts nutrition,
                              /** False while another adult has not yet confirmed their share (D-041). */
                              boolean inMemberLog,
                              /** True when {@code nutrition} is withheld from this reader (D-044). */
                              boolean nutritionWithheld) {
}
