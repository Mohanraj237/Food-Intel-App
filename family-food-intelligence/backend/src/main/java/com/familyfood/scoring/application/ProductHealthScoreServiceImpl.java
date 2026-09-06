package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Direction;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.RuleSetStatus;
import com.familyfood.scoring.domain.ScoreComponent;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.scoring.infra.ScoreComponentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes {@code ProductHealthScore} (D-003): a deterministic, versioned, category-aware
 * 0-100 score built from the ACTIVE rule_set's {@code score_component} rows.
 *
 * <p>Today's real ACTIVE rule_set ("v1") has zero {@code score_component} rows (D-024), so
 * {@link #score} always returns {@code status=NOT_YET_SCORED} in production — the honest MVP
 * default, not a bug. The aggregation math below ({@link #aggregate}) is written and tested
 * for real against synthetic {@link ScoreComponent} fixtures built directly in tests (never
 * persisted, never routed through the ACTIVE or any DRAFT rule_set — inserting a second
 * ACTIVE row would violate {@code uq_rule_set_active} and inserting under the DRAFT
 * "v1-draft-examples" rule_set would misleadingly suggest a reviewed component set exists),
 * so the moment a nutrition-reviewed component set is authored and activated as a new
 * rule_set version, this starts producing real scores without a rewrite.
 *
 * <p><b>Basis.</b> A {@code score_component} row has no {@code basis} column (unlike
 * {@code scoring_rule} — see V5__scoring.sql), so each component is evaluated once: against
 * the per-100g value when declared, falling back to per-serving otherwise. Per-100g is
 * preferred because it is comparable across products regardless of a possibly-implausible
 * declared serving size — the same underlying concern D-010 raises for scoring_rule, applied
 * here by extension since the schema does not specify a choice for score_component; flagged
 * as an interpretation, not a literal requirement.
 */
@Service
public class ProductHealthScoreServiceImpl implements ProductHealthScoreService {

    private static final String STATUS_NOT_YET_SCORED = "NOT_YET_SCORED";
    private static final String STATUS_SCORED = "SCORED";
    private static final String REASON_NO_ACTIVE_RULES = "NO_ACTIVE_RULES";
    private static final String REASON_MISSING_NUTRIENT_DATA = "MISSING_NUTRIENT_DATA";

    private final RuleSetRepository ruleSets;
    private final ScoreComponentRepository scoreComponents;
    private final Clock clock;

    public ProductHealthScoreServiceImpl(RuleSetRepository ruleSets,
                                         ScoreComponentRepository scoreComponents,
                                         Clock clock) {
        this.ruleSets = ruleSets;
        this.scoreComponents = scoreComponents;
        this.clock = clock;
    }

    @Override
    public ProductHealthView score(ProductAssessmentInput input) {
        RuleSet activeRuleSet = ruleSets.findByStatus(RuleSetStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "no ACTIVE rule_set exists — exactly one is seeded by V5__scoring.sql "
                      + "and uq_rule_set_active guarantees at most one; this is a deployment "
                      + "invariant violation, not a normal empty state"));

        List<ScoreComponent> components = scoreComponents.findByRuleSetId(activeRuleSet.getId()).stream()
                .filter(c -> c.getCategoryScope() == null || c.getCategoryScope().equals(input.categoryCode()))
                .toList();

        if (components.isEmpty()) {
            return new ProductHealthView(input.productVersionId(), activeRuleSet.getVersion(),
                    STATUS_NOT_YET_SCORED, null, null,
                    List.of(new Reason(REASON_NO_ACTIVE_RULES, null, null, null, null, null)),
                    clock.instant());
        }

        return aggregate(input, activeRuleSet.getVersion(), components, clock.instant());
    }

    /**
     * Package-private and free of any repository call so it is unit-testable with hand-built
     * {@link ScoreComponent} fixtures and no Spring context or database — see
     * {@code ProductHealthScoreServiceImplTest}.
     */
    ProductHealthView aggregate(ProductAssessmentInput input, String ruleSetVersion,
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
                reasons.add(new Reason(REASON_MISSING_NUTRIENT_DATA, component.getNutrientCode(),
                        null, null, null, component.getStandardRef()));
                continue;
            }
            BigDecimal componentScore = componentScoreFor(component, value);
            weightedSum = weightedSum.add(componentScore.multiply(component.getWeight()));
            totalWeight = totalWeight.add(component.getWeight());
        }

        if (totalWeight.signum() <= 0) {
            // Every component lacked data. Distinct from "components exist but none apply"
            // (handled in score() before this method is ever called) — here at least one
            // component was in scope, but nothing could be evaluated.
            return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                    STATUS_NOT_YET_SCORED, null, null, reasons, now);
        }

        int finalScore = weightedSum.divide(totalWeight, MathContext.DECIMAL64)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        finalScore = Math.max(0, Math.min(100, finalScore));

        return new ProductHealthView(input.productVersionId(), ruleSetVersion,
                STATUS_SCORED, finalScore, gradeFor(finalScore), reasons, now);
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
     * {@code min→100, max→0}. This shape is this implementation's own choice — the schema
     * comment in V5__scoring.sql says only "thresholds jsonb NOT NULL" — documented here so a
     * future rule author knows the contract; exercised only by test fixtures today since no
     * real score_component exists yet (D-024).
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
     * it needs no {@code reference_standard} citation. Exercised only by test fixtures today
     * (D-024); revisit independently of any future rule_set activation.
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
