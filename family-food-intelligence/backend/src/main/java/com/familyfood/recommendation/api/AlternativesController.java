package com.familyfood.recommendation.api;

import com.familyfood.platform.security.CurrentUser;
import com.familyfood.recommendation.application.AlternativesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Alternative products for one product, scoped to one household (D-013, FR-007, PX-2).
 * Route shape deliberately mirrors {@code scoring.api.HouseholdFitController} exactly —
 * same authentication requirement, same {@code householdId}/{@code productId} path, same
 * "not an authorization input, a target to authorize against" contract — because
 * alternatives are built from the same per-member fit data that endpoint computes.
 */
@RestController
@RequestMapping("/api/v1/households/{householdId}/products/{productId}/alternatives")
@Tag(name = "Recommendation")
public class AlternativesController {

    private final AlternativesService alternativesService;
    private final CurrentUser currentUser;

    public AlternativesController(AlternativesService alternativesService, CurrentUser currentUser) {
        this.alternativesService = alternativesService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Real, ranked alternatives to a product for one household",
               description = "Every alternative publishes both betterOn and worseOn (D-013). "
                           + "An empty list is a first-class result — never padded with a "
                           + "same-category product that offers no demonstrable improvement, "
                           + "and never one that would introduce a new allergen risk for "
                           + "anyone the caller may see.")
    public AlternativesResponse get(@PathVariable UUID householdId, @PathVariable UUID productId) {
        UUID callerAccountId = currentUser.require().accountId();
        return AlternativesResponse.from(
                alternativesService.findAlternatives(callerAccountId, householdId, productId));
    }
}
