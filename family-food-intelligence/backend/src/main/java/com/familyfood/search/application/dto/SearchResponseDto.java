package com.familyfood.search.application.dto;

import com.familyfood.catalog.application.dto.ProductSummaryDto;

import java.util.List;

/** Mirrors {@code SearchResponse} in frontend/src/lib/catalog.ts. */
public record SearchResponseDto(List<ProductSummaryDto> items, long total, int page, int size) {
}
