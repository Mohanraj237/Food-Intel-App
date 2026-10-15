package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.platform.error.AppException;
import com.familyfood.scoring.application.AssessmentContext;
import com.familyfood.scoring.application.AssessmentInputMapper;
import com.familyfood.scoring.application.AssessmentOrchestrator;
import com.familyfood.scoring.application.HouseholdFitService;
import com.familyfood.scoring.application.HouseholdFitView;
import com.familyfood.scoring.application.PersonalFitView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares 2–5 products (D-066, supersedes D-027 point 1): each product's summary, label
 * facts and product-level rating, the caller's household fit when a household is named, and a
 * deterministic {@link BestOverall}. Anonymous without a household (product facts are public,
 * D-020); with one, the household-fit scope is exactly the single-product endpoint's — the
 * caller plus guarded children, never another adult (D-002).
 */
@Service
public class CompareService {

    public static final int MIN_PRODUCTS = 2;
    public static final int MAX_PRODUCTS = 5;

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final HouseholdFitService householdFitService;

    public CompareService(ProductQueryService productQueryService, AssessmentOrchestrator orchestrator,
                          HouseholdFitService householdFitService) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.householdFitService = householdFitService;
    }

    public record ComparedProduct(ProductSummaryDto summary, ProductDetailDto detail, AssessmentContext context,
                                  List<PersonalFitView> members) {
    }

    public record Comparison(UUID householdId, List<ComparedProduct> products, BestOverall bestOverall) {
    }

    /**
     * @param callerAccountId null when anonymous; required when {@code householdId} is given.
     * @throws AppException VALIDATION_FAILED for fewer than 2 or more than 5 distinct ids,
     *                      PRODUCT_NOT_FOUND for an unknown id, NOT_HOUSEHOLD_MEMBER for a
     *                      household the caller is not a live member of.
     */
    @Transactional(readOnly = true)
    public Comparison compare(UUID callerAccountId, UUID householdId, List<UUID> requestedIds) {
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        if (ids.size() < MIN_PRODUCTS || ids.size() > MAX_PRODUCTS) {
            throw AppException.validation("ids", "compare between " + MIN_PRODUCTS + " and " + MAX_PRODUCTS + " products");
        }

        List<ProductDetailDto> details = ids.stream().map(productQueryService::getProduct).toList();
        List<AssessmentContext> contexts = details.stream()
                .map(d -> orchestrator.assess(AssessmentInputMapper.from(d))).toList();
        Map<UUID, ProductSummaryDto> summaries = productQueryService.getSummaries(ids).stream()
                .collect(Collectors.toMap(ProductSummaryDto::id, Function.identity()));

        List<HouseholdFitView> fits = householdId == null ? null
                : householdFitService.evaluateMany(callerAccountId, householdId,
                        contexts.stream().map(AssessmentContext::input).toList());

        List<ComparedProduct> products = new ArrayList<>();
        List<BestOverall.Entry> entries = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            List<PersonalFitView> members = fits == null ? List.of() : fits.get(i).members();
            products.add(new ComparedProduct(summaries.get(ids.get(i)), details.get(i), contexts.get(i), members));
            entries.add(new BestOverall.Entry(ids.get(i), contexts.get(i).health(), members));
        }
        return new Comparison(householdId, products, BestOverall.decide(entries, householdId != null));
    }
}
