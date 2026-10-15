package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.scoring.application.AssessmentContext;
import com.familyfood.scoring.application.AssessmentInputMapper;
import com.familyfood.scoring.application.AssessmentOrchestrator;
import com.familyfood.scoring.application.HouseholdFitService;
import com.familyfood.scoring.application.HouseholdFitView;
import com.familyfood.scoring.application.PersonalFitView;
import com.familyfood.scoring.application.ProductHealthView;
import com.familyfood.scoring.application.ScoreDriver;
import com.familyfood.scoring.application.ScoreRankings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic alternative selection (D-013, D-060).
 *
 * <p><b>The pipeline:</b> the best-rated products in the same category (ACTIVE rule set's
 * persisted score), topped up with other same-category products while few are rated &rarr;
 * exclude anything that would introduce a <em>new</em> allergen risk for anyone the caller may
 * see &rarr; list real differences both ways &rarr; rank by improvements, then fewer
 * trade-offs, then rating &rarr; cap.
 *
 * <p><b>What counts as a real difference.</b> Allergen set membership and per-member verdicts
 * (as before, D-033); and, product-level (D-060): a different star band, a different points
 * band for one factor of the rating method — the band edge is the significance threshold, so
 * nothing here invents a "meaningfully lower" delta — and a different NOVA group. Directions
 * come from the rating method's own components (a risk factor's points going down is better),
 * never from a judgement coded in this class. A factor undeclared on either label is not
 * compared.
 */
@Service
public class AlternativesServiceImpl implements AlternativesService {

    /** How many same-category products to evaluate before ranking. */
    private static final int CANDIDATE_POOL_SIZE = 15;

    /** How many alternatives to return, once ranked. */
    private static final int MAX_RESULTS = 6;

    private static final String ASSESSED = "ASSESSED";
    private static final Map<String, Integer> VERDICT_SEVERITY = Map.of(
            "EVERYDAY", 0, "OCCASIONAL", 1, "LIMIT", 2, "DOESNT_FIT", 3);

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final HouseholdFitService householdFitService;
    private final ScoreRankings scoreRankings;
    private final Clock clock;

    public AlternativesServiceImpl(ProductQueryService productQueryService,
                                    AssessmentOrchestrator orchestrator,
                                    HouseholdFitService householdFitService,
                                    ScoreRankings scoreRankings,
                                    Clock clock) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.householdFitService = householdFitService;
        this.scoreRankings = scoreRankings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativesResult findAlternatives(UUID callerAccountId, UUID householdId, UUID productId) {
        ProductDetailDto current = productQueryService.getProduct(productId);
        AssessmentContext currentContext = orchestrator.assess(AssessmentInputMapper.from(current));

        // Household-membership authorization must run unconditionally, before any other
        // branch — including the "nothing to compare within" early return below. Checking
        // it only on the categorised path would let a caller who is not a member of
        // householdId still get a (trivially empty) 200 for an uncategorised product,
        // instead of the 403 every other household-scoped endpoint guarantees regardless
        // of the product's own data (found during Phase 6 hardening review).
        HouseholdFitView currentFit = householdFitService.evaluate(callerAccountId, householdId, currentContext.input());

        if (current.categoryCode() == null) {
            // Nothing to compare within — an honest empty result, not an error (D-013).
            return new AlternativesResult(productId, householdId, List.of(), clock.instant());
        }

        List<Candidate> candidates = candidates(current);
        List<HouseholdFitView> candidateFits = candidates.isEmpty() ? List.of()
                : householdFitService.evaluateMany(callerAccountId, householdId,
                        candidates.stream().map(c -> c.context().input()).toList());
        Map<UUID, PersonalFitView> currentByMember = byMember(currentFit);

        List<AlternativeView> alternatives = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            List<AlternativeReason> betterOn = new ArrayList<>();
            List<AlternativeReason> worseOn = new ArrayList<>();

            Map<UUID, PersonalFitView> candidateByMember = byMember(candidateFits.get(i));
            for (Map.Entry<UUID, PersonalFitView> entry : currentByMember.entrySet()) {
                PersonalFitView candidateMemberFit = candidateByMember.get(entry.getKey());
                if (candidateMemberFit == null) {
                    continue;
                }
                compareAllergens(entry.getKey(), entry.getValue(), candidateMemberFit, betterOn, worseOn);
                compareVerdicts(entry.getKey(), entry.getValue(), candidateMemberFit, betterOn, worseOn);
            }
            // Never recommend something that newly conflicts with someone's allergy — this is
            // not a "worse" alternative, it is not an alternative at all.
            if (worseOn.stream().anyMatch(r -> "NEW_ALLERGEN_RISK".equals(r.code()))) {
                continue;
            }
            compareProductLevel(currentContext, candidate.context(), betterOn, worseOn);
            addIfDifferent(alternatives, candidate, betterOn, worseOn);
        }

