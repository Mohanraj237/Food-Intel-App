package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One catalogue image for a product. Separate from {@code label_image} on purpose — see
 * V11__product_image.sql's header comment: this is public, non-personal catalogue data with
 * no retention/deletion-obligation column, unlike the private, retention-bound label-capture
 * evidence table.
 *
 * <p>{@code primary} is the current hero image shown for the product. At most one row per
 * product may have {@code primary = true} — enforced by the partial unique index
 * {@code uq_product_image_primary}. {@code purpose} says which view of the pack this is;
 * at most one live original per (product, purpose) — {@code uq_product_image_purpose} (V15).
 * A cleaned derivative points at its parent through {@code derivedFromImageId} and sits
 * outside that rule. Only {@code catalog.application.ProductImageService} writes this table.
 */
@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImagePurpose purpose;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "derived_from_image_id")
    private UUID derivedFromImageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> transform;

    @Column(name = "quality_score")
    private BigDecimal qualityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> classification;

    @Column(name = "license_note")
    private String licenseNote;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductImage() {
        // JPA
    }

    public static ProductImage create(UUID productId, String storageKey, ImageSource source,
                                      String contentHash, String mimeType, long byteSize,
                                      Integer width, Integer height, boolean primary) {
        return create(productId, storageKey, source, contentHash, mimeType, byteSize, width, height,
                primary, ImagePurpose.FRONT, 0, null, null, null, null, null);
    }

    public static ProductImage create(UUID productId, String storageKey, ImageSource source,
                                      String contentHash, String mimeType, long byteSize,
                                      Integer width, Integer height, boolean primary,
                                      ImagePurpose purpose, int sortOrder, UUID derivedFromImageId,
                                      Map<String, Object> transform, BigDecimal qualityScore,
                                      Map<String, Object> classification, String licenseNote) {
        ProductImage image = new ProductImage();
        image.id = UUID.randomUUID();
        image.productId = productId;
        image.storageKey = storageKey;
        image.source = source;
        image.status = ImageStatus.UNVERIFIED;
        image.contentHash = contentHash;
        image.mimeType = mimeType;
        image.byteSize = byteSize;
        image.width = width;
        image.height = height;
        image.primary = primary;
        image.purpose = purpose == null ? ImagePurpose.FRONT : purpose;
        image.sortOrder = sortOrder;
        image.derivedFromImageId = derivedFromImageId;
        image.transform = transform;
        image.qualityScore = qualityScore;
        image.classification = classification;
        image.licenseNote = licenseNote;
        return image;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (capturedAt == null) {
            capturedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (purpose == null) {
            purpose = ImagePurpose.FRONT;
        }
    }

    /** Demoted when a newer image becomes the product's primary image. */
    public void clearPrimary() {
        this.primary = false;
    }

    /** A newer live original of the same purpose replaced this one. Never deleted. */
    public void markSuperseded() {
        this.status = ImageStatus.SUPERSEDED;
        this.primary = false;
    }

    public void markVerified(UUID verifiedByUserId, Instant now) {
        this.status = ImageStatus.VERIFIED;
        this.verifiedAt = now;
        this.verifiedByUserId = verifiedByUserId;
    }

    public void markRejected() {
        this.status = ImageStatus.REJECTED;
        this.primary = false;
    }

    public boolean isLiveOriginal() {
        return derivedFromImageId == null
                && (status == ImageStatus.UNVERIFIED || status == ImageStatus.VERIFIED);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public ImageSource getSource() {
        return source;
    }

    public ImageStatus getStatus() {
        return status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public boolean isPrimary() {
        return primary;
    }

    public ImagePurpose getPurpose() {
        return purpose;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public UUID getDerivedFromImageId() {
        return derivedFromImageId;
    }

    public Map<String, Object> getTransform() {
        return transform;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public Map<String, Object> getClassification() {
        return classification;
    }

    public String getLicenseNote() {
        return licenseNote;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedByUserId() {
        return verifiedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
