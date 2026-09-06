package com.familyfood.scoring.application;

/**
 * Computes the product-level {@code ProductHealthScore} (D-003) — deterministic, versioned,
 * category-aware, and entirely independent of any one person's profile (that is
 * {@code PersonalFitService}'s concern).
 */
public interface ProductHealthScoreService {

    ProductHealthView score(ProductAssessmentInput input);
}
