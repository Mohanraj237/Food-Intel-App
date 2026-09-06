package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.dto.KeyFactDto;

import java.util.List;
import java.util.UUID;

/**
 * One candidate alternative product, with its trade-offs (D-013: {@code betterOn} and
 * {@code worseOn} always both present, never only the flattering one). A candidate that
 * would introduce a new allergen risk for anyone the caller is authorized to see is never
 * returned at all (see {@code AlternativesServiceImpl}) — it is not a "worse" alternative,
 * it is not an alternative.
 */
public record AlternativeView(
        UUID productId,
        String name,
        String brandName,
        String categoryCode,
        List<KeyFactDto> keyFacts,
        List<AlternativeReason> betterOn,
        List<AlternativeReason> worseOn) {
}
