package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.RuleSetStatus;
import com.familyfood.scoring.domain.ScoreComponent;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.scoring.infra.ScoreComponentRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Computes {@code ProductHealthScore} (D-003): a deterministic, versioned, category-aware
 * score built from a rule set's {@code score_component} rows, dispatching on the rule set's
 * {@code algorithm} (D-059): {@link WeightedMeanAlgorithm} for {@code WEIGHTED_MEAN_V1},
 * {@link PointsNetAlgorithm} for {@code POINTS_NET_V2}.
 *
 * <p>Today's real ACTIVE rule set ("v1") has zero {@code score_component} rows (D-024), so
 * {@link #score(ProductAssessmentInput)} returns {@code status=NOT_YET_SCORED} in production
 * until rule set v2 is activated by migration ({@code V18}, after
 * {@code docs/data/scoring/review.md} is signed) — the honest default, not a bug.
 */
@Service
public class ProductHealthScoreServiceImpl implements ProductHealthScoreService {

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
        return score(input, activeRuleSet());
    }

    @Override
    public ProductHealthView score(ProductAssessmentInput input, String ruleSetVersion) {
        RuleSet ruleSet = ruleSets.findByVersion(ruleSetVersion)
                .orElseThrow(() -> new IllegalArgumentException("no rule_set with version " + ruleSetVersion));
        return score(input, ruleSet);
    }

    /** Package-visible so {@link ProductScoreService} can score against an already-loaded rule set. */
    ProductHealthView score(ProductAssessmentInput input, RuleSet ruleSet) {
        List<ScoreComponent> components = scoreComponents.findByRuleSetId(ruleSet.getId()).stream()
                .filter(c -> c.getCategoryScope() == null || c.getCategoryScope().equals(input.categoryCode()))
                .toList();

        if (components.isEmpty()) {
            return ProductHealthView.notYetScored(input.productVersionId(), ruleSet.getVersion(),
                    PointsNetAlgorithm.processingLevelOf(input), clock.instant());
        }

        return switch (ruleSet.getAlgorithm()) {
            case WEIGHTED_MEAN_V1 -> WeightedMeanAlgorithm.aggregate(input, ruleSet.getVersion(), components, clock.instant());
            case POINTS_NET_V2 -> PointsNetAlgorithm.score(input, ruleSet.getVersion(), ruleSet.getAlgorithmConfig(),
                    components, clock.instant());
        };
    }

    RuleSet activeRuleSet() {
        return ruleSets.findByStatus(RuleSetStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "no ACTIVE rule_set exists — exactly one is seeded by V5__scoring.sql "
                      + "and uq_rule_set_active guarantees at most one; this is a deployment "
                      + "invariant violation, not a normal empty state"));
    }
}
