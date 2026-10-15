package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_version.diet_flag} CHECK constraint (V15__catalog_enrichment.sql).
 * {@code UNKNOWN} is the default and the honest state for a product whose label mark was not
 * captured; an estimated value (from an ingredient-list analysis) is still only an estimate —
 * see {@code ProductVersion.isDietFlagEstimated()}.
 */
public enum DietFlag {
    VEG, NON_VEG, VEGAN, UNKNOWN
}
