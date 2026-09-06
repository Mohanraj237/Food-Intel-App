package com.familyfood.search.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.search.application.dto.SearchResponseDto;
import com.familyfood.search.infra.ProductSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Product search: full-text ({@code product.search_vector}) and trigram
 * ({@code product.search_text}) matching, delegated to a native query
 * ({@link ProductSearchRepository}) because neither is expressible in JPQL.
 *
 * <p>Pagination is page-based and 0-indexed to match the frontend contract
 * (frontend/src/lib/catalog.ts). Page size is clamped to a sane range so a caller
 * cannot force an unbounded result set.
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ProductSearchRepository searchRepository;
    private final ProductQueryService productQueryService;

    public SearchService(ProductSearchRepository searchRepository,
                         ProductQueryService productQueryService) {
        this.searchRepository = searchRepository;
        this.productQueryService = productQueryService;
    }

    public SearchResponseDto search(String q, String category, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        String normalizedQuery = blankToNull(q);
        String normalizedCategory = blankToNull(category);

        long total = searchRepository.countSearch(normalizedQuery, normalizedCategory);
        List<UUID> ids = searchRepository.searchIds(normalizedQuery, normalizedCategory,
                safeSize, (long) safePage * safeSize);
        List<ProductSummaryDto> items = productQueryService.getSummaries(ids);

        return new SearchResponseDto(items, total, safePage, safeSize);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
