package com.familyfood.search.application;

/**
 * Server-side result orderings (D-061). Every nutrient sort is per 100 g / 100 ml and puts
 * products that do not declare the nutrient last — never first, never as if they were zero.
 * {@code SCORE_DESC} orders by the ACTIVE rule set's persisted score; while that set has no
 * content (D-024) every score is absent and the order falls back to relevance, then name.
 */
public enum SearchSort {
    RELEVANCE,
    SCORE_DESC,
    SUGAR_ASC,
    ADDED_SUGAR_ASC,
    SODIUM_ASC,
    SAT_FAT_ASC,
    PROTEIN_DESC,
    FIBRE_DESC,
    QUALITY;

    public static SearchSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw com.familyfood.platform.error.AppException.validation("sort", "unknown sort " + raw);
        }
    }
}
