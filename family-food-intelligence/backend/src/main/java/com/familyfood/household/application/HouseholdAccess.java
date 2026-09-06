package com.familyfood.household.application;

import java.time.Instant;
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
}
