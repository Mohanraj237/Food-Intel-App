package com.familyfood.household.api;

import com.familyfood.household.domain.HouseholdMember;

import java.time.Instant;
import java.util.UUID;

/** {@code userAccountId} is null for a CHILD member — that is structural, not an omission. */
public record MemberResponse(UUID id, UUID householdId, UUID userAccountId, String memberType,
                             String role, String displayLabel, Instant joinedAt) {

    public static MemberResponse from(HouseholdMember member) {
        return new MemberResponse(
                member.getId(), member.getHouseholdId(), member.getUserAccountId(),
                member.getMemberType().name(), member.getRole().name(),
                member.getDisplayLabel(), member.getJoinedAt());
    }
}
