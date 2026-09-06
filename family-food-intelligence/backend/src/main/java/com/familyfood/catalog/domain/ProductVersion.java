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
 * Product version — where every fact lives (D-014). Exactly one {@code isCurrent=true}
 * row per product is enforced by a partial unique index
 * ({@code uq_product_current_version}) in V4__catalog.sql; this module never needs to
 * enforce that itself.
 */
@Entity
@Table(name = "product_version")
public class ProductVersion {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "serving_size_g")
    private BigDecimal servingSizeG;

    @Column(name = "serving_size_declared")
    private String servingSizeDeclared;

    @Column(name = "serving_plausible")
    private Boolean servingPlausible;

    @Column(name = "servings_per_pack")
    private BigDecimal servingsPerPack;

    @Column(name = "ingredients_raw")
    private String ingredientsRaw;

    @Column(name = "ingredients_language")
    private String ingredientsLanguage;

    @Column(name = "label_image_id")
    private UUID labelImageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_method", nullable = false)
    private CaptureMethod captureMethod;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "captured_by_user_id")
    private UUID capturedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verification_pass", nullable = false)
    private int verificationPass;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_quality", nullable = false)
    private DataQuality dataQuality;

    @Column(name = "freshness_due_at")
    private Instant freshnessDueAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductVersion() {
        // JPA
    }

    public static ProductVersion create(UUID productId, int versionNo, VersionStatus status,
                                        boolean current, BigDecimal servingSizeG,
                                        String servingSizeDeclared, Boolean servingPlausible,
                                        BigDecimal servingsPerPack, String ingredientsRaw,
                                        String ingredientsLanguage, UUID labelImageId,
                                        CaptureMethod captureMethod, UUID capturedByUserId,
                                        DataQuality dataQuality) {
        ProductVersion version = new ProductVersion();
        version.id = UUID.randomUUID();
        version.productId = productId;
        version.versionNo = versionNo;
        version.status = status;
        version.current = current;
        version.servingSizeG = servingSizeG;
        version.servingSizeDeclared = servingSizeDeclared;
        version.servingPlausible = servingPlausible;
        version.servingsPerPack = servingsPerPack;
        version.ingredientsRaw = ingredientsRaw;
        version.ingredientsLanguage = ingredientsLanguage;
        version.labelImageId = labelImageId;
        version.captureMethod = captureMethod;
        version.capturedByUserId = capturedByUserId;
        version.verificationPass = 0;
        version.dataQuality = dataQuality;
        return version;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (effectiveFrom == null) {
            effectiveFrom = now;
        }
        if (capturedAt == null) {
            capturedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    /**
     * A human reviewer (ops review approval, a future correction pass) has checked this
     * version. Moves status to {@code VERIFIED} and records who/when — never set at
     * ingestion time for an unreviewed external import (see {@code ProductIngestionService}).
     */
    public void markVerified(UUID verifiedByUserId, Instant now) {
        this.status = VersionStatus.VERIFIED;
        this.verifiedAt = now;
        this.verifiedByUserId = verifiedByUserId;
        this.verificationPass = this.verificationPass + 1;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public boolean isCurrent() {
        return current;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public BigDecimal getServingSizeG() {
        return servingSizeG;
    }

    public String getServingSizeDeclared() {
        return servingSizeDeclared;
    }

    public Boolean getServingPlausible() {
        return servingPlausible;
    }

    public BigDecimal getServingsPerPack() {
        return servingsPerPack;
    }

    public String getIngredientsRaw() {
        return ingredientsRaw;
    }

    public String getIngredientsLanguage() {
        return ingredientsLanguage;
    }

    public UUID getLabelImageId() {
        return labelImageId;
    }

    public CaptureMethod getCaptureMethod() {
        return captureMethod;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public UUID getCapturedByUserId() {
        return capturedByUserId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedByUserId() {
        return verifiedByUserId;
    }

    public int getVerificationPass() {
        return verificationPass;
    }

    public DataQuality getDataQuality() {
        return dataQuality;
    }

    public Instant getFreshnessDueAt() {
        return freshnessDueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
