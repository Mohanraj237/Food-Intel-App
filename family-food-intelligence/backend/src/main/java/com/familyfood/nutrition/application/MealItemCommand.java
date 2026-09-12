package com.familyfood.nutrition.application;

import com.familyfood.nutrition.domain.NutritionAmounts;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One food to put in a meal. Exactly one of {@code productId} and {@code customName} must be
 * given — the same either/or the {@code ck_meal_item_identifies_food} constraint enforces.
 *
 * @param nutritionPer100 caller-supplied per-100 amounts, used only for a custom food. For a
 *                        catalogue product it is ignored: the amounts come from the product's
 *                        current version, and letting a caller override them would make a
 *                        meal log able to restate what a label says.
 */
public record MealItemCommand(UUID productId,
                              String customName,
                              BigDecimal quantityG,
                              NutritionAmounts nutritionPer100) {
}
