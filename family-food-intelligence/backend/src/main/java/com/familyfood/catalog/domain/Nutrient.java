package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A nutrient reference row. Reference data, seeded by
 * V1__extensions_and_reference_data.sql; this module only reads it.
 * {@code isCore} marks the nutrients shown in a {@code ProductSummary.keyFacts}.
 */
@Entity
@Table(name = "nutrient")
public class Nutrient {

    @Id
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "canonical_unit", nullable = false)
    private String canonicalUnit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_core", nullable = false)
    private boolean core;

    protected Nutrient() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCanonicalUnit() {
        return canonicalUnit;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isCore() {
        return core;
    }
}
