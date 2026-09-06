package com.familyfood.catalog.domain;

/** Mirrors the {@code product_barcode.gtin_type} CHECK constraint in V4__catalog.sql. */
public enum GtinType {
    EAN13, EAN8, UPCA, ITF14
}
