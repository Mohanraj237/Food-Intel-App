package com.familyfood.scoring.api;

import com.familyfood.scoring.application.ProductHealthView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@link ProductHealthView}. {@code status="NOT_YET_SCORED"} (score/grade both
 * {@code null}) is today's honest default for every product — the ACTIVE rule_set has zero
 * score_component rows (D-024) — not a bug and not something the frontend should treat as an
 * error.
 */
public record ProductHealthResponse(
        UUID productVersionId,
        String ruleSetVersion,
        String status,
        Integer score,
        String grade,
        List<ReasonDto> reasons,
        Instant computedAt) {

    static ProductHealthResponse from(ProductHealthView view) {
        return new ProductHealthResponse(
                view.productVersionId(),
                view.ruleSetVersion(),
                view.status(),
                view.score(),
                view.grade(),
                view.reasons().stream().map(ReasonDto::from).toList(),
                view.computedAt());
    }
}
