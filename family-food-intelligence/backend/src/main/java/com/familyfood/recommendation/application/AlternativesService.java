package com.familyfood.recommendation.application;

import java.util.UUID;

/**
 * Alternative selection for a product (D-013, D-060, FR-007, PX-2).
 *
 * <p>{@link #findAlternatives} is scoped to one household and mirrors
 * {@code scoring.application.HouseholdFitService}'s authorization contract exactly: a caller
 * must be a live member of {@code householdId}, and member reasons only ever concern members
 * the caller is authorized to see (their own membership, plus any child they guard — D-002).
 * {@link #findProductAlternatives} is anonymous and product-level only: rating, nutrient
 * bands and processing level, nothing about any person.
 */
public interface AlternativesService {

    /**
     * @throws com.familyfood.platform.error.AppException with
     *         {@link com.familyfood.platform.error.ErrorCode#NOT_HOUSEHOLD_MEMBER} if
     *         {@code callerAccountId} is not a live member of {@code householdId}.
     */
    AlternativesResult findAlternatives(UUID callerAccountId, UUID householdId, UUID productId);

    /** Product-level alternatives for anyone (D-020): no allergen or verdict reasons. */
    AlternativesResult findProductAlternatives(UUID productId);
}
