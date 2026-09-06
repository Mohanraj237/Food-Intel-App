package com.familyfood.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "correlation_id")
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "household_id")
    private UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutcome outcome;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    protected AuditEvent() {
        // JPA
    }

    static AuditEvent of(Instant occurredAt, UUID actorUserId, String actorRole,
                         String correlationId, AuditAction action, String resourceType,
                         UUID resourceId, UUID householdId, AuditOutcome outcome,
                         Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.occurredAt = occurredAt;
        event.actorUserId = actorUserId;
        event.actorRole = actorRole;
        event.correlationId = correlationId;
        event.action = action;
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.householdId = householdId;
        event.outcome = outcome;
        event.metadata = metadata == null ? Map.of() : metadata;
        return event;
    }

    public UUID getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public enum AuditOutcome {
        SUCCESS, DENIED, ERROR
    }
}
