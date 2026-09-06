package com.familyfood.activity.domain;

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
 * One activity event: a signed-in account's own lookup, comparison or outcome decision for
 * a product (FR-012, D-005). Scoped to {@code accountId} only, never a household — this is
 * self-only data (D-002's pattern applied here), and {@code ActivityService} never accepts
 * or returns another account's events.
 *
 * <p>Deliberately immutable and append-only: an activity event is a historical fact about
 * what happened, not a record anyone edits. There is no update method.
 */
@Entity
@Table(name = "activity_event")
public class ActivityEvent {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "alternative_product_id")
    private UUID alternativeProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private ActivityEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ActivityEvent() {
        // JPA
    }

    public static ActivityEvent record(UUID accountId, UUID productId, UUID alternativeProductId,
                                        ActivityEventType eventType, Instant now) {
        ActivityEvent event = new ActivityEvent();
        event.id = UUID.randomUUID();
        event.accountId = accountId;
        event.productId = productId;
        event.alternativeProductId = alternativeProductId;
        event.eventType = eventType;
        event.occurredAt = now;
        return event;
    }

    @PrePersist
    void onPersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getAlternativeProductId() {
        return alternativeProductId;
    }

    public ActivityEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
