package com.familyfood.scoring.application;

/**
 * Computes the product-level {@code ProductHealthScore} (D-003) — deterministic, versioned,
 * category-aware, and entirely independent of any one person's profile (that is
 * {@code PersonalFitService}'s concern). Pure computation: nothing here reads or writes
 * {@code product_score}; {@link ProductScoreService} does that.
 */
public interface ProductHealthScoreService {

    /** Against the ACTIVE rule set. */
    ProductHealthView score(ProductAssessmentInput input);

    /**
     * Against a named rule set of any status — for the rescore job and, later, the scoring
     * console's test/diff of a DRAFT set against the ACTIVE one. Never activates anything.
     */
    ProductHealthView score(ProductAssessmentInput input, String ruleSetVersion);
}
