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
 * A field-level disagreement between two sources for the same {@link ProductVersion}.
 * An OPEN row forces {@code data_quality = CONFLICTING} on the version by trigger
 * ({@code trg_conflict_quality}, AC-5.3) — this module only reads {@code status} to
 * report {@code ProductDetail.hasOpenConflict}; it never resolves a conflict.
 *
 * <p>Only the columns this module reads or a test fixture needs to write are mapped;
 * the resolution workflow (owned by a future capture/review module) is not represented
 * here.
 */
@Entity
@Table(name = "product_conflict")
public class ProductConflict {

    @Id
    private UUID id;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductConflict() {
        // JPA
    }

    public static ProductConflict open(UUID productVersionId, String fieldPath) {
        ProductConflict conflict = new ProductConflict();
        conflict.id = UUID.randomUUID();
        conflict.productVersionId = productVersionId;
        conflict.fieldPath = fieldPath;
        conflict.status = Status.OPEN;
        return conflict;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Mirrors the {@code product_conflict.status} CHECK constraint in V4__catalog.sql. */
    public enum Status {
        OPEN, RESOLVED
    }
}
