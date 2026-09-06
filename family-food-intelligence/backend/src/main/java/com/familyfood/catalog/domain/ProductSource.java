package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-field provenance (D-015). Every {@link ProductNutrient}, {@link ProductIngredient}
 * and {@link ProductAllergen} row carries a NOT NULL {@code sourceId} pointing here.
 */
@Entity
@Table(name = "product_source")
public class ProductSource {

    @Id
    private UUID id;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "license_note")
    private String licenseNote;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "captured_by_user_id")
    private UUID capturedByUserId;

    @Column
    private BigDecimal confidence;

    protected ProductSource() {
        // JPA
    }

    public static ProductSource create(UUID productVersionId, SourceType sourceType,
                                       String sourceRef, String licenseNote,
                                       UUID capturedByUserId, BigDecimal confidence) {
        ProductSource source = new ProductSource();
        source.id = UUID.randomUUID();
        source.productVersionId = productVersionId;
        source.sourceType = sourceType;
        source.sourceRef = sourceRef;
        source.licenseNote = licenseNote;
        source.capturedByUserId = capturedByUserId;
        source.confidence = confidence;
        return source;
    }

    @PrePersist
    void onPersist() {
        if (capturedAt == null) {
            capturedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getLicenseNote() {
        return licenseNote;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public UUID getCapturedByUserId() {
        return capturedByUserId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }
}
