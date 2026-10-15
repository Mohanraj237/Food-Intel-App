package com.familyfood.scoring.api;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.platform.security.CurrentUser;
import com.familyfood.scoring.application.AssessmentInputMapper;
import com.familyfood.scoring.application.AssessmentOrchestrator;
import com.familyfood.scoring.application.HouseholdFitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-person fit for a product, scoped to one household. Requires a principal — not listed in
 * {@code SecurityConfig}'s permitAll, so it falls under "anyRequest().authenticated()" like
 * every other household-scoped route (D-020: only product-level facts are anonymous).
 *
 * <p>{@code householdId} is not an authorization input — exactly as in {@code
 * HouseholdController}/{@code ProfileController}: it names a target to authorize against, not
 * the caller's own identity. {@link HouseholdFitService#evaluate} throws {@code
 * NOT_HOUSEHOLD_MEMBER} if the caller is not actually a live member of it, and returns fit only
 * for the members the caller is authorized to see within it (D-002, D-024 point 4) — this
 * controller does not, and must not, loosen either of those.
 *
 * <p>The input goes through {@link AssessmentOrchestrator} first so verdict rules can reference
 * the engine-derived facts ({@code INR_STARS}, {@code NOVA_GROUP}) — D-059.
 *
 * <p>Thin by design (Constitution §36): bind, extract the caller from {@link CurrentUser},
 * delegate, map the result.
 */
@RestController
@RequestMapping("/api/v1/households/{householdId}/products/{productId}/fit")
@Tag(name = "Scoring")
public class HouseholdFitController {

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final HouseholdFitService householdFitService;
    private final CurrentUser currentUser;

    public HouseholdFitController(ProductQueryService productQueryService,
                                  AssessmentOrchestrator orchestrator,
                                  HouseholdFitService householdFitService,
                                  CurrentUser currentUser) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.householdFitService = householdFitService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Get per-person fit for a product, for one household",
               description = "403 (NOT_HOUSEHOLD_MEMBER) if the caller is not a live member of "
                           + "householdId. The result includes only members the caller is "
                           + "authorized to see: their own membership, plus any child they "
                           + "guard — never another adult's fit, not even for the household "
                           + "owner (D-002). Each member's own entry further distinguishes "
                           + "ASSESSED from NO_PROFILE/CONSENT_REQUIRED/CONSENT_WITHDRAWN/"
                           + "NOT_AUTHORIZED — never a fabricated verdict.")
    public HouseholdFitResponse get(@PathVariable UUID householdId, @PathVariable UUID productId) {
        UUID callerAccountId = currentUser.require().accountId();
        var product = productQueryService.getProduct(productId);
        var input = orchestrator.assess(AssessmentInputMapper.from(product)).input();
        return HouseholdFitResponse.from(householdFitService.evaluate(callerAccountId, householdId, input));
    }
}
