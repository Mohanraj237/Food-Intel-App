package com.familyfood.catalog.domain;

/**
 * Mirrors the {@code product_nutrient.basis_per_100} and {@code declared_basis} CHECK
 * constraints in V4__catalog.sql. {@code basis_per_100} only ever takes the first two
 * values; {@code declared_basis} may additionally be {@code PER_SERVING} or {@code BOTH}.
 */
public enum NutrientBasis {
    PER_100G, PER_100ML, PER_SERVING, BOTH
}
