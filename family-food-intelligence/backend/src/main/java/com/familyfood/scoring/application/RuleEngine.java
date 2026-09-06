package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.RuleSet;

/**
 * Deterministic matching and evaluation of {@code scoring_rule} rows (D-009, D-010, D-011).
 * "ACTIVE" in this interface's javadoc means a rule whose {@code [active_from, active_to)}
 * window contains {@code now} — a {@code scoring_rule} row has no status column of its own;
 * {@link RuleSet}'s own {@code status} (DRAFT/ACTIVE/RETIRED) is a separate, unrelated axis,
 * which is exactly why tests can call
 * {@link #evaluateGoal(String, String, ProductAssessmentInput, String)} directly against the
 * DRAFT {@code "v1-draft-examples"} rule_set version without that rule_set ever being
 * activated.
 */
public interface RuleEngine {

    /** The version string of the currently ACTIVE rule_set (today: "v1", empty of rules). */
    String activeRuleSetVersion();

    /**
     * Evaluates every scoring_rule for this ruleSetVersion where {@code applies_to_goal} is
     * null or equals {@code goalCode}, {@code applies_to_category} is null or equals
     * {@code input.categoryCode()}, {@code applies_to_age_band} is null or equals
     * {@code ageBand}, and {@code now} is in {@code [active_from, active_to)}. Evaluates on
     * both PER_SERVING and PER_100G where {@code basis=BOTH}; most severe fired verdict wins
     * (severity: DOESNT_FIT > LIMIT > OCCASIONAL > EVERYDAY). A rule whose relevant nutrient
     * fact is absent from {@code input.nutrients()} for a given basis simply does not fire on
     * that basis — never a crash.
     *
     * <p>Returns {@code verdict=INSUFFICIENT_DATA} with a single {@code NO_ACTIVE_RULES}
     * reason only when no scoring_rule matches the scoping/window filters above at all. If
     * at least one rule matches but none of them fire (whether because the measured values
     * are within bounds, or because the relevant nutrient data is missing), the result is
     * {@code verdict=EVERYDAY} — the closed verdict enum's baseline (D-004) — with a
     * {@code MISSING_NUTRIENT_DATA} reason for each nutrient/basis pair that could not be
     * evaluated.
     */
    RuleOutcome evaluateGoal(String ruleSetVersion, String goalCode, ProductAssessmentInput input, String ageBand);
}
