package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.scoring.application.HouseholdFitService;
import com.familyfood.scoring.application.HouseholdFitView;
import com.familyfood.scoring.application.NutrientFact;
import com.familyfood.scoring.application.PersonalFitView;
import com.familyfood.scoring.application.ProductAssessmentInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic alternative selection (D-013). See the module javadoc on
 * {@link AlternativeReason} for exactly which two dimensions this ranks on and why a
 * nutrient-direction judgment ("lower sugar is better") is deliberately not one of them —
 * that requires a reviewed rule set (D-025) this repository does not yet have (D-033).
 *
 * <p><b>The pipeline:</b> same category &rarr; exclude anything that would introduce a
 * <em>new</em> allergen conflict for anyone the caller may see &rarr; rank what remains by
 * how many real improvements it offers &rarr; cap the result. Every step operates on real,
 * already-computed {@link HouseholdFitView} data — nothing here re-implements allergen
 * matching or verdict computation.
 */
@Service
public class AlternativesServiceImpl implements AlternativesService {

    /** How many same-category products to evaluate before ranking. Small on purpose — this
     *  is a dev-scale candidate pool, not a paginated search. */
    private static final int CANDIDATE_POOL_SIZE = 15;

    /** How many alternatives to actually return, once ranked. */
    private static final int MAX_RESULTS = 3;

    private static final String ASSESSED = "ASSESSED";
    private static final Map<String, Integer> VERDICT_SEVERITY = Map.of(
            "EVERYDAY", 0, "OCCASIONAL", 1, "LIMIT", 2, "DOESNT_FIT", 3);

    private final ProductQueryService productQueryService;
    private final HouseholdFitService householdFitService;
    private final Clock clock;

    public AlternativesServiceImpl(ProductQueryService productQueryService,
                                    HouseholdFitService householdFitService,
                                    Clock clock) {
        this.productQueryService = productQueryService;
        this.householdFitService = householdFitService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativesResult findAlternatives(UUID callerAccountId, UUID householdId, UUID productId) {
        ProductDetailDto current = productQueryService.getProduct(productId);

        // Household-membership authorization must run unconditionally, before any other
        // branch — including the "nothing to compare within" early return below. Checking
        // it only on the categorised path would let a caller who is not a member of
        // householdId still get a (trivially empty) 200 for an uncategorised product,
        // instead of the 403 every other household-scoped endpoint guarantees regardless
        // of the product's own data (found during Phase 6 hardening review).
        HouseholdFitView currentFit = householdFitService.evaluate(
                callerAccountId, householdId, toAssessmentInput(current));

        if (current.categoryCode() == null) {
            // Nothing to compare within — an honest empty result, not an error (D-013).
            return new AlternativesResult(productId, householdId, List.of(), clock.instant());
        }

        Map<UUID, PersonalFitView> currentByMember = currentFit.members().stream()
                .collect(Collectors.toMap(PersonalFitView::memberId, Function.identity()));

        List<ProductSummaryDto> candidates = productQueryService.listActiveInCategory(
                current.categoryCode(), productId, CANDIDATE_POOL_SIZE);

        List<AlternativeView> alternatives = new ArrayList<>();
        for (ProductSummaryDto candidateSummary : candidates) {
            ProductDetailDto candidate = productQueryService.getProduct(candidateSummary.id());
            HouseholdFitView candidateFit = householdFitService.evaluate(
                    callerAccountId, householdId, toAssessmentInput(candidate));
            Map<UUID, PersonalFitView> candidateByMember = candidateFit.members().stream()
                    .collect(Collectors.toMap(PersonalFitView::memberId, Function.identity()));

            List<AlternativeReason> betterOn = new ArrayList<>();
            List<AlternativeReason> worseOn = new ArrayList<>();

            for (Map.Entry<UUID, PersonalFitView> entry : currentByMember.entrySet()) {
                UUID memberId = entry.getKey();
                PersonalFitView currentMemberFit = entry.getValue();
                PersonalFitView candidateMemberFit = candidateByMember.get(memberId);
                if (candidateMemberFit == null) {
                    continue;
                }
                compareAllergens(memberId, currentMemberFit, candidateMemberFit, betterOn, worseOn);
                compareVerdicts(memberId, currentMemberFit, candidateMemberFit, betterOn, worseOn);
            }

            boolean introducesNewAllergenRisk = worseOn.stream()
                    .anyMatch(r -> "NEW_ALLERGEN_RISK".equals(r.code()));
            if (introducesNewAllergenRisk) {
                // Never recommend something that newly conflicts with someone's allergy —
                // this is not a "worse" alternative, it is not an alternative at all.
                continue;
            }
            if (betterOn.isEmpty() && worseOn.isEmpty()) {
                // No real, data-backed differentiator either way — an honest "we don't
                // know this is better" beats padding the list with a same-category product
                // that offers nothing demonstrable (D-013).
                continue;
            }

            alternatives.add(new AlternativeView(
                    candidate.id(), candidate.name(), candidate.brandName(), candidate.categoryCode(),
                    candidate.keyFacts(), List.copyOf(betterOn), List.copyOf(worseOn)));
        }

        alternatives.sort(Comparator
                .<AlternativeView>comparingInt(a -> -a.betterOn().size())
                .thenComparing(AlternativeView::name));

        List<AlternativeView> ranked = alternatives.size() > MAX_RESULTS
                ? alternatives.subList(0, MAX_RESULTS) : alternatives;

        return new AlternativesResult(productId, householdId, List.copyOf(ranked), clock.instant());
    }

    private static void compareAllergens(UUID memberId, PersonalFitView currentFit, PersonalFitView candidateFit,
                                          List<AlternativeReason> betterOn, List<AlternativeReason> worseOn) {
        Set<String> currentAllergens = Set.copyOf(currentFit.allergenMatches());
        Set<String> candidateAllergens = Set.copyOf(candidateFit.allergenMatches());

        for (String resolved : currentAllergens) {
            if (!candidateAllergens.contains(resolved)) {
                betterOn.add(new AlternativeReason("SAFER_ALLERGENS", memberId, resolved, null, null));
            }
        }
        for (String introduced : candidateAllergens) {
            if (!currentAllergens.contains(introduced)) {
                worseOn.add(new AlternativeReason("NEW_ALLERGEN_RISK", memberId, introduced, null, null));
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
        // Both null while D-024's active rule set ships empty (every verdict is
        // INSUFFICIENT_DATA) — this comparison is real and ready, but honestly inert until
        // D-025 supplies reviewed rule content (D-033).
        if (currentSeverity == null || candidateSeverity == null) {
            return;
        }
        if (candidateSeverity < currentSeverity) {
            betterOn.add(new AlternativeReason("BETTER_VERDICT", memberId, null,
                    candidateFit.verdict(), currentFit.verdict()));
        } else if (candidateSeverity > currentSeverity) {
            worseOn.add(new AlternativeReason("WORSE_VERDICT", memberId, null,
                    candidateFit.verdict(), currentFit.verdict()));
        }
    }

    private static ProductAssessmentInput toAssessmentInput(ProductDetailDto product) {
        List<NutrientFact> nutrients = product.nutrients().stream()
                .map(n -> new NutrientFact(n.nutrientCode(), n.amountPerServing(), n.amountPer100(), n.unit()))
                .toList();
        List<String> declaredAllergens = product.allergens().stream()
                .filter(a -> "CONTAINS".equals(a.declarationType()))
                .map(a -> a.code())
                .toList();
        return new ProductAssessmentInput(
                product.currentVersion().id(), product.categoryCode(), nutrients, declaredAllergens);
    }
}
