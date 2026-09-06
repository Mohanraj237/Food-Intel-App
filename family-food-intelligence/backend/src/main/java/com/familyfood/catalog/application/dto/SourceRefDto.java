package com.familyfood.catalog.application.dto;

import java.time.Instant;

/** Mirrors the inline {@code source} shape on {@code NutrientRow} in frontend/src/lib/catalog.ts. */
public record SourceRefDto(String type, String ref, Instant capturedAt) {
}
