package com.familyfood.scoring.application;

import java.util.List;

/**
 * Result of evaluating every applicable {@code scoring_rule} for one goal against one
 * product (D-010: both bases, most severe wins).
 */
public record RuleOutcome(
        String verdict, // "EVERYDAY"|"OCCASIONAL"|"LIMIT"|"DOESNT_FIT"|"INSUFFICIENT_DATA" (the last only when no rule applied)
        List<Reason> reasons,
        List<String> firedRuleIds) {
}
