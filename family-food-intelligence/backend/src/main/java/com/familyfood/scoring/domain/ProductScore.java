package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted form of a computed {@code ProductHealthScore} (D-003), keyed so at most one row
 * exists per (product_version, rule_set) pair ({@code uq_product_score}).
 *
 * <p>Written by {@code com.familyfood.scoring.application.ProductScoreService} (D-059): a
 * derived product fact, separable from the source facts and recomputable from them at any
 * time (database-design.md §1.4) — not a cache of anything about a person (D-056).
 * {@link #components} holds the full computed view (drivers, reasons, coverage) so a
 * stored row can be served without recomputation; {@link #inputHash} says which input it
 * was computed from, so a changed fact triggers a recompute rather than a stale read.
 * Only {@code SCORED} and {@code INSUFFICIENT_DATA} results are stored — a rule set with no
 * applicable components ({@code NOT_YET_SCORED}) has nothing worth persisting.
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

    @Column
    private BigDecimal stars;

    @Column(nullable = false)
    private String status;

    @Column(name = "input_hash")
    private String inputHash;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "processing_level")
    private Integer processingLevel;

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
        return create(productVersionId, ruleSetId, score, grade, null, "SCORED", null, null,
                components, computedAt);
    }

    public static ProductScore create(UUID productVersionId, UUID ruleSetId, Integer score,
                                      String grade, BigDecimal stars, String status,
                                      String inputHash, Integer processingLevel,
                                      Map<String, Object> components, Instant computedAt) {
        ProductScore productScore = new ProductScore();
        productScore.id = UUID.randomUUID();
        productScore.productVersionId = productVersionId;
        productScore.ruleSetId = ruleSetId;
        productScore.replace(score, grade, stars, status, inputHash, processingLevel, components, computedAt);
        return productScore;
    }

    /**
     * Replaces the stored result in place. A product score is derived data — recomputing it
     * from changed source facts is the point of the row, not an overwrite of a fact (D-014
     * governs {@code product_version}, not this table).
     */
    public void replace(Integer score, String grade, BigDecimal stars, String status,
                        String inputHash, Integer processingLevel,
                        Map<String, Object> components, Instant computedAt) {
        this.score = score;
        this.grade = grade;
        this.stars = stars;
        this.status = status;
        this.inputHash = inputHash;
        this.processingLevel = processingLevel;
        this.components = components;
        this.computedAt = computedAt;
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

    public BigDecimal getStars() {
        return stars;
    }

    public String getStatus() {
        return status;
    }

    public String getInputHash() {
        return inputHash;
    }

    public Integer getProcessingLevel() {
        return processingLevel;
    }

    public Map<String, Object> getComponents() {
        return components;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
