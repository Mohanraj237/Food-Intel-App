package com.familyfood.capture.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * The manual-entry form's submission shape (a parallel frontend workstream builds against
 * this contract). Every constraint here is a shape/size gate only — {@code categoryCode}
 * membership, per-nutrient sign, and every other business rule is enforced by
 * {@code ContributionService}, not by bean validation, because whether a category code
 * exists depends on the database, not the request in isolation.
 *
 * <p>{@code nutrients} and {@code allergenCodes} default to an empty list rather than
 * {@code null}, the same defensive-copy pattern {@code consent.api.GrantConsentRequest}
 * uses for its own optional collection.
 */
public record SubmitContributionRequest(
        @Size(max = 20) String gtin,
        @NotBlank @Size(max = 200) String productName,
        @Size(max = 200) String brandName,
        @Size(max = 40) String categoryCode,
        BigDecimal packSizeValue,
        @Size(max = 20) String packSizeUnit,
        @Size(max = 200) String servingDeclaredText,
        @Valid List<NutrientEntryDto> nutrients,
        @Size(max = 4000) String ingredientsRawText,
        List<@Size(max = 30) String> allergenCodes
) {
    public SubmitContributionRequest {
        nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
        allergenCodes = allergenCodes == null ? List.of() : List.copyOf(allergenCodes);
    }
}
