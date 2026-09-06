package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A GTIN mapped to a product. {@code gtin} is deliberately not unique (AC-4.4): a lookup
 * must check {@code conflictFlag} and the count of distinct active products before
 * trusting a single match — see {@code trg_barcode_conflict} in V4__catalog.sql, which
 * maintains {@code conflictFlag} on every insert/update/delete for the same GTIN.
 */
@Entity
@Table(name = "product_barcode")
public class ProductBarcode {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String gtin;

    @Enumerated(EnumType.STRING)
    @Column(name = "gtin_type", nullable = false)
    private GtinType gtinType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column
    private String source;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "conflict_flag", nullable = false)
    private boolean conflictFlag;

    protected ProductBarcode() {
        // JPA
    }

    public static ProductBarcode create(UUID productId, String gtin, GtinType gtinType,
                                        boolean primary, String source) {
        ProductBarcode barcode = new ProductBarcode();
        barcode.id = UUID.randomUUID();
        barcode.productId = productId;
        barcode.gtin = gtin;
        barcode.gtinType = gtinType;
        barcode.primary = primary;
        barcode.active = true;
        barcode.source = source;
        barcode.conflictFlag = false;
        return barcode;
    }

    @PrePersist
    void onPersist() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getGtin() {
        return gtin;
    }

    public GtinType getGtinType() {
        return gtinType;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isActive() {
        return active;
    }

    public String getSource() {
        return source;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public boolean isConflictFlag() {
        return conflictFlag;
    }
}
