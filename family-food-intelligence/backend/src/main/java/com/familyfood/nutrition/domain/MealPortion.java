package com.familyfood.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One member's share of one meal (D-039).
 *
 * <p>A fraction rather than a weight, because that is how people describe eating together
 * ("he had about half of what I had") and because it stays correct when an item's quantity is
 * later corrected — the split does not need re-entering.
 *
 * <p>Fractions across a meal are deliberately not required to sum to 1: a family rarely eats
 * exactly what was cooked, and forcing the sum would make leftovers unrepresentable and push
 * people into entering numbers they know are wrong.
 */
@Entity
@Table(name = "meal_portion")
public class MealPortion {

    @Id
    private UUID id;

    @Column(name = "meal_id", nullable = false)
    private UUID mealId;

    /** The subject is a household member, never an account: a child has no account (D-035). */
    @Column(name = "household_member_id", nullable = false)
    private UUID householdMemberId;

    @Column(name = "portion_fraction", nullable = false, precision = 6, scale = 4)
    private BigDecimal portionFraction;

    /** When this share entered the member's own log; null while it awaits them (D-041). */
    @Column(name = "derived_at")
    private Instant derivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MealPortion() {
        // JPA
    }

    public static MealPortion apportion(UUID mealId,
                                        UUID householdMemberId,
                                        BigDecimal portionFraction,
                                        Instant now) {
        MealPortion portion = new MealPortion();
        portion.id = UUID.randomUUID();
        portion.mealId = mealId;
        portion.householdMemberId = householdMemberId;
        portion.portionFraction = portionFraction;
        portion.createdAt = now;
        return portion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMealId() {
        return mealId;
    }

    public UUID getHouseholdMemberId() {
        return householdMemberId;
    }

    public BigDecimal getPortionFraction() {
        return portionFraction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDerivedAt() {
        return derivedAt;
    }

    /** True once this share is in the member's own log (D-041). */
    public boolean isInMemberLog() {
        return derivedAt != null;
    }

    public void markDerived(Instant now) {
        this.derivedAt = now;
    }
}
