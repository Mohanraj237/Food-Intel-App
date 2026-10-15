package com.familyfood.scoring.api;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.security.CurrentUser;
import com.familyfood.scoring.application.AssessmentInputMapper;
import com.familyfood.scoring.application.AssessmentOrchestrator;
import com.familyfood.scoring.application.HouseholdFitService;
import com.familyfood.scoring.application.HouseholdFitView;
import com.familyfood.scoring.application.ProductAssessmentInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Household fit for a page of products at once (D-061) — what lets a search result or a list
 * show the caller's own verdict chip on every card without one request per card. Same scope as
 * the single-product endpoint: the caller's own fit plus any child they guard, never another
 * adult's (D-002); 403 if the caller is not a live member of the household. Never cached (D-056).
 *
 * <p>A product that does not exist or is hidden is listed in {@code notFound} rather than
 * failing the whole batch.
 */
@RestController
@Tag(name = "Scoring")
public class BatchFitController {

    static final int MAX_PRODUCTS = 50;

    private final ProductQueryService productQueryService;
    private final AssessmentOrchestrator orchestrator;
    private final HouseholdFitService householdFitService;
    private final CurrentUser currentUser;

    public BatchFitController(ProductQueryService productQueryService,
                              AssessmentOrchestrator orchestrator,
                              HouseholdFitService householdFitService,
                              CurrentUser currentUser) {
        this.productQueryService = productQueryService;
        this.orchestrator = orchestrator;
        this.householdFitService = householdFitService;
        this.currentUser = currentUser;
    }

    public record BatchFitRequest(@NotNull @Size(min = 1, max = MAX_PRODUCTS) List<@NotNull UUID> productIds) {
    }

    public record ProductFit(UUID productId, UUID productVersionId, List<PersonalFitResponse> members) {
    }

    public record BatchFitResponse(UUID householdId, List<ProductFit> items, List<UUID> notFound) {
    }

    @PostMapping("/api/v1/households/{householdId}/products/fit:batch")
    @Operation(summary = "Get per-person fit for up to 50 products, for one household",
               description = "Same scope and statuses as GET .../products/{productId}/fit, for many "
                           + "products. Unknown or hidden products are listed in notFound.")
    public BatchFitResponse batch(@PathVariable UUID householdId, @Valid @RequestBody BatchFitRequest request) {
        UUID callerAccountId = currentUser.require().accountId();
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(request.productIds()));
        if (ids.size() > MAX_PRODUCTS) {
            throw AppException.validation("productIds", "at most " + MAX_PRODUCTS);
        }

        List<UUID> found = new ArrayList<>();
        List<UUID> notFound = new ArrayList<>();
        List<ProductAssessmentInput> inputs = new ArrayList<>();
        for (UUID id : ids) {
            try {
                ProductDetailDto product = productQueryService.getProduct(id);
                inputs.add(orchestrator.assess(AssessmentInputMapper.from(product)).input());
                found.add(id);
            } catch (AppException e) {
                if (e.code() != ErrorCode.PRODUCT_NOT_FOUND) {
                    throw e;
                }
                notFound.add(id);
            }
        }

        if (inputs.isEmpty()) {
            // Still enforce membership: a non-member learns nothing, not even "none found".
            householdFitService.evaluateMany(callerAccountId, householdId, List.of());
            return new BatchFitResponse(householdId, List.of(), notFound);
        }

        List<HouseholdFitView> views = householdFitService.evaluateMany(callerAccountId, householdId, inputs);
        List<ProductFit> items = new ArrayList<>(views.size());
        for (int i = 0; i < views.size(); i++) {
            HouseholdFitView view = views.get(i);
            items.add(new ProductFit(found.get(i), view.productVersionId(),
                    view.members().stream().map(PersonalFitResponse::from).toList()));
        }
        return new BatchFitResponse(householdId, items, notFound);
    }
}
