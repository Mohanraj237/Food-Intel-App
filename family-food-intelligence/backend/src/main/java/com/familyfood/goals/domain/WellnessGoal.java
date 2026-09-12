package com.familyfood.goals.domain;

import com.familyfood.wellness.domain.MetricCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One target a member is working towards, expressed in the same closed metric taxonomy
 * observations use.
 *
 * <p>Sharing {@link MetricCode} with {@code wellness} is the whole design. It is what makes a
 * weight-loss goal for a child impossible under exactly the check that refuses a weight
 * <em>observation</em> for a child (D-036) — one age rule, one implementation, no second
 * safety net to keep in step. That check needs the subject's profile and therefore lives in
 * {@code GoalService}; this entity cannot enforce it and does not pretend to.
 *
 * <p>Changing a target ends this goal and starts a new one rather than mutating
 * {@link #targetValue}. Progress shown last month was progress against the target actually in
 * force then, and an edited target would rewrite that history into something nobody saw.
 *
 * <p>{@code BigDecimal}, not {@code double}: a 2000 ml target must round-trip exactly, and the
 * column is {@code numeric(12,3)}.
 */
@Entity
@Table(name = "wellness_goal")
public class WellnessGoal {

    @Id
    private UUID id;

    /** The subject. A household member, never an account — a child has no account (D-035). */
    @Column(name = "household_member_id", nullable = false)
    private UUID householdMemberId;

    /** Text in the column, a code-side enum here — see the V13 comment on {@code metric_code}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false)
    private MetricCode metricCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private GoalDirection direction;

    @Column(name = "target_value", nullable = false, precision = 12, scale = 3)
    private BigDecimal targetValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false)
    private GoalPeriod period;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WellnessGoal() {
        // JPA
    }

    public static WellnessGoal start(UUID householdMemberId,
                                     MetricCode metricCode,
                                     GoalDirection direction,
                                     BigDecimal targetValue,
                                     GoalPeriod period,
                                     LocalDate startedOn,
                                     UUID createdByUserId,
                                     Instant now) {
        WellnessGoal goal = new WellnessGoal();
        goal.id = UUID.randomUUID();
        goal.householdMemberId = householdMemberId;
        goal.metricCode = metricCode;
        goal.direction = direction;
        goal.targetValue = targetValue;
        goal.period = period;
        goal.startedOn = startedOn;
        goal.active = true;
        goal.createdByUserId = createdByUserId;
        goal.createdAt = now;
        return goal;
    }

    /**
     * Stops the goal. Idempotent: a second call keeps the original end date, so the record of
     * when the target actually stopped applying is not overwritten by a repeated request.
     *
     * <p>Clamped to {@link #startedOn} because {@code ck_goal_ended_after_started} would
     * otherwise turn a clock or timezone skew into a 500 on a perfectly ordinary request.
     */
    public void end(LocalDate on) {
        if (endedOn != null) {
            return;
        }
        endedOn = on.isBefore(startedOn) ? startedOn : on;
        active = false;
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

    public GoalDirection getDirection() {
        return direction;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public GoalPeriod getPeriod() {
        return period;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
