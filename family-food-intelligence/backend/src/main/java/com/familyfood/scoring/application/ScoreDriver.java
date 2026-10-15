package com.familyfood.scoring.application;

import java.math.BigDecimal;

/**
 * One factor's contribution to a v2 score — the "why this number" breakdown
 * (scoring-methodology.md §2.7). {@code direction} is {@code NEGATIVE} for a baseline
 * (risk-increasing) factor whose points add to the net and {@code POSITIVE} for a modifying
 * factor whose points subtract. {@code threshold} is the band edge the value crossed (the
 * {@code gt} of the band that set {@code points}), or null when the value sat in band 0.
 * {@code isEstimated} mirrors the catalogue fact's own flag; {@code assumed} is true when the
 * factor was undeclared and scored 0 under the {@code ASSUMED_ZERO} convention.
 */
public record ScoreDriver(
        String code,
        String direction,
        String nutrientCode,
        BigDecimal value,        // null when assumed
        String unit,
        String basis,            // "PER_100G" | "PER_100ML"
        int points,
        int maxPoints,
        BigDecimal threshold,    // nullable
        String standardRef,
        String explanationKey,
        boolean isEstimated,
        boolean assumed) {
}
