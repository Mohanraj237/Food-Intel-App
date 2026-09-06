package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A product category. Reference data, seeded by V1__extensions_and_reference_data.sql;
 * this module only reads it. {@code typicalServingMinG}/{@code typicalServingMaxG} drive
 * the "implausible declared serving" flag on {@link ProductVersion} (D-010).
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(name = "typical_serving_min_g")
    private BigDecimal typicalServingMinG;

    @Column(name = "typical_serving_max_g")
    private BigDecimal typicalServingMaxG;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public BigDecimal getTypicalServingMinG() {
        return typicalServingMinG;
    }

    public BigDecimal getTypicalServingMaxG() {
        return typicalServingMaxG;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
