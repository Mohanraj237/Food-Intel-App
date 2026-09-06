package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;

/** Mirrors {@code KeyFact} in frontend/src/lib/catalog.ts. */
public record KeyFactDto(String nutrientCode,
                         String displayName,
                         BigDecimal amountPerServing,
                         BigDecimal amountPer100,
                         String unit) {
}
