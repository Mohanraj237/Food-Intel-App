package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What people type for a category ("atta", "namkeen", "cold drink") → the closed category
 * code (V15 {@code category_alias}). Reference data, seeded by migration, read-only here.
 */
@Entity
@Table(name = "category_alias")
public class CategoryAlias {

    @Id
    private String alias;

    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    protected CategoryAlias() {
        // JPA
    }

    public String getAlias() {
        return alias;
    }

    public String getCategoryCode() {
        return categoryCode;
    }
}
