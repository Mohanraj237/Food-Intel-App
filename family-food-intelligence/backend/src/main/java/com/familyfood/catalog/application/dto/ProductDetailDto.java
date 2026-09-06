package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@code ProductDetail} in frontend/src/lib/catalog.ts. TypeScript expresses
 * this as {@code ProductSummary} extended with more fields; since {@code extends} on a
 * TS interface flattens into one object at the JSON level, this record repeats every
 * summary field directly rather than nesting a {@link ProductSummaryDto}.
 */
public record ProductDetailDto(UUID id,
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
                               CurrentVersionDto currentVersion,
                               List<NutrientRowDto> nutrients,
                               List<IngredientRowDto> ingredients,
                               List<AllergenRowDto> allergens,
                               List<BarcodeRowDto> barcodes,
                               boolean hasOpenConflict) {

    public static ProductDetailDto from(ProductSummaryDto summary,
                                        CurrentVersionDto currentVersion,
                                        List<NutrientRowDto> nutrients,
                                        List<IngredientRowDto> ingredients,
                                        List<AllergenRowDto> allergens,
                                        List<BarcodeRowDto> barcodes,
                                        boolean hasOpenConflict) {
        return new ProductDetailDto(
                summary.id(), summary.brandName(), summary.name(), summary.packSize(),
                summary.categoryCode(), summary.categoryName(), summary.imageUrl(),
                summary.dataQuality(), summary.servingSizeG(), summary.servingDeclaredText(),
                summary.keyFacts(), currentVersion, nutrients, ingredients, allergens,
                barcodes, hasOpenConflict);
    }
}
