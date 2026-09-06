package com.familyfood.recommendation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The result of looking for alternatives to one product, for one household. An empty
 * {@code alternatives} list is a first-class, honest result (D-013: "no better option in
 * our data yet" is never padded) — it means no same-category candidate existed, or every
 * candidate would have introduced a new allergen risk for someone the caller may see.
 */
public record AlternativesResult(
        UUID productId,
        UUID householdId,
        List<AlternativeView> alternatives,
        Instant computedAt) {
}
