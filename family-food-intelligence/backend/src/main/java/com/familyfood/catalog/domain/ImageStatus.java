package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_image.status} CHECK constraint (V11, extended in V15).
 * {@code SUPERSEDED}: a newer live original of the same purpose replaced this one — it was
 * not wrong, so it is not {@code REJECTED}, and nothing is deleted.
 */
public enum ImageStatus {
    UNVERIFIED, VERIFIED, REJECTED, SUPERSEDED
}
