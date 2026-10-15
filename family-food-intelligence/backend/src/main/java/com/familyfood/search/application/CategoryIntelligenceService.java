package com.familyfood.search.application;

import com.familyfood.activity.application.ActivityStats;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.AssessmentBadgeDto;
import com.familyfood.catalog.application.dto.CategoryDto;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.search.infra.SearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A category page's content (D-060): counts and six product rails. Every rail is an ordering
 * of real, declared values — "lowest sugar" only holds products that declare sugar — and
 * "popular" only holds products at least three different people viewed in the last 30 days
 * ({@link ActivityStats}), so it reveals nothing about any one person. A rail with nothing
 * real to show is empty, never padded. Public: nothing here is about the viewer.
 */
@Service
@Transactional(readOnly = true)
public class CategoryIntelligenceService {

    static final int RAIL_SIZE = 8;
    static final Duration POPULAR_WINDOW = Duration.ofDays(30);

    private final ProductQueryService productQueryService;
    private final SearchQueryBuilder queries;
    private final ActivityStats activityStats;

    public CategoryIntelligenceService(ProductQueryService productQueryService, SearchQueryBuilder queries,
                                       ActivityStats activityStats) {
        this.productQueryService = productQueryService;
        this.queries = queries;
        this.activityStats = activityStats;
    }

    public record Counts(long products, long rated) {
    }

    public record Rails(List<ProductSummaryDto> popular, List<ProductSummaryDto> betterChoices,
                        List<ProductSummaryDto> lowestSugar, List<ProductSummaryDto> highestProtein,
                        List<ProductSummaryDto> lowestSodium, List<ProductSummaryDto> leastProcessed) {
    }

    public record CategoryIntelligence(CategoryDto category, Counts counts, Rails rails) {
    }

    public CategoryIntelligence intelligence(String categoryCode) {
        CategoryDto category = productQueryService.listCategories().stream()
                .filter(c -> c.code().equals(categoryCode))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        long[] counts = queries.categoryCounts(categoryCode);
        return new CategoryIntelligence(category, new Counts(counts[0], counts[1]), new Rails(
                summaries(activityStats.mostViewedInCategory(categoryCode, POPULAR_WINDOW, RAIL_SIZE)),
                summaries(queries.categoryRail(categoryCode, SearchSort.SCORE_DESC, RAIL_SIZE)),
                summaries(queries.categoryRail(categoryCode, SearchSort.SUGAR_ASC, RAIL_SIZE)),
                summaries(queries.categoryRail(categoryCode, SearchSort.PROTEIN_DESC, RAIL_SIZE)),
                summaries(queries.categoryRail(categoryCode, SearchSort.SODIUM_ASC, RAIL_SIZE)),
                summaries(queries.leastProcessedInCategory(categoryCode, RAIL_SIZE))));
    }

    private List<ProductSummaryDto> summaries(List<UUID> ids) {
        List<ProductSummaryDto> summaries = productQueryService.getSummaries(ids);
        Map<UUID, SearchQueryBuilder.Facts> facts = queries.factsFor(ids);
        return summaries.stream().map(s -> {
            SearchQueryBuilder.Facts f = facts.get(s.id());
            return f == null || f.scoreStatus() == null ? s
                    : s.withAssessment(new AssessmentBadgeDto(f.scoreStatus(), f.score(), f.stars(), f.grade()));
        }).toList();
    }
}
