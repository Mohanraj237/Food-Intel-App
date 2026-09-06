package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Basis;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.RuleVerdict;
import com.familyfood.scoring.domain.ScoringRule;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.scoring.infra.ScoringRuleRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end {@link RuleEngine} behaviour against the real migrated schema and V5__scoring.sql
 * seed data — the real ACTIVE "v1" rule_set (zero rows, D-024) and the DRAFT
 * "v1-draft-examples" rule_set (illustrative rules, see its seed comment for why its threshold
 * numbers are fabricated for test purposes only).
 *
 * <p>The {@code v1} test below is the one that matters most for production honesty: it proves
 * that against the real ACTIVE rule_set, {@code evaluateGoal} returns
 * {@code INSUFFICIENT_DATA}/{@code NO_ACTIVE_RULES} for any goal, not a guessed verdict.
 */
class RuleEngineIT extends IntegrationTestBase {

    private static final String ACTIVE_VERSION = "v1";
    private static final String DRAFT_VERSION = "v1-draft-examples";

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private RuleSetRepository ruleSetRepository;

    @Autowired
    private ScoringRuleRepository scoringRuleRepository;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("activeRuleSetVersion() resolves to the seeded ACTIVE \"v1\" rule_set")
    void activeRuleSetVersionIsV1() {
        assertThat(ruleEngine.activeRuleSetVersion()).isEqualTo(ACTIVE_VERSION);
    }

