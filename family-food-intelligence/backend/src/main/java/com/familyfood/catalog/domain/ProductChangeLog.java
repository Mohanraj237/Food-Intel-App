package com.familyfood.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One recorded change to a product's facts (V4 {@code product_change_log}): which fields,
 * from which version to which, why, and whether it was material (a nutrient value or the
 * ingredient list changed — the "formulation updated" banner) or not (an enrichment fill of a
 * previously-absent fact, {@code from_version_id = to_version_id}).
 */
@Entity
@Table(name = "product_change_log")
public class ProductChangeLog {

    public static final String REASON_OFF_ENRICHMENT = "OFF_ENRICHMENT";

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "from_version_id")
    private UUID fromVersionId;

    @Column(name = "to_version_id")
    private UUID toVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> changedFields;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "materially_changed", nullable = false)
    private boolean materiallyChanged;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "detected_by_user_id")
    private UUID detectedByUserId;

    protected ProductChangeLog() {
        // JPA
    }

    public static ProductChangeLog record(UUID productId, UUID fromVersionId, UUID toVersionId,
                                          Map<String, Object> changedFields, String changeReason,
                                          boolean materiallyChanged, UUID detectedByUserId, Instant now) {
        ProductChangeLog log = new ProductChangeLog();
        log.id = UUID.randomUUID();
        log.productId = productId;
        log.fromVersionId = fromVersionId;
        log.toVersionId = toVersionId;
        log.changedFields = changedFields;
        log.changeReason = changeReason;
        log.materiallyChanged = materiallyChanged;
        log.detectedAt = now;
        log.detectedByUserId = detectedByUserId;
        return log;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getFromVersionId() {
        return fromVersionId;
    }

    public UUID getToVersionId() {
        return toVersionId;
    }

    public Map<String, Object> getChangedFields() {
        return changedFields;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public boolean isMateriallyChanged() {
        return materiallyChanged;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public UUID getDetectedByUserId() {
        return detectedByUserId;
    }
}
