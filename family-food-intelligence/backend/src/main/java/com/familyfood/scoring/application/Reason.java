package com.familyfood.scoring.application;

import java.math.BigDecimal;

/**
 * A structured, deterministic reason — never free text (D-011). Carries every field the
 * frontend's existing {@code why.driver} ({@code "{nutrient}: {value} {unit} {basis}"}) and
 * {@code why.threshold} ({@code "The reference point is {threshold} {unit}, from
 * {standard}."}) locale templates need, so prose rendering never has to invent a value —
 * see the class javadoc on {@code RuleEngine} for why no separate {@code ExplanationBuilder}
 * exists.
 *
 * <p>Codes: {@code NO_ACTIVE_RULES}, {@code MISSING_NUTRIENT_DATA},
 * {@code NUTRIENT_EXCEEDS_THRESHOLD}, {@code NO_GOALS_SET}; and, from engine v2 (D-059),
 * {@code POINTS_BAND} (a factor was placed in a band of the cited points table),
 * {@code ASSUMED_ZERO} (an undeclared modifying factor scored 0 points),
 * {@code DERIVED_FACT_THRESHOLD} (a verdict rule fired on an engine-derived fact such as
 * {@code INR_STARS}), {@code SCORE_NOT_APPLICABLE} (the cited standard exempts this food
 * type from a score).
 */
public record Reason(
        String code,
        String nutrientCode,   // nullable
        String basis,          // "PER_SERVING" | "PER_100G" | null
        BigDecimal value,      // nullable
        BigDecimal threshold,  // nullable
        String standardRef     // reference_standard.code, nullable
) {
    public static final String NO_ACTIVE_RULES = "NO_ACTIVE_RULES";
    public static final String MISSING_NUTRIENT_DATA = "MISSING_NUTRIENT_DATA";
    public static final String NUTRIENT_EXCEEDS_THRESHOLD = "NUTRIENT_EXCEEDS_THRESHOLD";
    public static final String NO_GOALS_SET = "NO_GOALS_SET";
    public static final String POINTS_BAND = "POINTS_BAND";
    public static final String ASSUMED_ZERO = "ASSUMED_ZERO";
    public static final String DERIVED_FACT_THRESHOLD = "DERIVED_FACT_THRESHOLD";
    public static final String SCORE_NOT_APPLICABLE = "SCORE_NOT_APPLICABLE";
}
