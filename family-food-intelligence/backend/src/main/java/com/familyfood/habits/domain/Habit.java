package com.familyfood.habits.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Something a member intends to do regularly, and nothing more.
 *
 * <p><b>There is no streak, badge, point, level, rank or score field on this entity, and there
 * will not be one.</b> That is a permanent product boundary rather than a backlog item (D-036,
 * CH-004): the table in V13 carries no such column, this class holds no such field, and
 * consistency is computed at read time from the completion log. A stored counter is a score,
 * and a score aimed at a child is the thing this product refuses to build. The absence is the
 * feature.
 *
 * <p>Archiving sets {@link #archivedAt} and clears {@link #active} rather than deleting the row,
 * so a member who stops doing something keeps the record of having done it. A deleted habit
 * would silently take its completion history with it.
 */
@Entity
@Table(name = "habit")
public class Habit {

    @Id
    private UUID id;

    /** The subject. A household member, never an account — a child has no account (D-035). */
    @Column(name = "household_member_id", nullable = false)
    private UUID householdMemberId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", nullable = false)
    private HabitCadence cadence;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Habit() {
        // JPA
    }

    public static Habit create(UUID householdMemberId,
                               String name,
                               HabitCadence cadence,
                               UUID createdByUserId,
                               Instant now) {
        Habit habit = new Habit();
        habit.id = UUID.randomUUID();
        habit.householdMemberId = householdMemberId;
        habit.name = name;
        habit.cadence = cadence;
        habit.active = true;
        habit.createdByUserId = createdByUserId;
        habit.createdAt = now;
        return habit;
    }

    /**
     * Renames or re-paces a habit in place.
     *
     * <p>Unlike a goal's target, this is a genuine edit: "drink water" becoming "drink water
     * with lunch" is the same habit described better, and ending it would break the log in half
     * for a wording change. A cadence change does alter what {@code consistency} expects for
     * past days, which is the honest cost of not treating every typo as a new habit.
     */
    public void update(String name, HabitCadence cadence) {
        this.name = name;
        this.cadence = cadence;
    }

    /** Idempotent: a repeated archive keeps the original date, so when tracking actually
     *  stopped is not overwritten by a duplicate request. */
    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
        }
        active = false;
    }

    /** Clears the archive entirely rather than keeping the old date — {@code habit} has one
     *  archive column, and a live habit with an archive date would fail
     *  {@code ck_habit_inactive_is_archived}'s sibling reading and confuse every listing. */
    public void reactivate() {
        archivedAt = null;
        active = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdMemberId() {
        return householdMemberId;
    }

    public String getName() {
        return name;
    }

    public HabitCadence getCadence() {
        return cadence;
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

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
