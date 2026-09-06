package com.familyfood.recommendation.application;

import java.util.UUID;

/**
 * Alternative selection for a product, scoped to one household (D-013, FR-007, PX-2).
 *
 * <p>Deliberately mirrors {@code scoring.application.HouseholdFitService}'s own
 * authorization contract exactly, because alternatives are built from the same per-member
 * fit data: a caller must be a live member of {@code householdId}, and the result only ever
 * concerns members the caller is authorized to see (their own membership, plus any child
 * they guard — D-002).
 */
public interface AlternativesService {

    /**
     * @throws com.familyfood.platform.error.AppException with
     *         {@link com.familyfood.platform.error.ErrorCode#NOT_HOUSEHOLD_MEMBER} if
     *         {@code callerAccountId} is not a live member of {@code householdId}.
     */
    AlternativesResult findAlternatives(UUID callerAccountId, UUID householdId, UUID productId);
}
