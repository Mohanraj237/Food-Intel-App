package com.familyfood.nutrition.domain;

/**
 * The eating occasions a meal may be filed under — the same closed set as the
 * {@code meal.meal_type} CHECK constraint in V13__nutrition_goals_habits.sql.
 *
 * <p>A slot in the day and nothing more. There is no ordering, no weighting and no "main
 * meal" flag, because any of those would be the start of judging what belongs in a slot.
 */
public enum MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK
}
