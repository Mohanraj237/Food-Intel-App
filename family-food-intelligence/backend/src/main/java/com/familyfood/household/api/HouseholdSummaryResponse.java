package com.familyfood.household.api;

import com.familyfood.household.application.HouseholdService.HouseholdSummary;

import java.time.Instant;
import java.util.UUID;

/** One household the caller is currently an active member of. */
public record HouseholdSummaryResponse(UUID householdId, String householdName, UUID memberId,
                                       String role, String displayLabel, Instant joinedAt,
                                       boolean individual, String timeZone) {

    public static HouseholdSummaryResponse from(HouseholdSummary summary) {
        return new HouseholdSummaryResponse(
                summary.householdId(), summary.householdName(), summary.memberId(),
                summary.role(), summary.displayLabel(), summary.joinedAt(),
                summary.individual(), summary.timeZone());
    }
}
