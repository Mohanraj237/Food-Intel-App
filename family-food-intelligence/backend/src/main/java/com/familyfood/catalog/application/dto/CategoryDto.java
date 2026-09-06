package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;

/** Mirrors {@code Category} in frontend/src/lib/catalog.ts. */
public record CategoryDto(String code,
                          String name,
                          String path,
                          int sortOrder,
                          BigDecimal typicalServingMinG,
                          BigDecimal typicalServingMaxG) {
}
