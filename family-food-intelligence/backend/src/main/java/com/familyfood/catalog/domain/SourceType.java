package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_source.source_type} CHECK constraint in V4__catalog.sql.
 * The permitted source list is a decision (D-016), not a convention: automated
 * e-commerce scraping has no representation here.
 */
public enum SourceType {
    LABEL_PHOTO, BRAND_PUBLISHED, OPEN_FOOD_FACTS, FSSAI, ICMR_NIN, USER_SUBMITTED,
    HUMAN_CROSSCHECK, MANUAL_ENTRY
}
