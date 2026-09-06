package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One allergen declaration for a {@link ProductVersion}. */
@Entity
@Table(name = "product_allergen")
public class ProductAllergen {

    @Id
    private UUID id;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Column(name = "allergen_code", nullable = false)
    private String allergenCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "declaration_type", nullable = false)
    private AllergenDeclaration declarationType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column
    private BigDecimal confidence;

    protected ProductAllergen() {
        // JPA
    }

    public static ProductAllergen create(UUID productVersionId, String allergenCode,
                                         AllergenDeclaration declarationType, UUID sourceId,
                                         BigDecimal confidence) {
        ProductAllergen allergen = new ProductAllergen();
        allergen.id = UUID.randomUUID();
        allergen.productVersionId = productVersionId;
        allergen.allergenCode = allergenCode;
        allergen.declarationType = declarationType;
        allergen.sourceId = sourceId;
        allergen.confidence = confidence;
        return allergen;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public String getAllergenCode() {
        return allergenCode;
    }

    public AllergenDeclaration getDeclarationType() {
        return declarationType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }
}
