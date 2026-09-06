package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code rule_set.status} CHECK constraint in V5__scoring.sql. At most one
 * {@link RuleSet} may hold {@code ACTIVE} at a time — enforced by the partial unique index
 * {@code uq_rule_set_active}, not by application code.
 */
public enum RuleSetStatus {
    DRAFT, ACTIVE, RETIRED
}
