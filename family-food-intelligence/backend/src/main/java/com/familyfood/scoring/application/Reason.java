package com.familyfood.scoring.application;

import java.math.BigDecimal;

/**
 * A structured, deterministic reason — never free text (D-011). Carries every field the
 * frontend's existing {@code why.driver} ({@code "{nutrient}: {value} {unit} {basis}"}) and
 * {@code why.threshold} ({@code "The reference point is {threshold} {unit}, from
 * {standard}."}) locale templates need, so prose rendering never has to invent a value —
 * see the class javadoc on {@code RuleEngine} for why no separate {@code ExplanationBuilder}
 * exists.
 */
public record Reason(
        String code,           // "NO_ACTIVE_RULES" | "MISSING_NUTRIENT_DATA" | "NUTRIENT_EXCEEDS_THRESHOLD" | "NO_GOALS_SET"
        String nutrientCode,   // nullable
        String basis,          // "PER_SERVING" | "PER_100G" | null
        BigDecimal value,      // nullable
        BigDecimal threshold,  // nullable
        String standardRef     // reference_standard.code, nullable
) {
}
