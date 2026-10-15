package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@code ProductSummary} in frontend/src/lib/catalog.ts.
 *
 * <p>{@code imageUrl} is {@code null} until a {@code product_image} row exists for the
 * product (see {@code catalog.application.ProductImageService}) — most products still have
 * no image today, since the Open Food Facts image backfill is a separate, opt-in step from
 * the base catalogue import (see {@code catalog.seed.OpenFoodFactsImageBackfillRunner}).
 * {@code brandName} is {@code ""} rather than {@code null} when the product has no brand:
 * the frontend type declares it non-nullable, and an unbranded product is not a data
 * error worth surfacing as a missing field.
 *
 * <p>Search v2 fields (D-061): {@code dietFlag}/{@code dietFlagEstimated} and
 * {@code novaGroup} from the current version, {@code allergenCodes} the codes the label
 * declares as CONTAINS or MAY_CONTAIN (for a card's allergen marker against the viewer's own
 * profile — never a free-from claim), and {@code assessment}, filled only by search.
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
                                List<KeyFactDto> keyFacts,
                                String dietFlag,
                                boolean dietFlagEstimated,
                                Integer novaGroup,
                                List<String> allergenCodes,
                                AssessmentBadgeDto assessment) {

    public ProductSummaryDto withAssessment(AssessmentBadgeDto badge) {
        return new ProductSummaryDto(id, brandName, name, packSize, categoryCode, categoryName, imageUrl,
                dataQuality, servingSizeG, servingDeclaredText, keyFacts, dietFlag, dietFlagEstimated,
                novaGroup, allergenCodes, badge);
    }
}
