package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@code ProductSummary} in frontend/src/lib/catalog.ts.
 *
 * <p>{@code imageUrl} is always {@code null} — there is no image storage wired up yet.
 * {@code brandName} is {@code ""} rather than {@code null} when the product has no brand:
 * the frontend type declares it non-nullable, and an unbranded product is not a data
 * error worth surfacing as a missing field.
 */
public record ProductSummaryDto(UUID id,
                                String brandName,
                                String name,
                                PackSizeDto packSize,
                                String categoryCode,
                                String categoryName,
                                String imageUrl,
                                String dataQuality,
                                BigDecimal servingSizeG,
                                String servingDeclaredText,
                                List<KeyFactDto> keyFacts) {
}
