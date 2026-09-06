package com.familyfood.scoring.application;

import java.util.UUID;

/**
 * Aggregates {@link PersonalFitView}s across a household for a single caller, scoped to what
 * that caller is authorized to see (D-002, D-024 point 4).
 */
public interface HouseholdFitService {

    /**
     * @throws com.familyfood.platform.error.AppException with
     *         {@link com.familyfood.platform.error.ErrorCode#NOT_HOUSEHOLD_MEMBER} if
     *         {@code callerAccountId} is not a live member of {@code householdId}. This is the
     *         same fact and the same error code {@code HouseholdService} already uses for
     *         "caller isn't an active member of this household" — a caller who is a real
     *         member of a different household gets the identical 403 whether
     *         {@code householdId} exists or not, which is what makes this household isolation
     *         rather than a lookup failure with a different label.
     */
    HouseholdFitView evaluate(UUID callerAccountId, UUID householdId, ProductAssessmentInput input);
}
