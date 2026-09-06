package com.familyfood.recommendation.api;

import com.familyfood.recommendation.application.AlternativesResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirrors {@link AlternativesResult}. An empty {@code alternatives} list is a first-class result (D-013). */
public record AlternativesResponse(
        UUID productId,
        UUID householdId,
        List<AlternativeViewDto> alternatives,
        Instant computedAt) {

    static AlternativesResponse from(AlternativesResult result) {
        return new AlternativesResponse(
                result.productId(),
                result.householdId(),
                result.alternatives().stream().map(AlternativeViewDto::from).toList(),
                result.computedAt());
    }
}