        return new AlternativesResult(productId, householdId, rank(alternatives), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativesResult findProductAlternatives(UUID productId) {
        ProductDetailDto current = productQueryService.getProduct(productId);
        if (current.categoryCode() == null) {
            return new AlternativesResult(productId, null, List.of(), clock.instant());
        }
        AssessmentContext currentContext = orchestrator.assess(AssessmentInputMapper.from(current));
        List<AlternativeView> alternatives = new ArrayList<>();
        for (Candidate candidate : candidates(current)) {
            List<AlternativeReason> betterOn = new ArrayList<>();
            List<AlternativeReason> worseOn = new ArrayList<>();
            compareProductLevel(currentContext, candidate.context(), betterOn, worseOn);
            addIfDifferent(alternatives, candidate, betterOn, worseOn);
        }
        return new AlternativesResult(productId, null, rank(alternatives), clock.instant());
    }

    // ------------------------------------------------------------------------ candidates

    private record Candidate(ProductSummaryDto summary, AssessmentContext context) {
    }

    /** Best-rated first; topped up with other same-category products while few are rated. */
    private List<Candidate> candidates(ProductDetailDto current) {
        Set<UUID> ids = new LinkedHashSet<>(scoreRankings.topScoredInCategory(current.categoryCode(), current.id(),
                CANDIDATE_POOL_SIZE));
        if (ids.size() < CANDIDATE_POOL_SIZE) {
            productQueryService.listActiveInCategory(current.categoryCode(), current.id(), CANDIDATE_POOL_SIZE)
                    .forEach(s -> ids.add(s.id()));
        }
        List<UUID> pool = ids.stream().limit(CANDIDATE_POOL_SIZE).toList();
        Map<UUID, ProductSummaryDto> summaries = productQueryService.getSummaries(pool).stream()
                .collect(Collectors.toMap(ProductSummaryDto::id, Function.identity()));

        List<Candidate> candidates = new ArrayList<>();
        for (UUID id : pool) {
            ProductSummaryDto summary = summaries.get(id);
            if (summary == null) {
                continue;
            }
            ProductDetailDto detail = productQueryService.getProduct(id);
            candidates.add(new Candidate(summary, orchestrator.assess(AssessmentInputMapper.from(detail))));
        }
        return candidates;
    }

    private static void addIfDifferent(List<AlternativeView> alternatives, Candidate candidate,
                                       List<AlternativeReason> betterOn, List<AlternativeReason> worseOn) {
        if (betterOn.isEmpty() && worseOn.isEmpty()) {
            // No real, data-backed differentiator either way — an honest "we don't know this
            // is better" beats padding the list with a same-category product that offers
            // nothing demonstrable (D-013).
            return;
        }
        ProductSummaryDto s = candidate.summary();
        ProductHealthView health = candidate.context().health();
        alternatives.add(new AlternativeView(s.id(), s.name(), s.brandName(), s.categoryCode(), s.imageUrl(),
                s.keyFacts(), health.isScored() ? health.stars() : null, health.isScored() ? health.score() : null,
                s.novaGroup(), List.copyOf(betterOn), List.copyOf(worseOn)));
    }

    private static List<AlternativeView> rank(List<AlternativeView> alternatives) {
        return alternatives.stream()
                .filter(a -> !a.betterOn().isEmpty())
                .sorted(Comparator
                        .<AlternativeView>comparingInt(a -> -a.betterOn().size())
                        .thenComparingInt(a -> a.worseOn().size())
                        .thenComparing(AlternativeView::stars, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AlternativeView::name))
                .limit(MAX_RESULTS)
                .toList();
    }

    // -------------------------------------------------------------------- product level

    /** Package-visible and static: unit-tested without a database. */
    static void compareProductLevel(AssessmentContext current, AssessmentContext candidate,
                                    List<AlternativeReason> betterOn, List<AlternativeReason> worseOn) {
        ProductHealthView a = current.health();
        ProductHealthView b = candidate.health();
        boolean bothRated = a.isScored() && b.isScored() && a.stars() != null && b.stars() != null
                && Objects.equals(a.ratingScheme(), b.ratingScheme()) && Objects.equals(a.foodType(), b.foodType());
        if (bothRated) {
            int cmp = b.stars().compareTo(a.stars());
            if (cmp > 0) {
                betterOn.add(AlternativeReason.product("HIGHER_RATING", null, b.stars(), a.stars()));
            } else if (cmp < 0) {
                worseOn.add(AlternativeReason.product("LOWER_RATING", null, b.stars(), a.stars()));
            }
            Map<String, ScoreDriver> currentDrivers = a.drivers().stream()
                    .collect(Collectors.toMap(ScoreDriver::code, Function.identity(), (x, y) -> x));
            for (ScoreDriver cd : b.drivers()) {
                ScoreDriver ad = currentDrivers.get(cd.code());
                if (ad == null || ad.assumed() || cd.assumed() || ad.points() == cd.points()) {
                    continue;
                }
                // Points follow the amount within a band table, so fewer points means a lower
                // amount. For a risk factor that is better; for a positive factor it is worse.
                boolean fewerPoints = cd.points() < ad.points();
                boolean riskFactor = "NEGATIVE".equals(cd.direction());
                String code = (fewerPoints ? "LOWER_" : "HIGHER_") + cd.nutrientCode();
                AlternativeReason reason = AlternativeReason.product(code, cd.nutrientCode(), cd.value(), ad.value());
                (riskFactor == fewerPoints ? betterOn : worseOn).add(reason);
            }
        }

        Integer nova = current.input().novaGroup();
        Integer candidateNova = candidate.input().novaGroup();
        if (nova != null && candidateNova != null && !nova.equals(candidateNova)) {
            AlternativeReason reason = AlternativeReason.product(candidateNova < nova ? "LESS_PROCESSED" : "MORE_PROCESSED",
                    null, BigDecimal.valueOf(candidateNova), BigDecimal.valueOf(nova));
            (candidateNova < nova ? betterOn : worseOn).add(reason);
        }
    }

    // --------------------------------------------------------------------- member level

    private static Map<UUID, PersonalFitView> byMember(HouseholdFitView fit) {
        return fit.members().stream().collect(Collectors.toMap(PersonalFitView::memberId, Function.identity()));
    }

    private static void compareAllergens(UUID memberId, PersonalFitView currentFit, PersonalFitView candidateFit,
                                          List<AlternativeReason> betterOn, List<AlternativeReason> worseOn) {
        Set<String> currentAllergens = Set.copyOf(currentFit.allergenMatches());
        Set<String> candidateAllergens = Set.copyOf(candidateFit.allergenMatches());

        for (String resolved : currentAllergens) {
            if (!candidateAllergens.contains(resolved)) {
                betterOn.add(AlternativeReason.member("SAFER_ALLERGENS", memberId, resolved, null, null));
            }
        }
        for (String introduced : candidateAllergens) {
            if (!currentAllergens.contains(introduced)) {
                worseOn.add(AlternativeReason.member("NEW_ALLERGEN_RISK", memberId, introduced, null, null));
            }
        }
    }

    private static void compareVerdicts(UUID memberId, PersonalFitView currentFit, PersonalFitView candidateFit,
                                         List<AlternativeReason> betterOn, List<AlternativeReason> worseOn) {
        if (!ASSESSED.equals(currentFit.status()) || !ASSESSED.equals(candidateFit.status())) {
            return;
        }
        Integer currentSeverity = VERDICT_SEVERITY.get(currentFit.verdict());
        Integer candidateSeverity = VERDICT_SEVERITY.get(candidateFit.verdict());
        // Null while a verdict is INSUFFICIENT_DATA — nothing to compare, not a difference.
        if (currentSeverity == null || candidateSeverity == null) {
            return;
        }
        if (candidateSeverity < currentSeverity) {
            betterOn.add(AlternativeReason.member("BETTER_VERDICT", memberId, null,
                    candidateFit.verdict(), currentFit.verdict()));
        } else if (candidateSeverity > currentSeverity) {
            worseOn.add(AlternativeReason.member("WORSE_VERDICT", memberId, null,
                    candidateFit.verdict(), currentFit.verdict()));
        }
    }
}
