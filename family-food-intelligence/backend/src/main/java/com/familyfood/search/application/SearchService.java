package com.familyfood.search.application;

import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.ProductSuggestionService;
import com.familyfood.catalog.application.dto.AssessmentBadgeDto;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.catalog.application.dto.SuggestionDto;
import com.familyfood.search.application.dto.SearchResponseDto;
import com.familyfood.search.infra.SearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Product search v2 (D-061): the query is parsed deterministically for filters, sort and
 * category ({@link SearchQueryParser}); explicit request parameters win over anything read
 * from the words; the rest is matched as text. Results are filtered and ordered in SQL over
 * {@code product_search_facts} ({@link SearchQueryBuilder}) — no client-side re-sorting, no
 * value invented for a product that does not declare it.
 *
 * <p>Pagination is page-based and 0-indexed to match the frontend contract
 * (frontend/src/lib/catalog.ts). Page size is clamped so a caller cannot force an unbounded
 * result set.
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int RELATED_LIMIT = 8;
    /** A first page where at least this share of results is one brand is "about" that brand. */
    private static final double DOMINANT_SHARE = 0.6;

    private final SearchQueryBuilder queries;
    private final ProductQueryService productQueryService;
    private final ProductSuggestionService suggestions;

    public SearchService(SearchQueryBuilder queries,
                         ProductQueryService productQueryService,
                         ProductSuggestionService suggestions) {
        this.queries = queries;
        this.productQueryService = productQueryService;
        this.suggestions = suggestions;
    }

    /** The original four-parameter contract, kept for existing callers. */
    public SearchResponseDto search(String q, String category, Integer page, Integer size) {
        SearchFilters explicit = new SearchFilters(blankToNull(category), null, null, null, null, null, null, null,
                List.of(), null, null);
        return search(q, explicit, null, page, size);
    }

    public SearchResponseDto search(String q, SearchFilters explicitFilters, SearchSort explicitSort,
                                    Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        String rawQuery = blankToNull(q);
        SearchQueryParser.ParsedQuery parsed = new SearchQueryParser(productQueryService.categoryAliases()).parse(rawQuery);
        SearchFilters filters = explicitFilters.validated().overlay(parsed.filters()).validated();
        SearchSort sort = explicitSort != null ? explicitSort : parsed.sort();
        String textQuery = parsed.residual();

        SearchQueryBuilder.SearchPage pageResult = queries.search(textQuery, filters, sort, safeSize,
                (long) safePage * safeSize);
        List<ProductSummaryDto> items = withBadges(productQueryService.getSummaries(pageResult.ids()));

        SearchResponseDto.RelatedDto related = safePage == 0 && rawQuery != null ? related(textQuery, items) : null;
        String didYouMean = pageResult.total() == 0 && textQuery != null
                ? suggestions.didYouMean(textQuery).orElse(null) : null;

        return new SearchResponseDto(items, pageResult.total(), safePage, safeSize, sort.name(), textQuery,
                parsed.interpreted(), pageResult.excludedForMissingData(), related, didYouMean);
    }

    public List<SuggestionDto> suggest(String q) {
        return suggestions.suggest(q);
    }

    // ------------------------------------------------------------------------------ related

    /**
     * When the query names a brand (exactly, or by dominating the first page): the best-scored
     * products in that brand's main category from other brands. When one product clearly leads:
     * more from its brand, and higher-scored products in its category. Empty lists are honest —
     * "better in this category" stays empty until products carry scores.
     */
    private SearchResponseDto.RelatedDto related(String textQuery, List<ProductSummaryDto> items) {
        if (items.isEmpty()) {
            return null;
        }
        Map<UUID, UUID[]> brandCategory = queries.brandAndCategory(items.stream().map(ProductSummaryDto::id).toList());

        UUID namedBrand = textQuery == null ? null : queries.brandNamedExactly(ProductIngestionService.normalize(textQuery));
        UUID dominantBrand = namedBrand != null ? namedBrand : dominant(items, brandCategory, 0);
        if (dominantBrand != null) {
            ProductSummaryDto sample = items.stream()
                    .filter(i -> dominantBrand.equals(brandCategory.getOrDefault(i.id(), new UUID[2])[0]))
                    .findFirst().orElse(items.get(0));
            String categoryCode = mostCommonCategoryCode(items, brandCategory, dominantBrand);
            List<ProductSummaryDto> better = categoryCode == null ? List.of()
                    : summaries(queries.topScoredInCategory(categoryCode, dominantBrand, null, RELATED_LIMIT));
            return new SearchResponseDto.RelatedDto("BRAND", sample.brandName(), List.of(), categoryCode, better);
        }

        ProductSummaryDto top = items.get(0);
        UUID[] topBrandCategory = brandCategory.get(top.id());
        if (topBrandCategory == null || items.size() > 3) {
            return null;
        }
        List<ProductSummaryDto> sameBrand = topBrandCategory[0] == null ? List.of()
                : summaries(queries.sameBrand(topBrandCategory[0], top.id(), RELATED_LIMIT));
        Integer topScore = top.assessment() == null ? null : top.assessment().score();
        List<ProductSummaryDto> better = top.categoryCode() == null || topScore == null ? List.of()
                : summaries(queries.topScoredInCategory(top.categoryCode(), null, topScore, RELATED_LIMIT));
        return new SearchResponseDto.RelatedDto("PRODUCT", top.brandName(), sameBrand, top.categoryCode(), better);
    }

    private static UUID dominant(List<ProductSummaryDto> items, Map<UUID, UUID[]> brandCategory, int index) {
        Map<UUID, Long> counts = items.stream()
                .map(i -> brandCategory.getOrDefault(i.id(), new UUID[2])[index])
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .filter(e -> items.size() >= 3 && e.getValue() >= Math.ceil(items.size() * DOMINANT_SHARE))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static String mostCommonCategoryCode(List<ProductSummaryDto> items, Map<UUID, UUID[]> brandCategory, UUID brand) {
        return items.stream()
                .filter(i -> brand.equals(brandCategory.getOrDefault(i.id(), new UUID[2])[0]))
                .map(ProductSummaryDto::categoryCode)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<ProductSummaryDto> summaries(List<UUID> ids) {
        return withBadges(productQueryService.getSummaries(ids));
    }

    /** Adds the ACTIVE rule set's persisted score, when the product has one. */
    private List<ProductSummaryDto> withBadges(List<ProductSummaryDto> summaries) {
        Map<UUID, SearchQueryBuilder.Facts> facts = queries.factsFor(summaries.stream().map(ProductSummaryDto::id).toList());
        return summaries.stream().map(s -> {
            SearchQueryBuilder.Facts f = facts.get(s.id());
            if (f == null || f.scoreStatus() == null) {
                return s;
            }
            return s.withAssessment(new AssessmentBadgeDto(f.scoreStatus(), f.score(), f.stars(), f.grade()));
        }).toList();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
