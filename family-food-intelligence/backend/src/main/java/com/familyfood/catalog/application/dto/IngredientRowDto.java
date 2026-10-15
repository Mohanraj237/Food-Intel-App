package com.familyfood.catalog.application.dto;

/**
 * Mirrors the inline ingredient row shape on {@code ProductDetail} in
 * frontend/src/lib/catalog.ts. {@code canonicalName} and {@code insNumber} are
 * {@code null} while the raw label text is unmapped to the ingredient vocabulary.
 * {@code additiveCode}/{@code additiveName}/{@code additiveFunction} (V15) come from the
 * INS reference table when the token carried a recognised INS/E number — a name and a
 * Codex functional class only, never a safety statement.
 */
public record IngredientRowDto(String rawText,
                               int position,
                               String canonicalName,
                               boolean isAdditive,
                               String insNumber,
                               String additiveCode,
                               String additiveName,
                               String additiveFunction) {
}
