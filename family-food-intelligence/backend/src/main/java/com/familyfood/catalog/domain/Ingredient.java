package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An ingredient reference row. Reference data, seeded by V4__catalog.sql; this module
 * only reads it — {@code ProductIngredient.ingredientCode} is NULL while unmapped.
 */
@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    private String code;

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    @Column(name = "function_category")
    private String functionCategory;

    @Column(name = "ins_number")
    private String insNumber;

    @Column(name = "evidence_status", nullable = false)
    private String evidenceStatus;

    @Column(name = "explanation_key")
    private String explanationKey;

    protected Ingredient() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public String getFunctionCategory() {
        return functionCategory;
    }

    public String getInsNumber() {
        return insNumber;
    }

    public String getEvidenceStatus() {
        return evidenceStatus;
    }

    public String getExplanationKey() {
        return explanationKey;
    }
}
