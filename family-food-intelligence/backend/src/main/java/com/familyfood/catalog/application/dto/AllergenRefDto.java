package com.familyfood.catalog.application.dto;

/** An allergen reference row. Mirrors {@code CategoryDto}'s/{@code NutrientRefDto}'s shape for the same reference-data purpose. */
public record AllergenRefDto(String code, String displayName, boolean fssaiDeclarable, int sortOrder) {
}
