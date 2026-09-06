package com.familyfood.household.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One person's standing in one household — an account holder ({@link MemberRole#OWNER} or
 * {@link MemberRole#ADULT}) or a child ({@link MemberRole#DEPENDENT}).
 *
 * <p>Two invariants mirror the database CHECK constraints {@code ck_member_child_has_no_account}
 * and {@code ck_member_child_is_dependent} in code, kept in both places on purpose: the
 * database constraint is the guarantee, this is the early, legible failure.
 *
 * <ul>
 *   <li>a CHILD member never has a {@code userAccountId} — there is no credential, session
 *       or notification channel that could reach a child (D-001);
 *   <li>an account holder can never be {@code DEPENDENT} — the guardian-controlled pathway
 *       cannot be pointed at an adult to bypass D-002.
 * </ul>
 *
 * <p>{@code displayLabel} is a household-chosen label, never a legal name. There is no name
 * field for a child anywhere in this class or the schema it maps to.
 */
@Entity
@Table(name = "household_member")
public class HouseholdMember {

    /** The scrubbed {@code displayLabel} left behind by {@link #scrubForErasure}. */
    public static final String ERASED_DISPLAY_LABEL = "Erased member";

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "user_account_id")
    private UUID userAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false)
    private MemberType memberType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MemberRole role;

    @Column(name = "display_label", nullable = false)
    private String displayLabel;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HouseholdMember() {
        // JPA
    }

    /** The founding member of a new household. Always account-holding, always OWNER. */
    public static HouseholdMember owner(UUID householdId, UUID userAccountId,
                                        String displayLabel, Instant now) {
        return accountHolder(householdId, userAccountId, MemberRole.OWNER, displayLabel, now);
    }

    /** An adult who accepted an invitation. Always account-holding, always ADULT. */
    public static HouseholdMember adult(UUID householdId, UUID userAccountId,
                                        String displayLabel, Instant now) {
        return accountHolder(householdId, userAccountId, MemberRole.ADULT, displayLabel, now);
    }

    private static HouseholdMember accountHolder(UUID householdId, UUID userAccountId,
                                                  MemberRole role, String displayLabel,
                                                  Instant now) {
        if (userAccountId == null) {
            throw new IllegalArgumentException("an account-holding member requires a userAccountId");
        }
        HouseholdMember member = base(householdId, MemberType.ADULT, role, displayLabel, now);
        member.userAccountId = userAccountId;
        return member;
    }

    /**
     * A child member. Never has a {@code userAccountId}, always {@link MemberRole#DEPENDENT}
     * (mirrors {@code ck_member_child_has_no_account} / {@code ck_member_child_is_dependent}).
     */
    public static HouseholdMember child(UUID householdId, String displayLabel, Instant now) {
        return base(householdId, MemberType.CHILD, MemberRole.DEPENDENT, displayLabel, now);
    }

    private static HouseholdMember base(UUID householdId, MemberType memberType, MemberRole role,
                                        String displayLabel, Instant now) {
        String trimmed = displayLabel == null ? "" : displayLabel.trim();
        if (trimmed.isEmpty() || trimmed.length() > 60) {
            throw new IllegalArgumentException("displayLabel must be between 1 and 60 characters");
        }
        HouseholdMember member = new HouseholdMember();
        member.id = UUID.randomUUID();
        member.householdId = householdId;
        member.memberType = memberType;
        member.role = role;
        member.displayLabel = trimmed;
        member.joinedAt = now;
        member.createdAt = now;
        return member;
    }

    /** Marks this membership as ended. Idempotent: leaving twice is a no-op. */
    public void leave(Instant now) {
        if (leftAt == null) {
            this.leftAt = now;
        }
    }

    /**
     * Erasure's treatment of a membership row (AC-9.3): ends it like {@link #leave} and
     * overwrites {@code displayLabel} with a fixed, non-identifying placeholder. The row
     * itself is never hard-deleted — {@code guardian_link} and {@code consent_record} rows
     * may still reference it with no {@code ON DELETE} action (see V3's schema comments),
     * so the row must keep existing; only the personal content it carries is removed.
     * Idempotent, like {@link #leave}.
     */
    public void scrubForErasure(Instant now) {
        leave(now);
        this.displayLabel = ERASED_DISPLAY_LABEL;
    }

    /** True while this is a live membership (has not left the household). */
    public boolean isActive() {
        return leftAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public MemberType getMemberType() {
        return memberType;
    }

    public MemberRole getRole() {
        return role;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
