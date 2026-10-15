package com.familyfood.search.application.dto;

import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.search.application.InterpretedTerm;

import java.util.List;

/**
 * Mirrors {@code SearchResponse} in frontend/src/lib/catalog.ts. The first four fields are the
 * original contract, unchanged; the rest are search v2 (D-061):
 *
 * <ul>
 *   <li>{@code sort} — the ordering actually applied (from the request, or read from the query);
 *   <li>{@code residualQuery} — the words left after the parser took its filters out, the text
 *       actually matched; null when nothing was left;
 *   <li>{@code interpreted} — what the parser read from the query, for removable chips;
 *   <li>{@code excludedForMissingData} — products that matched everything they declare but lack
 *       a fact a filter needs, so the UI never implies they were checked and failed;
 *   <li>{@code related} — extra rows when the query names a brand or one product clearly;
 *   <li>{@code didYouMean} — the closest brand/product name when nothing matched.
 * </ul>
 */
public record SearchResponseDto(List<ProductSummaryDto> items,
                                long total,
                                int page,
                                int size,
                                String sort,
                                String residualQuery,
                                List<InterpretedTerm> interpreted,
                                long excludedForMissingData,
                                RelatedDto related,
                                String didYouMean) {

    /** Related rows. Each list is empty rather than padded when nothing real qualifies. */
    public record RelatedDto(String kind, String brandName, List<ProductSummaryDto> sameBrand,
                             String categoryCode, List<ProductSummaryDto> betterInCategory) {
    }
}
