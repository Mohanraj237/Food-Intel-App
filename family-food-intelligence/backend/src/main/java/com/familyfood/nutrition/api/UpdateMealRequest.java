package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.UpdateMealCommand;
import com.familyfood.nutrition.domain.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Correct a meal. The items and portions given replace the existing ones wholesale — "we
 * actually shared it three ways" is one edit to one event, not a sequence of add and remove
 * calls with a half-apportioned meal visible in between.
 *
 * <p>No {@code householdId}: a meal does not move household, which would strand its portions.
 *
 * @param consumedAt when it was eaten; omit to leave it as it was
 */
public record UpdateMealRequest(
        @NotNull MealType mealType,
        @Size(min = 1, max = 120) String name,
        Instant consumedAt,
        @NotNull @Valid List<MealItemRequest> items,
        @NotNull @Valid List<MealPortionRequest> portions) {

    public UpdateMealCommand toCommand() {
        return new UpdateMealCommand(mealType, name, consumedAt,
                items.stream().map(MealItemRequest::toCommand).toList(),
                portions.stream().map(MealPortionRequest::toCommand).toList());
    }
}
