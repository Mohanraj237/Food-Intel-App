package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;

/**
 * The product-level score a result card may show small and secondary (D-003): the ACTIVE
 * rule set's persisted result, filled in by {@code search} from {@code product_search_facts}.
 * Catalogue reads leave it null — the catalogue itself knows nothing about scores.
 * {@code status} is {@code SCORED} or {@code INSUFFICIENT_DATA}; a product never scored has
 * no badge at all.
 */
public record AssessmentBadgeDto(String status, Integer score, BigDecimal stars, String grade) {
}
