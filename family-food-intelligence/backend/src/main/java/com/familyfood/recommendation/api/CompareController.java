package com.familyfood.recommendation.api;

import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.security.CurrentUser;
import com.familyfood.recommendation.application.BestOverall;
import com.familyfood.recommendation.application.CompareService;
import com.familyfood.scoring.application.PersonalFitView;
import com.familyfood.scoring.application.ProductHealthView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/compare?ids=a,b[,c,d,e][&householdId=]} (D-066). Public for product facts;
 * naming a household requires a principal and membership, and returns only the members the
 * caller may see (D-002). Thin: bind, delegate, map.
 */
@RestController
@Tag(name = "Recommendation")
public class CompareController {

    private final CompareService compareService;
    private final CurrentUser currentUser;

    public CompareController(CompareService compareService, CurrentUser currentUser) {
        this.compareService = compareService;
        this.currentUser = currentUser;
    }

    public record MemberFit(UUID memberId, String status, String verdict, List<String> allergenMatches) {
        static MemberFit from(PersonalFitView v) {
            return new MemberFit(v.memberId(), v.status(), v.verdict(), v.allergenMatches());
        }
    }

    public record Rating(String status, Integer score, BigDecimal stars, String grade, String ratingScheme,
                         String foodType) {
        static Rating from(ProductHealthView h) {
            return new Rating(h.status(), h.score(), h.stars(), h.grade(), h.ratingScheme(), h.foodType());
        }
    }

    public record NutrientFact(String code, String displayName, BigDecimal per100, String unit, String basis,
                               boolean estimated) {
    }

    public record AllergenFact(String code, String declarationType) {
    }

    public record Item(ProductSummaryDto product, Rating rating, Integer novaGroup, String dietFlag,
                       boolean dietFlagEstimated, List<NutrientFact> nutrients, List<AllergenFact> allergens,
                       List<String> ingredients, List<MemberFit> members) {
    }

    public record Best(UUID productId, String outcome, List<String> reasons, List<UUID> excludedForAllergens) {
        static Best from(BestOverall b) {
            return new Best(b.productId(), b.outcome(), b.reasons(), b.excludedForAllergens());
        }
    }

    public record CompareResponse(UUID householdId, List<Item> items, Best bestOverall) {
    }

    @GetMapping("/api/v1/compare")
    @Operation(summary = "Compare 2–5 products",
               description = "Label facts per 100 g/ml, the product-level rating, and — with householdId, "
                           + "signed in — each visible member's fit. bestOverall is deterministic and says "
                           + "NO_WINNER_* with the reason when the data cannot separate the products.")
    public CompareResponse compare(@RequestParam String ids, @RequestParam(required = false) UUID householdId) {
        UUID caller = householdId == null ? null : currentUser.require().accountId();
        CompareService.Comparison comparison = compareService.compare(caller, householdId, parseIds(ids));
        List<Item> items = comparison.products().stream().map(p -> new Item(
                p.summary(),
                Rating.from(p.context().health()),
                p.detail().currentVersion().novaGroup(),
                p.detail().currentVersion().dietFlag(),
                p.detail().currentVersion().dietFlagEstimated(),
                p.detail().nutrients().stream()
                        .filter(n -> n.amountPer100() != null)
                        .map(n -> new NutrientFact(n.nutrientCode(), n.displayName(), n.amountPer100(), n.unit(),
                                n.basisPer100(), n.isEstimated()))
                        .toList(),
                p.detail().allergens().stream().map(a -> new AllergenFact(a.code(), a.declarationType())).toList(),
                p.detail().ingredients().stream().map(i -> i.rawText()).toList(),
                p.members().stream().map(MemberFit::from).toList())).toList();
        return new CompareResponse(comparison.householdId(), items, Best.from(comparison.bestOverall()));
    }

    private static List<UUID> parseIds(String csv) {
        List<UUID> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException e) {
                throw AppException.validation("ids", "not a product id: " + trimmed);
            }
        }
        return ids;
    }
}
