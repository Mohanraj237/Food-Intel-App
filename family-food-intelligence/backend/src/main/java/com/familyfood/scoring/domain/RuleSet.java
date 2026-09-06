package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A versioned set of {@link ScoringRule}/{@link ScoreComponent} rows (D-014): a change is
 * always a new row, never a mutation of an old one. At most one row may hold
 * {@link RuleSetStatus#ACTIVE} at a time — enforced by {@code uq_rule_set_active}, a partial
 * unique index in V5__scoring.sql, not merely an application-level check.
 *
 * <p>Today's ACTIVE row ({@code version = "v1"}) intentionally references zero
 * {@code scoring_rule}/{@code score_component} rows (D-024): no numeric health threshold in
 * this repository is nutrition-reviewed yet, so shipping a guessed one would be exactly the
 * fabricated medical judgment the project forbids. A separate DRAFT row
 * ({@code version = "v1-draft-examples"}) carries illustrative rules used only to prove the
 * rule engine's mechanism in tests. Nothing in this codebase transitions a rule_set's
 * status — activating a reviewed threshold set is a future migration, per D-024's reversal
 * condition, not a runtime action this class or its service ever performs.
 */
@Entity
@Table(name = "rule_set")
public class RuleSet {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleSetStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RuleSet() {
        // JPA
    }

    /**
     * Builds a new DRAFT rule_set. There is deliberately no {@code activate(...)} method on
     * this class: nothing in this codebase transitions a rule_set to ACTIVE (D-024) — that is
     * a future migration once a reviewed threshold set exists, not application behaviour.
     */
    public static RuleSet draft(String version, String notes, Instant now) {
        RuleSet ruleSet = new RuleSet();
        ruleSet.id = UUID.randomUUID();
        ruleSet.version = version;
        ruleSet.status = RuleSetStatus.DRAFT;
        ruleSet.notes = notes;
        ruleSet.createdAt = now;
        return ruleSet;
    }

    public UUID getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public RuleSetStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
