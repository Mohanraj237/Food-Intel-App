package com.familyfood.scoring.application;

import java.math.BigDecimal;

/** A nutrient fact for a product version, normalised to both bases (D-010). */
public record NutrientFact(
        String nutrientCode,
        BigDecimal amountPerServing,   // nullable — not declared
        BigDecimal amountPer100,       // nullable — not declared
        String unit) {
}
