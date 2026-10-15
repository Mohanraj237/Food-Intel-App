package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One entry in a product's declared ingredient list, in label order.
 * {@code ingredientCode} is NULL while the raw label text is unmapped to the
 * {@link Ingredient} reference vocabulary. {@code additiveCode} (V15) is the INS number of
 * the {@link Additive} this token names, when the token carries an INS/E number that the
 * reference table knows; a token can be {@code additive=true} with a null code (the number
 * was recognisable, the table has no row for it).
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

    @Column(name = "additive_code")
    private String additiveCode;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    protected ProductIngredient() {
        // JPA
    }

    public static ProductIngredient create(UUID productVersionId, String ingredientCode,
                                           String rawText, int position, boolean additive,
                                           UUID sourceId) {
        return create(productVersionId, ingredientCode, rawText, position, additive, null, sourceId);
    }

    public static ProductIngredient create(UUID productVersionId, String ingredientCode,
                                           String rawText, int position, boolean additive,
                                           String additiveCode, UUID sourceId) {
        ProductIngredient ingredient = new ProductIngredient();
        ingredient.id = UUID.randomUUID();
        ingredient.productVersionId = productVersionId;
        ingredient.ingredientCode = ingredientCode;
        ingredient.rawText = rawText;
        ingredient.position = position;
        ingredient.additive = additive;
        ingredient.additiveCode = additiveCode;
        ingredient.sourceId = sourceId;
        return ingredient;
    }

    /** Marks the token as an additive; fills the code only when none is set. Returns whether anything changed. */
    public boolean markAdditive(String code) {
        boolean changed = false;
        if (!this.additive) {
            this.additive = true;
            changed = true;
        }
        if (this.additiveCode == null && code != null) {
            this.additiveCode = code;
            changed = true;
        }
        return changed;
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

    public String getAdditiveCode() {
        return additiveCode;
    }

    public UUID getSourceId() {
        return sourceId;
    }
}
