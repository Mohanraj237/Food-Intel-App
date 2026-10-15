package com.familyfood.household.api;

import com.familyfood.household.domain.Household;

import java.time.Instant;
import java.util.UUID;

/**
 * @param individual D-043: a household of one created through "Just me"; clients suppress
 *                   household framing while it is true
 * @param timeZone   D-053: the IANA zone whose midnight bounds "today" for its members
 */
public record HouseholdResponse(UUID id, String name, UUID createdByUserId, Instant createdAt,
                                boolean individual, String timeZone) {

    public static HouseholdResponse from(Household household) {
        return new HouseholdResponse(
                household.getId(), household.getName(),
                household.getCreatedByUserId(), household.getCreatedAt(),
                household.isIndividual(), household.getTimeZone());
    }
}
