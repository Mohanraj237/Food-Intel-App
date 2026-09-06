package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One rule in a {@link RuleSet} (D-009, D-010). {@code definition} is a small
 * {@code {field, op, value, unit}} shape: {@code field} matches a
 * {@code com.familyfood.scoring.application.NutrientFact#nutrientCode()}, {@code op} is one
 * of {@code GT}/{@code GTE}/{@code LT}/{@code LTE}/{@code EQ}, and {@code value} is compared
 * against the fact's value on whichever of {@link #basis} applies
 * (see {@code com.familyfood.scoring.application.RuleEngineImpl}).
 *
 * <p>{@code appliesToCategory} stores a {@code category.code} (text), not a
 * {@code category.id} (uuid) — see the header comment in V5__scoring.sql for why: it
 * compares directly against {@code ProductAssessmentInput.categoryCode()} without a lookup,
 * and this module holds no JPA relationship into {@code catalog.domain} (the same raw
 * id/code convention D-023 established for cross-module facts).
 */
@Entity
@Table(name = "scoring_rule")
public class ScoringRule {

    @Id
    private UUID id;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(nullable = false)
    private String code;

    @Column(name = "applies_to_goal")
    private String appliesToGoal;

    @Column(name = "applies_to_category")
    private String appliesToCategory;

    @Column(name = "applies_to_age_band")
    private String appliesToAgeBand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Basis basis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> definition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleVerdict verdict;

    @Column(nullable = false)
    private int severity;

    @Column(name = "standard_ref", nullable = false)
    private String standardRef;

    @Column(name = "explanation_template_key", nullable = false)
    private String explanationTemplateKey;

    @Column(nullable = false)
    private String rationale;

    @Column(nullable = false)
    private String owner;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom;

    @Column(name = "active_to")
    private Instant activeTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScoringRule() {
        // JPA
    }

    public static ScoringRule create(UUID ruleSetId, String code, String appliesToGoal,
                                     String appliesToCategory, String appliesToAgeBand,
                                     Basis basis, Map<String, Object> definition,
                                     RuleVerdict verdict, int severity, String standardRef,
                                     String explanationTemplateKey, String rationale,
                                     String owner, Instant activeFrom, Instant activeTo,
                                     Instant now) {
        ScoringRule rule = new ScoringRule();
        rule.id = UUID.randomUUID();
        rule.ruleSetId = ruleSetId;
        rule.code = code;
        rule.appliesToGoal = appliesToGoal;
        rule.appliesToCategory = appliesToCategory;
        rule.appliesToAgeBand = appliesToAgeBand;
        rule.basis = basis;
        rule.definition = definition;
        rule.verdict = verdict;
        rule.severity = severity;
        rule.standardRef = standardRef;
        rule.explanationTemplateKey = explanationTemplateKey;
        rule.rationale = rationale;
        rule.owner = owner;
        rule.activeFrom = activeFrom;
        rule.activeTo = activeTo;
        rule.createdAt = now;
        return rule;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRuleSetId() {
        return ruleSetId;
    }

    public String getCode() {
        return code;
    }

    public String getAppliesToGoal() {
        return appliesToGoal;
    }

    public String getAppliesToCategory() {
        return appliesToCategory;
    }

    public String getAppliesToAgeBand() {
        return appliesToAgeBand;
    }

    public Basis getBasis() {
        return basis;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public RuleVerdict getVerdict() {
        return verdict;
    }

    public int getSeverity() {
        return severity;
    }

    public String getStandardRef() {
        return standardRef;
    }

    public String getExplanationTemplateKey() {
        return explanationTemplateKey;
    }

    public String getRationale() {
        return rationale;
    }

    public String getOwner() {
        return owner;
    }

    public Instant getActiveFrom() {
        return activeFrom;
    }

    public Instant getActiveTo() {
        return activeTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
