package com.familyfood.recommendation.api;

import com.familyfood.catalog.application.dto.KeyFactDto;
import com.familyfood.recommendation.application.AlternativeView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Mirrors {@link AlternativeView}. */
public record AlternativeViewDto(
        UUID productId,
        String name,
        String brandName,
        String categoryCode,
        String imageUrl,
        List<KeyFactDto> keyFacts,
        BigDecimal stars,
        Integer score,
        Integer novaGroup,
        List<AlternativeReasonDto> betterOn,
        List<AlternativeReasonDto> worseOn) {

    static AlternativeViewDto from(AlternativeView view) {
        return new AlternativeViewDto(
                view.productId(),
                view.name(),
                view.brandName(),
                view.categoryCode(),
                view.imageUrl(),
                view.keyFacts(),
                view.stars(),
                view.score(),
                view.novaGroup(),
                view.betterOn().stream().map(AlternativeReasonDto::from).toList(),
                view.worseOn().stream().map(AlternativeReasonDto::from).toList());
    }
}
