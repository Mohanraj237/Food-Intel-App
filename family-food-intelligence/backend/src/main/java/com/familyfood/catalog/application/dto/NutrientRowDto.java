package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;

/** Mirrors {@code NutrientRow} in frontend/src/lib/catalog.ts. */
public record NutrientRowDto(String nutrientCode,
                             String displayName,
                             BigDecimal amountPer100,
                             BigDecimal amountPerServing,
                             String unit,
                             String basisPer100,
                             boolean isEstimated,
                             BigDecimal confidence,
                             SourceRefDto source) {
}
