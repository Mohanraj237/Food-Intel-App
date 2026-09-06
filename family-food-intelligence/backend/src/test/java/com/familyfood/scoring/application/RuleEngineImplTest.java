package com.familyfood.scoring.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit coverage of {@link RuleEngineImpl#matchesOperator}, the one piece of the engine
 * that has no dependency on a database or a Spring context. End-to-end rule matching
 * (scoping, windowing, most-severe-wins, missing-data handling) is covered by
 * {@link RuleEngineIT} against the real migrated schema and seed data.
 */
class RuleEngineImplTest {

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
}
