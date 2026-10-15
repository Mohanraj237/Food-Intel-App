package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_image.purpose} CHECK constraint (V15__catalog_enrichment.sql):
 * which view of the pack a catalogue photo shows. At most one live original per (product,
 * purpose) — {@code uq_product_image_purpose}.
 */
public enum ImagePurpose {
    FRONT, BACK, PACKAGING, NUTRITION, INGREDIENTS, OTHER
}
