package com.familyfood.catalog.application.dto;

/** Mirrors the inline allergen row shape on {@code ProductDetail} in frontend/src/lib/catalog.ts. */
public record AllergenRowDto(String code, String displayName, String declarationType) {
}
