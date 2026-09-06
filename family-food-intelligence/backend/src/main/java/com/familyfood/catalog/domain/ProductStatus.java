package com.familyfood.catalog.domain;

/** Mirrors the {@code product.status} CHECK constraint in V4__catalog.sql. */
public enum ProductStatus {
    DISCOVERED, ACTIVE, REJECTED, MERGED
}
