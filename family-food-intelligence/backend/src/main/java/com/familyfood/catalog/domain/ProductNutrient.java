package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single nutrient value declared for a {@link ProductVersion}. The row cannot exist
 * without a value ({@code ck_nutrient_has_value} in V4__catalog.sql): a nutrient the pack
 * does not declare is an absent row, never a zero (AC-5.2).
 */
@Entity
@Table(name = "product_nutrient")
public class ProductNutrient {

    @Id
    private UUID id;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Column(name = "nutrient_code", nullable = false)
    private String nutrientCode;

    @Column(name = "amount_per_100")
    private BigDecimal amountPer100;

    @Column(name = "amount_per_serving")
    private BigDecimal amountPerServing;

    @Column(nullable = false)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis_per_100")
    private NutrientBasis basisPer100;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_basis", nullable = false)
    private NutrientBasis declaredBasis;

    @Column(name = "is_estimated", nullable = false)
    private boolean estimated;

    @Column
    private BigDecimal confidence;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    protected ProductNutrient() {
        // JPA
    }

    public static ProductNutrient create(UUID productVersionId, String nutrientCode,
                                         BigDecimal amountPer100, BigDecimal amountPerServing,
                                         String unit, NutrientBasis basisPer100,
                                         NutrientBasis declaredBasis, boolean estimated,
                                         BigDecimal confidence, UUID sourceId) {
        ProductNutrient nutrient = new ProductNutrient();
        nutrient.id = UUID.randomUUID();
        nutrient.productVersionId = productVersionId;
        nutrient.nutrientCode = nutrientCode;
        nutrient.amountPer100 = amountPer100;
        nutrient.amountPerServing = amountPerServing;
        nutrient.unit = unit;
        nutrient.basisPer100 = basisPer100;
        nutrient.declaredBasis = declaredBasis;
        nutrient.estimated = estimated;
        nutrient.confidence = confidence;
        nutrient.sourceId = sourceId;
        return nutrient;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public String getNutrientCode() {
        return nutrientCode;
    }

    public BigDecimal getAmountPer100() {
        return amountPer100;
    }

    public BigDecimal getAmountPerServing() {
        return amountPerServing;
    }

    public String getUnit() {
        return unit;
    }

    public NutrientBasis getBasisPer100() {
        return basisPer100;
    }

    public NutrientBasis getDeclaredBasis() {
        return declaredBasis;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public UUID getSourceId() {
        return sourceId;
    }
}
