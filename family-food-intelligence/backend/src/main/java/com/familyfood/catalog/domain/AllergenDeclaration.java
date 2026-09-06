package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_allergen.declaration_type} CHECK constraint in
 * V4__catalog.sql. There is deliberately no {@code FREE_FROM} value: we report what a
 * label declares, and manufacturing cross-contamination is outside what a label can tell
 * us (AC-5.8).
 */
public enum AllergenDeclaration {
    CONTAINS, MAY_CONTAIN, NOT_DECLARED
}
