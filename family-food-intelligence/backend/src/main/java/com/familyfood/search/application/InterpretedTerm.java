package com.familyfood.search.application;

import java.math.BigDecimal;

/**
 * One thing {@link SearchQueryParser} read out of the query, echoed back so the UI can show
 * it as a removable chip ("Total sugar under 10 g", "Vegetarian", "Hide: peanut"). Structured,
 * never prose — the frontend renders it from locale templates (D-011's no-free-text rule,
 * applied to search).
 *
 * <p>{@code type}: {@code CATEGORY}, {@code MAX_NUTRIENT}, {@code MIN_NUTRIENT},
 * {@code SORT}, {@code DIET}, {@code EXCLUDE_ALLERGEN}, {@code MAX_NOVA}.
 * {@code source} is the phrase it came from, so a chip can say what it replaced.
 */
public record InterpretedTerm(String type, String code, BigDecimal value, String unit, String source) {
}
