package com.familyfood.catalog.domain;

/** Mirrors the {@code product_version.status} CHECK constraint in V4__catalog.sql. */
public enum VersionStatus {
    DISCOVERED, OCR_PARSED, NEEDS_VERIFICATION, VERIFIED, STALE, SUPERSEDED, REJECTED
}
