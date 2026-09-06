package com.familyfood.scoring.application;

import java.util.List;
import java.util.UUID;

/**
 * Everything a scoring computation needs about one product, assembled by the API layer from
 * catalog's {@code ProductQueryService}/{@code ProductDetailDto} — nothing in {@code scoring}
 * imports catalog's domain or infra packages directly, only calls the published
 * {@code ProductQueryService} and maps its DTOs into this.
 */
public record ProductAssessmentInput(
        UUID productVersionId,
        String categoryCode,               // nullable
        List<NutrientFact> nutrients,
        List<String> declaredAllergenCodes // CONTAINS-declared allergen codes only
) {
}
