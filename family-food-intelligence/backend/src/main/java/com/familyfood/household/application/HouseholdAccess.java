package com.familyfood.household.application;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way another module may learn about household membership. Keeps profile/consent
 * decoupled from household's persistence layer — they call this interface, never a
 * household repository directly.
 */
public interface HouseholdAccess {

    Optional<MemberInfo> findMember(UUID memberId);

    /** True only if guardianAccountId currently (non-revoked) guards childMemberId. */
    boolean isGuardianOf(UUID guardianAccountId, UUID childMemberId);

    /**
     * Every live member of a household, in no particular order. Added for `scoring` (D-024)
     * to enumerate who a household-fit result could apply to — the caller is still
     * responsible for deciding which of these members it is authorized to show anything
     * about (D-002: an adult's own data is self-only, not household-owner-visible).
     */
    List<MemberInfo> listActiveMembers(UUID householdId);

    record MemberInfo(UUID id, UUID householdId, UUID userAccountId, String memberType, boolean active) {}

    /**
     * Every child a guardian actively guards, across every household — added for account
     * data export (AC-9.2), which needs {@code displayLabel} and {@code joinedAt} that
     * {@link MemberInfo} deliberately does not carry. A new record rather than widening
     * {@code MemberInfo}: that record is constructed positionally by every existing test
     * double across four modules, and widening it would ripple into all of them for a field
     * only this one caller needs.
     */
    List<GuardedChildInfo> listGuardedChildren(UUID guardianAccountId);

    record GuardedChildInfo(UUID id, UUID householdId, String displayLabel, Instant joinedAt) {}

    /* ------------------------------------------------------------------ W1 additions */

    /**
     * The caller's own live membership in a given household, if any. The access policy (D-044)
     * needs exactly this — "which member is this account, in the subject's household?" — to
     * look up grants, which name a member rather than an account. One query, where filtering
     * {@link #listActiveMembers} would load the whole household to find one row.
     */
    Optional<MemberInfo> findActiveMembership(UUID householdId, UUID accountId);

    /** Every live membership an account holds, oldest first (the frontend's default order). */
    List<MemberInfo> findActiveMembershipsOf(UUID accountId);

    /** Household-level facts other modules need: its mode and where its days start. */
    Optional<HouseholdInfo> findHousehold(UUID householdId);

    /**
     * @param individual D-043: created through "Just me" and not yet joined by anyone else
     * @param zone       D-053: the zone whose midnight bounds "today" for these members
     */
    record HouseholdInfo(UUID id, String name, boolean individual, ZoneId zone) {}

    /**
     * Live members with the labels a person-facing view needs (the family overview, the access
     * log, "shared with me"). Separate from {@link MemberInfo} for the same reason as
     * {@link GuardedChildInfo}. The label is what members already see in the household list,
     * so returning it discloses nothing new.
     */
    List<MemberSummary> listActiveMemberSummaries(UUID householdId);

    Optional<MemberSummary> findMemberSummary(UUID memberId);

    record MemberSummary(UUID id, UUID householdId, UUID userAccountId, String memberType,
                         String role, String displayLabel, Instant joinedAt, boolean active) {}
}
