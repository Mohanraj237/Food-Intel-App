package com.familyfood.catalog.application.dto;

/**
 * Mirrors the inline ingredient row shape on {@code ProductDetail} in
 * frontend/src/lib/catalog.ts. {@code canonicalName} and {@code insNumber} are
 * {@code null} while the raw label text is unmapped to the ingredient vocabulary.
 */
public record IngredientRowDto(String rawText,
                               int position,
                               String canonicalName,
                               boolean isAdditive,
                               String insNumber) {
}
