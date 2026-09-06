package com.familyfood.catalog.domain;

/** Mirrors the {@code product_version.data_quality} CHECK constraint in V4__catalog.sql. */
public enum DataQuality {
    VERIFIED, RECENT, STALE, CONFLICTING, NEEDS_REVIEW, UNVERIFIED
}
