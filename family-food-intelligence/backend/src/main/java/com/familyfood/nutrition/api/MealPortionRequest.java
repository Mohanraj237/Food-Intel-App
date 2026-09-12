package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealPortionCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One member's share of the meal: {@code 1.0} is all of it, {@code 0.5} half, {@code 1.5} one
 * and a half helpings.
 *
 * <p>Shares across a meal are not required to sum to 1. A family rarely eats exactly what was
 * cooked, and demanding the sum would either make leftovers unrepresentable or push people into
 * entering numbers they know are wrong.
 */
public record MealPortionRequest(
        @NotNull UUID householdMemberId,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("10.0")
        @Digits(integer = 2, fraction = 4)
        BigDecimal portionFraction) {

    MealPortionCommand toCommand() {
        return new MealPortionCommand(householdMemberId, portionFraction);
    }
}
