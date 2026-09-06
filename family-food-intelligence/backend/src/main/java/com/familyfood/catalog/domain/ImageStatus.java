package com.familyfood.catalog.domain;

/** Mirrors the {@code product_image.status} CHECK constraint in V11__product_image.sql. */
public enum ImageStatus {
    UNVERIFIED, VERIFIED, REJECTED
}
