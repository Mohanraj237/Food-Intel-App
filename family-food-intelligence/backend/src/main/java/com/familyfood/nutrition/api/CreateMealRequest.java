package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.CreateMealCommand;
import com.familyfood.nutrition.domain.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Log one eating occasion: what was eaten, and who ate what share of it, in one call.
 *
 * <p>The lists are {@code @NotNull} but deliberately not {@code @NotEmpty}: an empty list is
 * refused by {@code MealService} with {@code MEAL_HAS_NO_ITEMS} or {@code MEAL_HAS_NO_PORTIONS},
 * which tells the caller which half is missing. A bean-validation rejection here would flatten
 * both into one generic validation failure, and would put the rule in the HTTP adapter where a
 * second entry point could miss it.
 *
 * @param consumedAt when it was eaten; omit for now. A meal in the future is refused.
 */
public record CreateMealRequest(
        @NotNull UUID householdId,
        @NotNull MealType mealType,
        @Size(min = 1, max = 120) String name,
        Instant consumedAt,
        @NotNull @Valid List<MealItemRequest> items,
        @NotNull @Valid List<MealPortionRequest> portions) {

    public CreateMealCommand toCommand() {
        return new CreateMealCommand(householdId, mealType, name, consumedAt,
                items.stream().map(MealItemRequest::toCommand).toList(),
                portions.stream().map(MealPortionRequest::toCommand).toList());
    }
}
