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
 * One catalogue image for a product. Separate from {@code label_image} on purpose — see
 * V11__product_image.sql's header comment: this is public, non-personal catalogue data with
 * no retention/deletion-obligation column, unlike the private, retention-bound label-capture
 * evidence table.
 *
 * <p>{@code primary} is the current hero image shown for the product. At most one row per
 * product may have {@code primary = true} — enforced by the partial unique index
 * {@code uq_product_image_primary}, the same pattern
 * {@code uq_product_current_version} uses for {@link ProductVersion}. Only
 * {@code catalog.application.ProductImageService} clears a prior primary row before
 * inserting a new one; nothing else writes this table.
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
    }

    /** Demoted when a newer image becomes the product's primary image. */
    public void clearPrimary() {
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
