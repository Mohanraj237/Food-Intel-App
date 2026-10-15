package com.familyfood.household.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * A household: the unit that owns memberships, invitations and (through
 * {@code household_member}) the people whose consent and profile records hang off it.
 *
 * <p>A household of exactly one member (the {@link MemberRole#OWNER}) is a fully valid,
 * terminal state (D-002). Nothing here or in {@code HouseholdService} treats an
 * unaccompanied owner as incomplete.
 */
@Entity
@Table(name = "household")
public class Household {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * D-035/D-043: created for one person through "Just me". Presentation only — the data path
     * is identical to any other household — and it becomes false for good the moment a second
     * member joins, so "I started alone, then added my family" moves no data.
     */
    @Column(name = "is_individual", nullable = false)
    private boolean individual;

    /** D-053: IANA zone whose midnight bounds "today" for this household's members. */
    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    /** India-first (D-019): the zone a household gets unless someone sets another. */
    public static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

    protected Household() {
        // JPA
    }

    /**
     * @param name mirrors {@code CHECK (length(btrim(name)) BETWEEN 1 AND 80)} — enforced
     *             here as well as by the database (defence in depth).
     */
    public static Household create(String name, UUID createdByUserId, Instant now) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("household name must be between 1 and 80 characters");
        }
        Household household = new Household();
        household.id = UUID.randomUUID();
        household.name = trimmed;
        household.createdByUserId = createdByUserId;
        household.createdAt = now;
        household.updatedAt = now;
        household.individual = false;
        household.timeZone = DEFAULT_TIME_ZONE;
        return household;
    }

    /** A household of one, created through "Just me" (D-043). */
    public static Household createIndividual(String name, UUID createdByUserId, Instant now) {
        Household household = create(name, createdByUserId, now);
        household.individual = true;
        return household;
    }

    /**
     * A second member has joined. One-way: a household that has had a family in it does not
     * become "individual" again when someone leaves, because that framing would hide the
     * members who are still there and the history they share.
     *
     * @return true if this call changed the household, so the caller can audit the change once
     */
    public boolean becomeFamily(Instant now) {
        if (!individual) {
            return false;
        }
        individual = false;
        updatedAt = now;
        return true;
    }

    /**
     * Where this household's days start and end. Stored as text for readability in the
     * database; resolved here so an invalid id can never reach a date calculation.
     */
    public ZoneId zone() {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
    }

    public boolean isIndividual() {
        return individual;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
