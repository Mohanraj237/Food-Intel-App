package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted form of a computed {@code ProductHealthScore} (D-003), keyed so at most one row
 * exists per (product_version, rule_set) pair.
 *
 * <p>Not written by this phase's application code: {@code ProductHealthScoreServiceImpl}
 * computes on demand rather than caching, since the ACTIVE rule_set has no
 * {@code score_component} rows to score against yet (D-024) — there is nothing worth
 * persisting today. The table and this entity are modelled in full now, ahead of that
 * caching decision, so activating a real rule_set later does not require a migration or an
 * entity change, only a decision about whether {@link #score} is called synchronously or
 * written here.
 */
@Entity
@Table(name = "product_score")
public class ProductScore {

    @Id
    private UUID id;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column
    private Integer score;

    @Column
    private String grade;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> components;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected ProductScore() {
        // JPA
    }

    public static ProductScore create(UUID productVersionId, UUID ruleSetId, Integer score,
                                      String grade, Map<String, Object> components,
                                      Instant computedAt) {
        ProductScore productScore = new ProductScore();
        productScore.id = UUID.randomUUID();
        productScore.productVersionId = productVersionId;
        productScore.ruleSetId = ruleSetId;
        productScore.score = score;
        productScore.grade = grade;
        productScore.components = components;
        productScore.computedAt = computedAt;
        return productScore;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public UUID getRuleSetId() {
        return ruleSetId;
    }

    public Integer getScore() {
        return score;
    }

    public String getGrade() {
        return grade;
    }

    public Map<String, Object> getComponents() {
        return components;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
