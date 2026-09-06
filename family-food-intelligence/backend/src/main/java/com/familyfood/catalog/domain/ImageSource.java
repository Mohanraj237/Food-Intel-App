package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_image.source} CHECK constraint in V11__product_image.sql.
 *
 * <p>Deliberately a subset of {@link CaptureMethod} — {@code LABEL_PHOTO} and
 * {@code MANUAL_ENTRY} are not here because a catalogue hero image is never sourced from a
 * private OCR label capture (that stays inside {@code label_image}'s own contract) and
 * "manual entry" is not a coherent image provenance on its own.
 */
public enum ImageSource {
    OFF_IMPORT, BRAND_PUBLISHED, USER_SUBMITTED, HUMAN_CROSSCHECK
}
