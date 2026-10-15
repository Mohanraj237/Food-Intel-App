package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Direction;
import com.familyfood.scoring.domain.ScoreComponent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code WEIGHTED_MEAN_V1}: the original score aggregation, unchanged in behaviour from the
 * pre-D-059 {@code ProductHealthScoreServiceImpl.aggregate} — a weighted mean of per-component
 * 0-100 sub-scores, each a linear interpolation over the component's {@code {min,max}}
 * thresholds. Kept for the {@code v1-draft-examples} fixture set and for any rule set
 * authored against that shape; the real v2 content uses {@link PointsNetAlgorithm}.
 *
 * <p><b>Basis.</b> A {@code score_component} row has no {@code basis} column (unlike
 * {@code scoring_rule} — see V5__scoring.sql), so each component is evaluated once: against
 * the per-100 value when declared, falling back to per-serving otherwise. Per-100 is
 * preferred because it is comparable across products regardless of a possibly-implausible
 * declared serving size — the same underlying concern D-010 raises for scoring_rule, applied
 * here by extension since the schema does not specify a choice for score_component; flagged
 * as an interpretation, not a literal requirement.
 */
public final class WeightedMeanAlgorithm {

    private WeightedMeanAlgorithm() {
    }

    /** Free of any repository call so it is unit-testable with hand-built fixtures. */
    public static ProductHealthView aggregate(ProductAssessmentInput input, String ruleSetVersion,
                                              List<ScoreComponent> components, Instant now) {
        Map<String, NutrientFact> factsByCode = new LinkedHashMap<>();
        for (NutrientFact fact : input.nutrients()) {
            factsByCode.put(fact.nutrientCode(), fact);
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<Reason> reasons = new ArrayList<>();

        for (ScoreComponent component : components) {
            NutrientFact fact = factsByCode.get(component.getNutrientCode());
            BigDecimal value = valueFor(fact);
            if (value == null) {
                reasons.add(new Reason(Reason.MISSING_NUTRIENT_DATA, component.getNutrientCode(),
                        null, null, null, component.getStandardRef()));
                continue;
            }
            BigDecimal componentScore = componentScoreFor(component, value);
            weightedSum = weightedSum.add(componentScore.multiply(component.getWeight()));
            totalWeight = totalWeight.add(component.getWeight());
        }

        if (totalWeight.signum() <= 0) {
            // Every component lacked data. Distinct from "components exist but none apply"
            // (handled before this method is ever called) — here at least one component was
            // in scope, but nothing could be evaluated.
            return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                    ProductHealthView.STATUS_NOT_YET_SCORED, null, null, reasons, now);
        }

        int finalScore = weightedSum.divide(totalWeight, MathContext.DECIMAL64)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        finalScore = Math.max(0, Math.min(100, finalScore));

        return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                ProductHealthView.STATUS_SCORED, finalScore, gradeFor(finalScore), reasons, now);
    }

    private static BigDecimal valueFor(NutrientFact fact) {
        if (fact == null) {
            return null;
        }
        return fact.amountPer100() != null ? fact.amountPer100() : fact.amountPerServing();
    }

    /**
     * Maps a raw nutrient value to a 0-100 sub-score via {@code thresholds} =
     * {@code {"min": <number>, "max": <number>}}: the value is clamped to {@code [min, max]}
     * and interpolated linearly. {@link Direction#POSITIVE} (e.g. fibre) scores
     * {@code min→0, max→100}; {@link Direction#NEGATIVE} (e.g. added sugar) scores
     * {@code min→100, max→0}.
     */
    static BigDecimal componentScoreFor(ScoreComponent component, BigDecimal value) {
        BigDecimal min = toBigDecimal(component.getThresholds().get("min"));
        BigDecimal max = toBigDecimal(component.getThresholds().get("max"));
        BigDecimal clamped = value.max(min).min(max);
        BigDecimal range = max.subtract(min);
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal fraction = range.signum() == 0
                ? BigDecimal.ONE
                : clamped.subtract(min).divide(range, MathContext.DECIMAL64);
        return component.getDirection() == Direction.POSITIVE
                ? fraction.multiply(hundred)
                : hundred.subtract(fraction.multiply(hundred));
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(raw.toString());
    }

    /**
     * Score-to-letter banding is a display convention (equal 20-point bands), not a
     * nutrient-content threshold — it does not claim anything about a specific nutrient, so
     * it needs no {@code reference_standard} citation. Shared by both algorithms.
     */
    static String gradeFor(int score) {
        if (score >= 80) {
            return "A";
        }
        if (score >= 60) {
            return "B";
        }
        if (score >= 40) {
            return "C";
        }
        if (score >= 20) {
            return "D";
        }
        return "E";
    }
}
