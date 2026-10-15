package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.Basis;
import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.RuleVerdict;
import com.familyfood.scoring.domain.ScoringRule;
import com.familyfood.scoring.infra.RuleSetRepository;
import com.familyfood.scoring.infra.ScoringRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit coverage of {@link RuleEngineImpl}: the operator table, and (D-059) how an
 * engine-derived fact ({@code INR_STARS}, {@code NOVA_GROUP}) is resolved exactly like a
 * nutrient. End-to-end rule matching (scoping, windowing, most-severe-wins, missing-data
 * handling) is covered by {@link RuleEngineIT} against the real migrated schema and seed data.
 */
class RuleEngineImplTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final String VERSION = "unit-fixture";

    private final RuleSetRepository ruleSets = mock(RuleSetRepository.class);
    private final ScoringRuleRepository scoringRules = mock(ScoringRuleRepository.class);
    private final RuleEngineImpl engine = new RuleEngineImpl(ruleSets, scoringRules, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("GT/GTE/LT/LTE/EQ compare exactly as named")
    void operatorsCompareCorrectly() {
        BigDecimal threshold = new BigDecimal("10");

        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("11"), "GT", threshold)).isTrue();
        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10"), "GT", threshold)).isFalse();

        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10"), "GTE", threshold)).isTrue();
        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("9.99"), "GTE", threshold)).isFalse();

        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("9"), "LT", threshold)).isTrue();
        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10"), "LT", threshold)).isFalse();

        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10"), "LTE", threshold)).isTrue();
        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10.01"), "LTE", threshold)).isFalse();

        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10"), "EQ", threshold)).isTrue();
        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10.0"), "EQ", threshold)).isTrue();
        assertThat(RuleEngineImpl.matchesOperator(new BigDecimal("10.01"), "EQ", threshold)).isFalse();
    }

    @Test
    @DisplayName("an unknown operator fails loudly rather than silently matching or not matching")
    void unknownOperatorThrows() {
        assertThatThrownBy(() -> RuleEngineImpl.matchesOperator(BigDecimal.ONE, "BOGUS", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    @DisplayName("a rule on a derived fact fires on PER_100G and reports DERIVED_FACT_THRESHOLD")
    void derivedFactRuleFires() {
        givenRule(rule("STARS_LT", "INR_STARS", "LT", "2.5", Basis.PER_100G, RuleVerdict.OCCASIONAL));
        ProductAssessmentInput input = base().withDerivedFacts(Map.of("INR_STARS", new BigDecimal("1.5")));

        RuleOutcome outcome = engine.evaluateGoal(VERSION, "GENERAL_BALANCED_EATING", input, "A18_30");

        assertThat(outcome.verdict()).isEqualTo("OCCASIONAL");
        assertThat(outcome.firedRuleIds()).hasSize(1);
        assertThat(outcome.reasons()).containsExactly(new Reason("DERIVED_FACT_THRESHOLD", "INR_STARS", "PER_100G",
                new BigDecimal("1.5"), new BigDecimal("2.5"), "FSSAI_INR"));
    }

    @Test
    @DisplayName("a derived fact that is absent (product not scored) is MISSING_NUTRIENT_DATA, never assumed")
    void absentDerivedFactIsMissingData() {
        givenRule(rule("STARS_LT", "INR_STARS", "LT", "2.5", Basis.PER_100G, RuleVerdict.OCCASIONAL));

        RuleOutcome outcome = engine.evaluateGoal(VERSION, "GENERAL_BALANCED_EATING", base(), "A18_30");

        assertThat(outcome.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(outcome.reasons()).extracting(Reason::code).containsExactly("MISSING_NUTRIENT_DATA");
    }

    @Test
    @DisplayName("a derived fact has no per-serving form: on PER_SERVING it is missing, on PER_100G it compares")
    void derivedFactHasNoPerServingForm() {
        givenRule(rule("NOVA4", "NOVA_GROUP", "EQ", "4", Basis.BOTH, RuleVerdict.OCCASIONAL));
        ProductAssessmentInput input = base().withDerivedFacts(Map.of("NOVA_GROUP", new BigDecimal("4")));

        RuleOutcome outcome = engine.evaluateGoal(VERSION, "GENERAL_BALANCED_EATING", input, null);

        assertThat(outcome.verdict()).isEqualTo("OCCASIONAL");
        assertThat(outcome.reasons()).extracting(Reason::code, Reason::basis).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("MISSING_NUTRIENT_DATA", "PER_SERVING"),
                org.assertj.core.groups.Tuple.tuple("DERIVED_FACT_THRESHOLD", "PER_100G"));
    }

    @Test
    @DisplayName("a declared nutrient of the same code wins over a derived fact and keeps the nutrient reason code")
    void nutrientWinsOverDerivedFact() {
        givenRule(rule("SUGAR_GT", "TOTAL_SUGAR_G", "GT", "6", Basis.PER_100G, RuleVerdict.LIMIT));
        ProductAssessmentInput input = new ProductAssessmentInput(UUID.randomUUID(), "BISCUITS",
                List.of(new NutrientFact("TOTAL_SUGAR_G", null, new BigDecimal("25.5"), "g")), List.of())
                .withDerivedFacts(Map.of("TOTAL_SUGAR_G", new BigDecimal("1")));

        RuleOutcome outcome = engine.evaluateGoal(VERSION, "REDUCE_ADDED_SUGAR", input, "A18_30");

        assertThat(outcome.verdict()).isEqualTo("LIMIT");
        assertThat(outcome.reasons()).extracting(Reason::code).containsExactly("NUTRIENT_EXCEEDS_THRESHOLD");
        assertThat(outcome.reasons().get(0).value()).isEqualByComparingTo("25.5");
    }

    // ------------------------------------------------------------------------------ fixtures

    private void givenRule(ScoringRule rule) {
        RuleSet ruleSet = RuleSet.draft(VERSION, "unit fixture", NOW);
        when(ruleSets.findByVersion(VERSION)).thenReturn(Optional.of(ruleSet));
        when(scoringRules.findByRuleSetId(ruleSet.getId())).thenReturn(List.of(rule));
    }

    private static ScoringRule rule(String code, String field, String op, String value, Basis basis, RuleVerdict verdict) {
        return ScoringRule.create(UUID.randomUUID(), code, null, null, null, basis,
                Map.of("field", field, "op", op, "value", new BigDecimal(value), "unit", ""),
                verdict, 1, "FSSAI_INR", "why.driver",
                "UNIT FIXTURE — threshold is arbitrary, never a reviewed value", "scoring-module-test-fixture",
                NOW.minusSeconds(3600), null, NOW);
    }

    private static ProductAssessmentInput base() {
        return new ProductAssessmentInput(UUID.randomUUID(), "BISCUITS", List.of(), List.of());
    }
}
