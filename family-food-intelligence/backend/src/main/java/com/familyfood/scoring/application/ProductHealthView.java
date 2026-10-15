package com.familyfood.scoring.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Product-level, person-independent health assessment (D-003: score exists, is versioned,
 * explainable; separate from personal fit).
 *
 * <p>{@code status}: {@code SCORED} (score, grade and — under {@code POINTS_NET_V2} — stars
 * present), {@code NOT_YET_SCORED} (the rule set has no component that applies to this
 * product; the honest state while the ACTIVE set is the empty {@code v1}, D-024), or
 * {@code INSUFFICIENT_DATA} (components apply but a required input is undeclared, or the
 * cited standard exempts this food type — see {@code coverage} and {@code reasons}).
 *
 * <p>{@code ratingScheme} names what the number means: {@code FSSAI_INR_DRAFT_2022_DERIVED}
 * for the v2 points method (UI copy must say "based on the FSSAI draft Indian Nutrition
 * Rating method", never "the regulator's rating" — D-058), {@code WEIGHTED_MEAN_V1} for the
 * v1 shape, null when nothing was computed.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // a stored product_score.components row may predate a field change
public record ProductHealthView(
        UUID productVersionId,
        String ruleSetVersion,
        String status,               // "SCORED" | "NOT_YET_SCORED" | "INSUFFICIENT_DATA"
        Integer score,               // 0-100, null unless SCORED
        String grade,                // null unless SCORED
        BigDecimal stars,            // 0.5-5.0, null unless SCORED under POINTS_NET_V2
        String ratingScheme,         // nullable
        String foodType,             // "SOLID" | "LIQUID" | "FAT_OIL" | null
        List<ScoreDriver> drivers,
        ProcessingLevel processingLevel, // nullable
        Coverage coverage,
        List<Reason> reasons,
        Instant computedAt) {

    public static final String STATUS_SCORED = "SCORED";
    public static final String STATUS_NOT_YET_SCORED = "NOT_YET_SCORED";
    public static final String STATUS_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
    public static final String SCHEME_POINTS_NET_V2 = "FSSAI_INR_DRAFT_2022_DERIVED";
    public static final String SCHEME_WEIGHTED_MEAN_V1 = "WEIGHTED_MEAN_V1";

    /** The v1 shape: no stars, drivers, processing level or coverage. */
    public ProductHealthView(UUID productVersionId, String ruleSetVersion, String status,
                             Integer score, String grade, List<Reason> reasons, Instant computedAt) {
        this(productVersionId, ruleSetVersion, status, score, grade, null,
                score == null ? null : SCHEME_WEIGHTED_MEAN_V1, null, List.of(), null,
                Coverage.empty(), reasons, computedAt);
    }

    public static ProductHealthView notYetScored(UUID productVersionId, String ruleSetVersion,
                                                 ProcessingLevel processingLevel, Instant now) {
        return new ProductHealthView(productVersionId, ruleSetVersion, STATUS_NOT_YET_SCORED,
                null, null, null, null, null, List.of(), processingLevel, Coverage.empty(),
                List.of(new Reason(Reason.NO_ACTIVE_RULES, null, null, null, null, null)), now);
    }

    @JsonIgnore
    public boolean isScored() {
        return STATUS_SCORED.equals(status);
    }
}