    @Test
    @DisplayName("evaluateGoal against the real ACTIVE v1 rule_set is always INSUFFICIENT_DATA/NO_ACTIVE_RULES")
    void evaluateGoalAgainstRealActiveRuleSetIsHonestlyEmpty() {
        ProductAssessmentInput input = input("BISCUITS", fact("ADDED_SUGAR_G", null, "60"));

        RuleOutcome outcome = ruleEngine.evaluateGoal(ACTIVE_VERSION, "REDUCE_ADDED_SUGAR", input, "A18_30");

        assertThat(outcome.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(outcome.reasons()).containsExactly(
                new Reason("NO_ACTIVE_RULES", null, null, null, null, null));
        assertThat(outcome.firedRuleIds()).isEmpty();
    }

    @Test
    @DisplayName("a LIMIT rule fires on PER_100G when the declared value exceeds its threshold")
    void limitRuleFiresOnExceededThreshold() {
        ProductAssessmentInput input = input(null, fact("ADDED_SUGAR_G", null, "30")); // > 22.5, <= 45

        RuleOutcome outcome = ruleEngine.evaluateGoal(DRAFT_VERSION, "REDUCE_ADDED_SUGAR", input, "A18_30");

        assertThat(outcome.verdict()).isEqualTo("LIMIT");
        assertThat(outcome.firedRuleIds()).hasSize(1);
        assertThat(outcome.reasons()).contains(new Reason(
                "NUTRIENT_EXCEEDS_THRESHOLD", "ADDED_SUGAR_G", "PER_100G",
                new BigDecimal("30"), new BigDecimal("22.5"), "WHO_FREE_SUGARS_2015"));
    }

    @Test
    @DisplayName("most-severe-wins: DOESNT_FIT beats LIMIT when both sugar rules fire together")
    void mostSevereVerdictWinsWhenMultipleRulesFire() {
        ProductAssessmentInput input = input(null, fact("ADDED_SUGAR_G", null, "50")); // > 45 fires both

        RuleOutcome outcome = ruleEngine.evaluateGoal(DRAFT_VERSION, "REDUCE_ADDED_SUGAR", input, "A18_30");

        assertThat(outcome.verdict()).isEqualTo("DOESNT_FIT");
        assertThat(outcome.firedRuleIds()).hasSize(2);
    }

    @Test
    @DisplayName("a rule does not fire, and does not crash, when its nutrient data is absent")
    void ruleContributesNothingWhenDataMissing() {
        ProductAssessmentInput input = input(null); // no ADDED_SUGAR_G fact at all

        RuleOutcome outcome = ruleEngine.evaluateGoal(DRAFT_VERSION, "REDUCE_ADDED_SUGAR", input, "A18_30");

        // Candidates existed (both sugar rules apply to this goal) but neither could be
        // evaluated: verdict is INSUFFICIENT_DATA, not EVERYDAY — defaulting to "fine" about a
        // nutrient we know nothing about would be exactly the fabrication-from-missing-data this
        // product refuses to do (orchestrator correction, post-review; see RuleEngineImpl).
        assertThat(outcome.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(outcome.firedRuleIds()).isEmpty();
        assertThat(outcome.reasons()).extracting(Reason::code).contains("MISSING_NUTRIENT_DATA");
    }

    @Test
    @DisplayName("category, age-band and goal scoping are all applied")
    void categoryAgeBandAndGoalScopingAreApplied() {
        ProductAssessmentInput matching = input("BISCUITS", fact("SODIUM_MG", "450", null));

        RuleOutcome fired = ruleEngine.evaluateGoal(DRAFT_VERSION, "REDUCE_SODIUM", matching, "A18_30");
        assertThat(fired.verdict()).isEqualTo("OCCASIONAL");

        // Wrong category: the sodium rule is scoped to BISCUITS only.
        ProductAssessmentInput wrongCategory = input("DAIRY", fact("SODIUM_MG", "450", null));
        RuleOutcome noCategoryMatch = ruleEngine.evaluateGoal(DRAFT_VERSION, "REDUCE_SODIUM", wrongCategory, "A18_30");
        assertThat(noCategoryMatch.verdict()).isEqualTo("INSUFFICIENT_DATA");

        // Wrong age band: the sodium rule is scoped to A18_30 only.
        RuleOutcome noAgeBandMatch = ruleEngine.evaluateGoal(DRAFT_VERSION, "REDUCE_SODIUM", matching, "C1_3");
        assertThat(noAgeBandMatch.verdict()).isEqualTo("INSUFFICIENT_DATA");

        // Wrong goal: no scoring_rule under this DRAFT rule_set applies to this goal.
        RuleOutcome noGoalMatch = ruleEngine.evaluateGoal(DRAFT_VERSION, "INCREASE_FIBRE", matching, "A18_30");
        assertThat(noGoalMatch.verdict()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    @DisplayName("basis=BOTH evaluates PER_SERVING and PER_100G independently, firing on whichever exceeds")
    void bothBasisEvaluatesIndependently() {
        UUID ruleSetId = ruleSetRepository.findByVersion(DRAFT_VERSION).orElseThrow().getId();
        Instant now = clock.instant();
        String uniqueSuffix = UUID.randomUUID().toString();
        // A goal used by no other rule, seeded or test-inserted, so this fixture can never be
        // picked up by (or contaminate) another test's scoping assertions — see
        // categoryAgeBandAndGoalScopingAreApplied, which depends on "REDUCE_SODIUM" resolving
        // to exactly the one seeded, BISCUITS-scoped rule.
        String isolatedGoal = "IT_BOTH_BASIS_GOAL_" + uniqueSuffix;

        scoringRuleRepository.save(ScoringRule.create(ruleSetId,
                "IT_BOTH_SODIUM_" + uniqueSuffix, isolatedGoal, null, null, Basis.BOTH,
                Map.of("field", "SODIUM_MG", "op", "GTE", "value", 300, "unit", "mg"),
                RuleVerdict.LIMIT, 2, "WHO_SODIUM_2012", "why.driver",
                "ILLUSTRATIVE EXAMPLE — not a reviewed clinical threshold, used only to test "
              + "the rule engine mechanism, never activated",
                "scoring-module-test-fixture", now.minus(1, ChronoUnit.DAYS), null, now));

        // Below threshold on per-serving, above on per-100g -> fires on PER_100G only.
        ProductAssessmentInput input = input(null, fact("SODIUM_MG", "250", "350"));
        RuleOutcome outcome = ruleEngine.evaluateGoal(DRAFT_VERSION, isolatedGoal, input, null);

        assertThat(outcome.verdict()).isEqualTo("LIMIT");
        assertThat(outcome.reasons()).anySatisfy(reason -> {
            assertThat(reason.code()).isEqualTo("NUTRIENT_EXCEEDS_THRESHOLD");
            assertThat(reason.basis()).isEqualTo("PER_100G");
        });
        assertThat(outcome.reasons()).noneSatisfy(reason ->
                assertThat(reason.basis()).isEqualTo("PER_SERVING"));
    }

    @Test
    @DisplayName("a rule outside its active_from/active_to window is excluded from evaluation")
    void activeWindowIsRespected() {
        UUID ruleSetId = ruleSetRepository.findByVersion(DRAFT_VERSION).orElseThrow().getId();
        Instant now = clock.instant();
        String goal = "AVOID_TRANS_FAT"; // a goal no seeded illustrative rule uses, to isolate this test
        String uniqueSuffix = UUID.randomUUID().toString();

        // Already expired.
        scoringRuleRepository.save(ScoringRule.create(ruleSetId,
                "IT_EXPIRED_" + uniqueSuffix, goal, null, null, Basis.PER_100G,
                Map.of("field", "TRANS_FAT_G", "op", "GTE", "value", 0, "unit", "g"),
                RuleVerdict.DOESNT_FIT, 4, "WHO_FREE_SUGARS_2015", "why.driver",
                "ILLUSTRATIVE EXAMPLE — not a reviewed clinical threshold, used only to test "
              + "the rule engine mechanism, never activated",
                "scoring-module-test-fixture",
                now.minus(400, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS), now));

        // Not yet active.
        scoringRuleRepository.save(ScoringRule.create(ruleSetId,
                "IT_FUTURE_" + uniqueSuffix, goal, null, null, Basis.PER_100G,
                Map.of("field", "TRANS_FAT_G", "op", "GTE", "value", 0, "unit", "g"),
                RuleVerdict.DOESNT_FIT, 4, "WHO_FREE_SUGARS_2015", "why.driver",
                "ILLUSTRATIVE EXAMPLE — not a reviewed clinical threshold, used only to test "
              + "the rule engine mechanism, never activated",
                "scoring-module-test-fixture", now.plus(10, ChronoUnit.DAYS), null, now));

        ProductAssessmentInput input = input(null, fact("TRANS_FAT_G", null, "5"));
        RuleOutcome outcome = ruleEngine.evaluateGoal(DRAFT_VERSION, goal, input, "A18_30");

        assertThat(outcome.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(outcome.reasons()).containsExactly(
                new Reason("NO_ACTIVE_RULES", null, null, null, null, null));
    }

    @Test
    @DisplayName("the DRAFT rule_set itself is never ACTIVE, exactly as V5__scoring.sql seeds it")
    void draftRuleSetIsNeverActive() {
        RuleSet draft = ruleSetRepository.findByVersion(DRAFT_VERSION).orElseThrow();
        assertThat(draft.getStatus().name()).isEqualTo("DRAFT");
    }

    // ------------------------------------------------------------------------------ fixtures

    private static ProductAssessmentInput input(String categoryCode, NutrientFact... facts) {
        return new ProductAssessmentInput(UUID.randomUUID(), categoryCode, List.of(facts), List.of());
    }

    private static NutrientFact fact(String code, String perServing, String per100) {
        return new NutrientFact(code,
                perServing == null ? null : new BigDecimal(perServing),
                per100 == null ? null : new BigDecimal(per100),
                "g");
    }
}
