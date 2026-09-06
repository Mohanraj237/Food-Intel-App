package com.familyfood.catalog.domain;

/** Mirrors the {@code product_version.capture_method} CHECK constraint in V4__catalog.sql. */
public enum CaptureMethod {
    LABEL_PHOTO, BRAND_PUBLISHED, OFF_IMPORT, USER_SUBMITTED, HUMAN_CROSSCHECK, MANUAL_ENTRY
}
