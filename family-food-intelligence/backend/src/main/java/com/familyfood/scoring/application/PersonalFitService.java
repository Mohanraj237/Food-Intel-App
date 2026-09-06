package com.familyfood.scoring.application;

import java.util.UUID;

/**
 * Computes one household member's personal fit for a product: their goal-based verdict
 * plus a factual allergen-match check, both driven by that member's own recorded profile.
 *
 * <p>Personal fit is deliberately separate from {@code ProductHealthScore} (the product's
 * own, person-independent assessment) — see {@code system-architecture.md} §5.
 */
public interface PersonalFitService {

    /**
     * Never throws for an authorization/consent/no-profile outcome — those are
     * {@code status} values, not exceptions, because a household-fit caller (see the
     * Household Fit workstream) needs to skip one member without failing the whole
     * request. May throw {@code AppException} for a genuinely unexpected failure.
     */
    PersonalFitView evaluate(UUID callerAccountId, UUID memberId, ProductAssessmentInput input);
}
