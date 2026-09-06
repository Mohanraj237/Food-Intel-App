package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Product — stable identity only. No fact lives here (D-014); those live on
 * {@link ProductVersion}.
 *
 * <p>{@code search_vector} and {@code search_text} are deliberately not mapped: both are
 * maintained entirely by a database trigger (V4__catalog.sql, {@code trg_product_search})
 * and must never be written by the application. Leaving them unmapped means an insert
 * through this entity omits both columns and the trigger fills them in.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "pack_size_value")
    private BigDecimal packSizeValue;

    @Column(name = "pack_size_unit")
    private String packSizeUnit;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "merged_into_product_id")
    private UUID mergedIntoProductId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // JPA
    }

    public static Product create(String name, String normalizedName, UUID brandId,
                                 BigDecimal packSizeValue, String packSizeUnit,
                                 UUID categoryId, ProductStatus status) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.name = name;
        product.normalizedName = normalizedName;
        product.brandId = brandId;
        product.packSizeValue = packSizeValue;
        product.packSizeUnit = packSizeUnit;
        product.categoryId = categoryId;
        product.status = status;
        return product;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public BigDecimal getPackSizeValue() {
        return packSizeValue;
    }

    public String getPackSizeUnit() {
        return packSizeUnit;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public UUID getMergedIntoProductId() {
        return mergedIntoProductId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
