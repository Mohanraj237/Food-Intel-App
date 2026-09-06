package com.familyfood.capture.api;

import java.math.BigDecimal;

/**
 * One nutrient value pair as submitted by hand. Both amounts are optional — a household
 * filling in a form may only have one of the two off the pack — but
 * {@code ContributionService} rejects a negative value for either rather than silently
 * clamping it.
 */
public record NutrientEntryDto(String nutrientCode, BigDecimal amountPer100, BigDecimal amountPerServing) {
}
