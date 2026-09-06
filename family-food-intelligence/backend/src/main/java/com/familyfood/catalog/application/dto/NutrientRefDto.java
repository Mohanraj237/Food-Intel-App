package com.familyfood.catalog.application.dto;

/** A nutrient reference row. Mirrors {@code CategoryDto}'s shape for the same reference-data purpose. */
public record NutrientRefDto(String code, String displayName, String canonicalUnit, int sortOrder, boolean core) {
}
