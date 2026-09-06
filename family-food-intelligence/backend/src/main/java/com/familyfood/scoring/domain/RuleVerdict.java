package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code scoring_rule.verdict} CHECK constraint in V5__scoring.sql. Deliberately
 * excludes {@code INSUFFICIENT_DATA}: that is a {@code RuleEngine} pipeline outcome for "no
 * rule applied" (see {@code com.familyfood.scoring.application.RuleOutcome}), never a value a
 * rule author stores on a row (D-004).
 */
public enum RuleVerdict {
    EVERYDAY, OCCASIONAL, LIMIT, DOESNT_FIT
}
