package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * One weighted nutrient contribution to a {@link RuleSet}'s 0-100 {@code ProductHealthScore}
 * (D-003). {@code thresholds} is a {@code {min, max}} shape read and documented by
 * {@code com.familyfood.scoring.application.ProductHealthScoreServiceImpl}: values are
 * clamped to {@code [min, max]} and interpolated linearly to a 0-100 sub-score, direction of
 * the interpolation depending on {@link #direction}.
 *
 * <p>Unlike {@link ScoringRule}, this row has no {@code basis} column — see
 * V5__scoring.sql — so the aggregation service evaluates each component once, against the
 * per-100g value when declared and falling back to per-serving otherwise.
 *
 * <p>{@code categoryScope} stores a {@code category.code} (text), for the same reason
 * {@link ScoringRule#getAppliesToCategory()} does.
 */
@Entity
@Table(name = "score_component")
public class ScoreComponent {

    @Id
    private UUID id;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(nullable = false)
    private String code;

    @Column(name = "nutrient_code", nullable = false)
    private String nutrientCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false)
    private BigDecimal weight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> thresholds;

    @Column(name = "category_scope")
    private String categoryScope;

    @Column(name = "standard_ref", nullable = false)
    private String standardRef;

    protected ScoreComponent() {
        // JPA
    }

    /**
     * Builds a component. Used both by (a future) reviewed-threshold-set migration loader
     * and, today, exclusively by test fixtures that never persist the result — see
     * {@code ProductHealthScoreServiceImplTest} — since the ACTIVE rule_set has zero real
     * rows of this kind (D-024).
     */
    public static ScoreComponent create(UUID ruleSetId, String code, String nutrientCode,
                                        Direction direction, BigDecimal weight,
                                        Map<String, Object> thresholds, String categoryScope,
                                        String standardRef) {
        ScoreComponent component = new ScoreComponent();
        component.id = UUID.randomUUID();
        component.ruleSetId = ruleSetId;
        component.code = code;
        component.nutrientCode = nutrientCode;
        component.direction = direction;
        component.weight = weight;
        component.thresholds = thresholds;
        component.categoryScope = categoryScope;
        component.standardRef = standardRef;
        return component;
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

    public String getNutrientCode() {
        return nutrientCode;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public Map<String, Object> getThresholds() {
        return thresholds;
    }

    public String getCategoryScope() {
        return categoryScope;
    }

    public String getStandardRef() {
        return standardRef;
    }
}
