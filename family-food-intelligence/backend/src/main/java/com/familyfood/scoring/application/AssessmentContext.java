package com.familyfood.scoring.application;

/**
 * What {@link AssessmentOrchestrator} hands back: the product-level score and the input,
 * now carrying the resolved food type and the engine-derived facts ({@code INR_STARS},
 * {@code NOVA_GROUP}) that verdict rules may reference.
 */
public record AssessmentContext(ProductHealthView health, ProductAssessmentInput input) {
}
