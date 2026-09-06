package com.familyfood.scoring.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Product-level, person-independent health assessment (D-003: score exists, is versioned,
 * explainable; separate from personal fit).
 *
 * <p>Today, {@code status} is always {@code "NOT_YET_SCORED"} in production: the ACTIVE
 * rule_set has zero {@code score_component} rows (D-024), so {@code score}/{@code grade}
 * are {@code null} and {@code reasons} carries a single {@code NO_ACTIVE_RULES} entry. This
 * is the honest MVP default, not a bug — see
 * {@code ProductHealthScoreServiceImpl}.
 */
public record ProductHealthView(
        UUID productVersionId,
        String ruleSetVersion,
        String status,      // "SCORED" | "NOT_YET_SCORED"
        Integer score,       // 0-100, null when status = NOT_YET_SCORED
        String grade,        // null when status = NOT_YET_SCORED
        List<Reason> reasons,
        Instant computedAt) {
}
