package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;

/** Mirrors {@code PackSize} in frontend/src/lib/catalog.ts. */
public record PackSizeDto(BigDecimal value, String unit) {
}
