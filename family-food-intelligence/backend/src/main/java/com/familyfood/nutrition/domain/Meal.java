package com.familyfood.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One eating occasion for a household (D-039) — a composition event, not a per-member diary
 * entry. The same dinner eaten by four people is one of these with four {@link MealPortion}
 * rows, which is what makes a correction to the recipe a single edit rather than four.
 *
 * <p>Scoped to a household rather than a member even in Individual Mode, where it is simply a
 * household of one (D-035). A second "just me" code path would be a second place for the
 * apportioning rules to drift.
 *
 * <p>Unlike {@code HealthObservation}, a meal is editable: it is a record of what someone
 * says they ate, and "I actually had half of that" is a correction to the same event rather
 * than a new one. The observations it derives stay append-only — editing a meal retracts
 * them and states fresh ones (see {@code MealService}).
 */
@Entity
@Table(name = "meal")
public class Meal {

    /** Matches the {@code meal.name} CHECK: a trimmed length of 1..120, or absent. */
    private static final int MAX_NAME_LENGTH = 120;

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    @Column(name = "name")
    private String name;

    /** When it was eaten — not when it was typed in. Every window and total uses this. */
    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Meal() {
        // JPA
    }

    public static Meal log(UUID householdId,
                           MealType mealType,
                           String name,
                           Instant consumedAt,
                           UUID createdByUserId,
                           Instant now) {
        Meal meal = new Meal();
        meal.id = UUID.randomUUID();
        meal.householdId = householdId;
        meal.mealType = mealType;
        meal.name = normaliseName(name);
        meal.consumedAt = consumedAt;
        meal.createdByUserId = createdByUserId;
        meal.createdAt = now;
        meal.updatedAt = now;
        return meal;
    }

    /**
     * Corrects the occasion itself. Named for the intent rather than exposed as setters, so
     * that the only way to change a meal is the one the service's recompute path goes
     * through — a field quietly set elsewhere would leave the derived observations stale.
     *
     * <p>{@code householdId} and {@code createdByUserId} are deliberately not changeable: a
     * meal that moved household would silently strand its portions, and rewriting who logged
     * it would falsify the record.
     */
    public void revise(MealType mealType, String name, Instant consumedAt, Instant now) {
        this.mealType = mealType;
        this.name = normaliseName(name);
        this.consumedAt = consumedAt;
        this.updatedAt = now;
    }

    /** Blank is not a name. An empty string would trip the column CHECK; absent is the truth. */
    private static String normaliseName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.strip();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public MealType getMealType() {
        return mealType;
    }

    public String getName() {
        return name;
    }

    public Instant getConsumedAt() {
        return consumedAt;
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
}
