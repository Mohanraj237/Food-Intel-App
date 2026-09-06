package com.familyfood.scoring.domain;

/**
 * Mirrors the {@code scoring_rule.basis} CHECK constraint in V5__scoring.sql (D-010: every
 * rule is evaluated on both per-serving and per-100g bases where {@link #BOTH} applies).
 *
 * <p>Deliberately not {@code com.familyfood.catalog.domain.NutrientBasis}: that enum also
 * carries {@code PER_100ML} (a product_nutrient concern), which has no meaning for a
 * scoring_rule basis and would let an unrepresentable state compile.
 */
public enum Basis {
    PER_SERVING, PER_100G, BOTH
}
