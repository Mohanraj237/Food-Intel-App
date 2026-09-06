package com.familyfood.scoring.api;

import com.familyfood.scoring.application.HouseholdFitView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@link HouseholdFitView}. {@code members} contains only the members the caller is
 * authorized to see (their own membership, plus any child they guard) — never every member of
 * the household (D-002, D-024 point 4). A member the caller cannot see is simply absent, not
 * represented with a placeholder.
 */
public record HouseholdFitResponse(
        UUID householdId,
        UUID productVersionId,
        List<PersonalFitResponse> members,
        Instant computedAt) {

    static HouseholdFitResponse from(HouseholdFitView view) {
        return new HouseholdFitResponse(
                view.householdId(),
                view.productVersionId(),
                view.members().stream().map(PersonalFitResponse::from).toList(),
                view.computedAt());
    }
}
