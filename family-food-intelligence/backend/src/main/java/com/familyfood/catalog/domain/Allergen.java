package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An allergen reference row (the FSSAI-declarable set). Reference data, seeded by
 * V1__extensions_and_reference_data.sql; this module only reads it.
 */
@Entity
@Table(name = "allergen")
public class Allergen {

    @Id
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_fssai_declarable", nullable = false)
    private boolean fssaiDeclarable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Allergen() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isFssaiDeclarable() {
        return fssaiDeclarable;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
