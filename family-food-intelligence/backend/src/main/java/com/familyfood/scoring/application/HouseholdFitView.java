package com.familyfood.scoring.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The result of aggregating {@link PersonalFitView}s across a household, scoped to what the
 * calling account is authorized to see.
 *
 * <p>{@code members} is never "everyone in the household" — it is the caller's own live
 * membership plus any {@code CHILD} member the caller currently guards. Another adult's
 * membership is silently absent, never surfaced with a denial marker, exactly mirroring how
 * the frontend {@code ConsentPanel} already treats another adult's non-essential consent
 * purposes (D-002, D-024 point 4).
 */
public record HouseholdFitView(
        UUID householdId,
        UUID productVersionId,
        List<PersonalFitView> members,
        Instant computedAt) {
}
