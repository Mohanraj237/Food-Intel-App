package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.scoring.application.AssessmentContext;
import com.familyfood.scoring.application.AssessmentInputMapper;
import com.familyfood.scoring.application.AssessmentOrchestrator;
import com.familyfood.scoring.application.HouseholdFitService;
import com.familyfood.scoring.application.HouseholdFitView;
import com.familyfood.scoring.application.PersonalFitView;
import com.familyfood.scoring.application.ScoreRankings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Picks for your household" in a category (D-060): the 20 best-rated products, minus any
 * that declare an allergen matching someone the caller may see, ordered by the least severe
 * verdict among those members, then rating. Caller-scoped like every fit read (D-002), never
 * cached (D-056). Empty until products carry ratings — never padded with unrated ones.
 */
@Service
public class CategoryPicksService {

    static final int POOL = 20;
    static final int LIMIT = 8;
    private static final Map<String, Integer> SEVERITY = Map.of("EVERYDAY", 0, "OCCASIONAL", 1, "LIMIT", 2, "DOESNT_FIT", 3);

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final HouseholdFitService householdFitService;
    private final ScoreRankings scoreRankings;

    public CategoryPicksService(ProductQueryService productQueryService, AssessmentOrchestrator orchestrator,
                                HouseholdFitService householdFitService, ScoreRankings scoreRankings) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.householdFitService = householdFitService;
        this.scoreRankings = scoreRankings;
    }

    public record Pick(ProductSummaryDto product, BigDecimal stars, List<PersonalFitView> members) {
    }

    @Transactional(readOnly = true)
    public List<Pick> picks(UUID callerAccountId, UUID householdId, String categoryCode) {
        List<UUID> pool = scoreRankings.topScoredInCategory(categoryCode, null, POOL);
        if (pool.isEmpty()) {
            // Membership is still enforced for an empty answer.
            householdFitService.evaluateMany(callerAccountId, householdId, List.of());
            return List.of();
        }
        Map<UUID, ProductSummaryDto> summaries = productQueryService.getSummaries(pool).stream()
                .collect(Collectors.toMap(ProductSummaryDto::id, Function.identity()));
        List<UUID> ids = pool.stream().filter(summaries::containsKey).toList();
        List<AssessmentContext> contexts = ids.stream()
                .map(id -> orchestrator.assess(AssessmentInputMapper.from(productQueryService.getProduct(id)))).toList();
        List<HouseholdFitView> fits = householdFitService.evaluateMany(callerAccountId, householdId,
                contexts.stream().map(AssessmentContext::input).toList());

        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            List<PersonalFitView> members = fits.get(i).members();
            if (members.stream().anyMatch(m -> !m.allergenMatches().isEmpty())) {
                continue;
            }
            picks.add(new Pick(summaries.get(ids.get(i)), contexts.get(i).health().stars(), members));
        }
        return picks.stream()
                .sorted(Comparator.comparing((Pick p) -> worst(p.members()))
                        .thenComparing(Pick::stars, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(p -> p.product().name()))
                .limit(LIMIT)
                .toList();
    }

    /** Most severe real verdict among visible members; members with none rank as unknown (last). */
    private static int worst(List<PersonalFitView> members) {
        return members.stream()
                .filter(m -> "ASSESSED".equals(m.status()))
                .map(m -> SEVERITY.get(m.verdict()))
                .filter(java.util.Objects::nonNull)
                .max(Integer::compare)
                .orElse(SEVERITY.size());
    }
}
