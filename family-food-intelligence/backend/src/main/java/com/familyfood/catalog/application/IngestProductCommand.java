package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.GtinType;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.domain.DataQuality;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything {@link ProductIngestionService} needs to create one new product + its first
 * version, with per-field provenance (D-015). This is the single write path into the
 * catalogue — used by both the Open Food Facts bulk seed
 * ({@code catalog.seed.OpenFoodFactsSeedRunner}) and ops-review contribution approval
 * ({@code ops.application.ContributionReviewService}) — so both honour the same
 * versioning/provenance invariants rather than each hand-rolling their own writes.
 *
 * <p>Nothing here invents a fact: every optional field is nullable/empty precisely when
 * the source did not supply it, and {@code versionStatus}/{@code dataQuality} must be set
 * by the caller to reflect how trustworthy the source actually is — never defaulted to
 * {@code VERIFIED} by this class.
 */
public record IngestProductCommand(
        String name,
        String brandName,
        String categoryCode,
        BigDecimal packSizeValue,
        String packSizeUnit,
        String gtin,
        GtinType gtinType,
        BigDecimal servingSizeG,
        String servingSizeDeclared,
        String ingredientsRawText,
        List<String> ingredientTokens,
        List<NutrientValue> nutrients,
        List<AllergenValue> allergens,
        SourceType sourceType,
        CaptureMethod captureMethod,
        UUID capturedByUserId,
        String sourceRef,
        String licenseNote,
        VersionStatus versionStatus,
        DataQuality dataQuality,
        UUID verifiedByUserId,
        Instant verifiedAt) {

    public record NutrientValue(String nutrientCode, BigDecimal amountPer100, String unit, NutrientBasis basisPer100) {
    }

    public record AllergenValue(String allergenCode, AllergenDeclaration declarationType) {
    }
}
