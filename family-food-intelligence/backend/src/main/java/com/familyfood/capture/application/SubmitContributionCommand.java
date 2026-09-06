package com.familyfood.capture.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * The application-layer input to {@link ContributionService#submit}. Deliberately shaped
 * independently of {@code capture.api.SubmitContributionRequest}: the API DTO carries bean
 * validation annotations for shape/size, this command carries only data, and
 * {@code ContributionService} is the one place that decides what is actually acceptable
 * (D-023's "raw id/code, no cross-layer object reference" convention, applied one layer
 * down).
 */
public record SubmitContributionCommand(
        String gtin,
        String brandName,
        String productName,
        String categoryCode,
        BigDecimal packSizeValue,
        String packSizeUnit,
        String servingDeclaredText,
        List<NutrientContribution> nutrients,
        String ingredientsRawText,
        List<String> allergenCodes,
        boolean catalogueContributionAcknowledged) {

    public SubmitContributionCommand {
        nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
        allergenCodes = allergenCodes == null ? List.of() : List.copyOf(allergenCodes);
    }

    /** One submitted nutrient value pair, keyed by {@code nutrient.code} (not yet validated). */
    public record NutrientContribution(String nutrientCode, BigDecimal amountPer100,
                                       BigDecimal amountPerServing) {
    }
}
