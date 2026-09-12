package com.familyfood.wellness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One typed measurement about one household member — the single write path every tracker
 * in Phase 2 goes through (D-034).
 *
 * <p>Append-only and immutable, like {@code ActivityEvent}: an observation is a historical
 * fact about a reading, not a row anyone edits. A corrected weight is a new observation, so
 * that a trend can never be silently rewritten after an insight has been shown about it.
 * There is no setter and no update method.
 *
 * <p>{@code BigDecimal}, not {@code double}: 72.35 kg must round-trip exactly, and a float
 * sum over a day of water logging drifts by enough to be visible in the total.
 */
@Entity
@Table(name = "health_observation")
public class HealthObservation {

    @Id
    private UUID id;

    /** The subject. Always a household member, never an account (D-035) — a child has no
     *  account, so account-scoping would make a child's permitted metrics unrepresentable. */
    @Column(name = "household_member_id", nullable = false)
    private UUID householdMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false)
    private MetricCode metricCode;

    /** Denormalised from {@link MetricCode#category()} so that a visibility filter and a
     *  category aggregate are index-only reads, without joining a lookup table or loading
     *  every row to ask the enum. */
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_category", nullable = false)
    private MetricCategory metricCategory;

    @Column(name = "value", nullable = false, precision = 12, scale = 3)
    private BigDecimal value;

    @Column(name = "unit", nullable = false)
    private String unit;

    /** When the measurement applies to — not when it was typed in. Trends must use this. */
    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Who entered it. A guardian for a child; the subject themselves for an adult (D-002). */
    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ObservationSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For a DERIVED observation, the id of the thing that produced it (today: a meal).
     *  Opaque here — the producing module owns the meaning. Lets that module retract and
     *  restate its own rows precisely instead of mutating them. */
    @Column(name = "source_ref")
    private UUID sourceRef;

    protected HealthObservation() {
        // JPA
    }

    /**
     * Records an observation in the metric's canonical unit.
     *
     * <p>The unit is taken from {@link MetricCode#canonicalUnit()} rather than accepted from
     * the caller: conversion belongs at the edge, and a mixed-unit history makes every trend
     * and every sum wrong in a way that stays invisible until someone switches units
     * mid-history.
     *
     * <p>This constructor does <b>not</b> check the age gate. That check needs the subject's
     * current profile, which is a different module's data — it lives in
     * {@code WellnessService}, which is the only caller.
     */
    public static HealthObservation record(UUID householdMemberId,
                                           MetricCode metricCode,
                                           BigDecimal value,
                                           Instant effectiveAt,
                                           UUID recordedByUserId,
                                           ObservationSource source,
                                           Instant now) {
        return record(householdMemberId, metricCode, value, effectiveAt, recordedByUserId,
                source, now, null);
    }

    /** As above, tagged with the entity that derived it. */
    public static HealthObservation record(UUID householdMemberId,
                                           MetricCode metricCode,
                                           BigDecimal value,
                                           Instant effectiveAt,
                                           UUID recordedByUserId,
                                           ObservationSource source,
                                           Instant now,
                                           UUID sourceRef) {
        HealthObservation observation = new HealthObservation();
        observation.id = UUID.randomUUID();
        observation.householdMemberId = householdMemberId;
        observation.metricCode = metricCode;
        observation.metricCategory = metricCode.category();
        observation.value = value;
        observation.unit = metricCode.canonicalUnit();
        observation.effectiveAt = effectiveAt;
        observation.recordedAt = now;
        observation.recordedByUserId = recordedByUserId;
        observation.source = source;
        observation.createdAt = now;
        observation.sourceRef = sourceRef;
        return observation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdMemberId() {
        return householdMemberId;
    }

    public MetricCode getMetricCode() {
        return metricCode;
    }

    public MetricCategory getMetricCategory() {
        return metricCategory;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public UUID getRecordedByUserId() {
        return recordedByUserId;
    }

    public ObservationSource getSource() {
        return source;
    }

    public UUID getSourceRef() {
        return sourceRef;
    }
}
