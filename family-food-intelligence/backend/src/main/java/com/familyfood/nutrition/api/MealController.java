package com.familyfood.nutrition.api;

import com.familyfood.nutrition.application.MealService;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Meal logging with family apportioning (D-039).
 *
 * <p>Requires sign-in; not in {@code SecurityConfig}'s permit-all list. The caller's identity
 * comes from {@link CurrentUser} and never from the path or body — a member id in a URL is the
 * subject being written about, never proof of who is asking.
 *
 * <p>Every authorization decision lives in {@code MealService} and, for the derived
 * observations, in {@code WellnessAccess}: household membership, the portion-member boundary,
 * the D-036 age gate and the D-002 relationship rule. None of them is repeated here, so a second
 * entry point cannot bypass them by being written differently.
 */
@RestController
@RequestMapping("/api/v1/nutrition")
@Tag(name = "Nutrition")
public class MealController {

    /** A month of meals: wide enough for a diary screen, narrow enough to stay a cheap query. */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final MealService meals;
    private final CurrentUser currentUser;

    public MealController(MealService meals, CurrentUser currentUser) {
        this.meals = meals;
        this.currentUser = currentUser;
    }

    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log one meal, with its items and every member's share",
               description = "Items carry the quantity for the whole meal; each portion names a "
                           + "member and the share they ate. Per-member nutrition is derived "
                           + "into their health observations. An empty items or portions list is "
                           + "refused with MEAL_HAS_NO_ITEMS / MEAL_HAS_NO_PORTIONS; a portion "
                           + "naming someone outside the household with "
                           + "PORTION_MEMBER_NOT_IN_HOUSEHOLD.")
    public MealResponse create(@Valid @RequestBody CreateMealRequest request) {
        UUID accountId = currentUser.require().accountId();
        return MealResponse.from(meals.createMeal(accountId, request.toCommand()));
    }

    @GetMapping("/meals")
    @Operation(summary = "A household's meals in a window, newest first")
    public List<MealResponse> list(@RequestParam UUID householdId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to) {
        UUID accountId = currentUser.require().accountId();
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS) : from;
        return meals.listMeals(accountId, householdId, start, end).stream()
                .map(MealResponse::from)
                .toList();
    }

    @GetMapping("/meals/{mealId}")
    @Operation(summary = "One meal, with its items, shares and what each share came to")
    public MealResponse get(@PathVariable UUID mealId) {
        return MealResponse.from(meals.getMeal(currentUser.require().accountId(), mealId));
    }

    @PutMapping("/meals/{mealId}")
    @Operation(summary = "Correct a meal; its items and portions are replaced wholesale",
               description = "Every observation derived from this meal is retracted and restated, "
                           + "so a correction cannot double-count and a member dropped from the "
                           + "meal stops carrying a share of it.")
    public MealResponse update(@PathVariable UUID mealId,
                               @Valid @RequestBody UpdateMealRequest request) {
        UUID accountId = currentUser.require().accountId();
        return MealResponse.from(meals.updateMeal(accountId, mealId, request.toCommand()));
    }

    @DeleteMapping("/meals/{mealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a meal and retract everything derived from it")
    public void delete(@PathVariable UUID mealId) {
        meals.deleteMeal(currentUser.require().accountId(), mealId);
    }

    @PostMapping("/meals/{mealId}/confirm")
    @Operation(summary = "Add your own share of a household meal to your own log",
               description = "D-041: a meal derives immediately only for the member who logged "
                           + "it and children they guard. Another adult's portion is stored but "
                           + "reaches their log only when they confirm it themselves.")
    public MealResponse confirm(@PathVariable UUID mealId) {
        return MealResponse.from(meals.confirmPortion(currentUser.require().accountId(), mealId));
    }

    @GetMapping("/members/{memberId}/meals")
    @Operation(summary = "The meals one member had a share of, in a window",
               description = "Resolved from that member's portions, so a meal they ate none of "
                           + "is not listed as theirs.")
    public List<MealResponse> listForMember(@PathVariable UUID memberId,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to) {
        UUID accountId = currentUser.require().accountId();
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS) : from;
        return meals.listMealsForMember(accountId, memberId, start, end).stream()
                .map(MealResponse::from)
                .toList();
    }
}
