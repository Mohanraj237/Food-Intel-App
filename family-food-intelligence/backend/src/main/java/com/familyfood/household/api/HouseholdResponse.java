package com.familyfood.household.api;

import com.familyfood.household.domain.Household;

import java.time.Instant;
import java.util.UUID;

public record HouseholdResponse(UUID id, String name, UUID createdByUserId, Instant createdAt) {

    public static HouseholdResponse from(Household household) {
        return new HouseholdResponse(
                household.getId(), household.getName(),
                household.getCreatedByUserId(), household.getCreatedAt());
    }
}
