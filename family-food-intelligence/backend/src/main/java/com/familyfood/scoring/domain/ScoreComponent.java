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
 * One nutrient's contribution to a {@link RuleSet}'s {@code ProductHealthScore} (D-003).
 * How {@code thresholds} is read depends on the owning rule set's
 * {@link ScoringAlgorithm}:
 *
 * <ul>
 *   <li>{@code WEIGHTED_MEAN_V1}: {@code {min, max}} — values clamped to {@code [min, max]}
 *       and interpolated linearly to a 0-100 sub-score, direction per {@link #direction}
 *       ({@code com.familyfood.scoring.application.WeightedMeanAlgorithm}).
 *   <li>{@code POINTS_NET_V2}: {@code {"type":"BANDS","bands":[{"gt":x,"points":n},…],
 *       "cap":{…}}} — a step function transcribed from the cited standard's band table
 *       ({@code com.familyfood.scoring.application.PointsNetAlgorithm}); {@link #role} says
 *       whether the points add (BASELINE) or subtract (MODIFYING) and
 *       {@link #foodTypeScope} which band table this row is.
 * </ul>
 *
 * <p>Unlike {@link ScoringRule}, this row has no {@code basis} column — see
 * V5__scoring.sql — so both algorithms evaluate each component once, against the per-100
 * value when declared and falling back to per-serving otherwise.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoreComponentRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_type_scope")
    private FoodType foodTypeScope;

    protected ScoreComponent() {
        // JPA
    }

    /**
     * Builds a v1-shaped ({@code {min,max}}) BASELINE component with no food-type scope.
     * Used by the {@code v1-draft-examples} fixtures and {@code WeightedMeanAlgorithmTest}.
     */
    public static ScoreComponent create(UUID ruleSetId, String code, String nutrientCode,
                                        Direction direction, BigDecimal weight,
                                        Map<String, Object> thresholds, String categoryScope,
                                        String standardRef) {
        return create(ruleSetId, code, nutrientCode, direction, weight, thresholds, categoryScope,
                standardRef, ScoreComponentRole.BASELINE, null);
    }

    /** Builds a component with an explicit role and food-type scope (v2 band tables). */
    public static ScoreComponent create(UUID ruleSetId, String code, String nutrientCode,
                                        Direction direction, BigDecimal weight,
                                        Map<String, Object> thresholds, String categoryScope,
                                        String standardRef, ScoreComponentRole role,
                                        FoodType foodTypeScope) {
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
        component.role = role;
        component.foodTypeScope = foodTypeScope;
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

    public ScoreComponentRole getRole() {
        return role;
    }

    public FoodType getFoodTypeScope() {
        return foodTypeScope;
    }
}
