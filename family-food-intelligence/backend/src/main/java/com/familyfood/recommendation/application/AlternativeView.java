package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.dto.KeyFactDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One candidate alternative product, with its trade-offs (D-013: {@code betterOn} and
 * {@code worseOn} always both present, never only the flattering one). A candidate that
 * would introduce a new allergen risk for anyone the caller is authorized to see is never
 * returned at all (see {@code AlternativesServiceImpl}) — it is not a "worse" alternative,
 * it is not an alternative. {@code stars}/{@code score} are the ACTIVE method's rating when
 * the candidate is rated, else null.
 */
public record AlternativeView(
        UUID productId,
        String name,
        String brandName,
        String categoryCode,
        String imageUrl,
        List<KeyFactDto> keyFacts,
        BigDecimal stars,
        Integer score,
        Integer novaGroup,
        List<AlternativeReason> betterOn,
        List<AlternativeReason> worseOn) {
}
