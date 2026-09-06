package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One entry in a product's declared ingredient list, in label order.
 * {@code ingredientCode} is NULL while the raw label text is unmapped to the
 * {@link Ingredient} reference vocabulary.
 */
@Entity
@Table(name = "product_ingredient")
public class ProductIngredient {

    @Id
    private UUID id;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Column(name = "ingredient_code")
    private String ingredientCode;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_additive", nullable = false)
    private boolean additive;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    protected ProductIngredient() {
        // JPA
    }

    public static ProductIngredient create(UUID productVersionId, String ingredientCode,
                                           String rawText, int position, boolean additive,
                                           UUID sourceId) {
        ProductIngredient ingredient = new ProductIngredient();
        ingredient.id = UUID.randomUUID();
        ingredient.productVersionId = productVersionId;
        ingredient.ingredientCode = ingredientCode;
        ingredient.rawText = rawText;
        ingredient.position = position;
        ingredient.additive = additive;
        ingredient.sourceId = sourceId;
        return ingredient;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public String getIngredientCode() {
        return ingredientCode;
    }

    public String getRawText() {
        return rawText;
    }

    public int getPosition() {
        return position;
    }

    public boolean isAdditive() {
        return additive;
    }

    public UUID getSourceId() {
        return sourceId;
    }
}
