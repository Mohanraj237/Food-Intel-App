package com.familyfood.catalog.application.dto;

/**
 * One search-box suggestion (D-061). {@code kind} is {@code BRAND}, {@code CATEGORY} or
 * {@code PRODUCT}; {@code target} is what the frontend navigates with — the brand name to
 * search for, the category code, or the product id. {@code secondary} is a short context
 * line (a product's brand, a brand's product count) and may be null.
 */
public record SuggestionDto(String kind, String label, String target, String secondary) {
}
