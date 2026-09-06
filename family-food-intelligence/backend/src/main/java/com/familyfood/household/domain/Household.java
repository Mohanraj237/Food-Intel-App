package com.familyfood.household.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
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
        return household;
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
