package com.familyfood.scoring.application;

import java.math.BigDecimal;

/**
 * A nutrient fact for a product version, normalised to both bases (D-010).
 * {@code estimated} is {@code true} when the catalogue holds the value as an estimate rather
 * than a declared label figure (e.g. {@code FVNL_PCT} from Open Food Facts' ingredient-list
 * estimate) — carried through to {@code ScoreDriver.isEstimated} so the UI never presents a
 * derived number as a declared one (D-015).
 */
public record NutrientFact(
        String nutrientCode,
        BigDecimal amountPerServing,   // nullable — not declared
        BigDecimal amountPer100,       // nullable — not declared
        String unit,
        boolean estimated) {

    public NutrientFact(String nutrientCode, BigDecimal amountPerServing, BigDecimal amountPer100, String unit) {
        this(nutrientCode, amountPerServing, amountPer100, unit, false);
    }
}
