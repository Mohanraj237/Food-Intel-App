package com.familyfood.capture.api;

import com.familyfood.capture.application.ContributionService;
import com.familyfood.capture.application.ContributionView;
import com.familyfood.capture.application.SubmitContributionCommand;
import com.familyfood.capture.application.SubmitContributionCommand.NutrientContribution;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Manual-entry intake for the contribution queue (system-architecture.md §6) — the path a
 * household uses to add a product's facts by hand after a barcode scan or search comes up
 * empty. Thin by design: bind, extract the caller from {@link CurrentUser}, delegate, map
 * the result (Constitution §36). Requires sign-in; this route deliberately has no entry in
 * {@code SecurityConfig}'s permit-all list, so it falls under the default
 * "everything else needs a principal" rule.
 *
 * <p>Every submission is queued {@code PENDING} for ops review — never auto-published, and
 * never visible in search. Nothing here writes to any {@code catalog} table.
 */
@RestController
@RequestMapping("/api/v1/contributions")
@Tag(name = "Contributions")
public class ContributionController {

    private final ContributionService contributionService;
    private final CurrentUser currentUser;

    public ContributionController(ContributionService contributionService, CurrentUser currentUser) {
        this.contributionService = contributionService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a manually entered product to the contribution queue",
               description = "Always created PENDING. Reviewed by ops separately; never "
                           + "auto-published and never visible in search until a reviewer "
                           + "creates a real catalogue product from it.")
    public ContributionResponse submit(@Valid @RequestBody SubmitContributionRequest request) {
        UUID callerAccountId = currentUser.require().accountId();
        ContributionView view = contributionService.submit(callerAccountId, toCommand(request));
        return ContributionResponse.from(view);
    }

    private static SubmitContributionCommand toCommand(SubmitContributionRequest request) {
        return new SubmitContributionCommand(
                request.gtin(),
                request.brandName(),
                request.productName(),
                request.categoryCode(),
                request.packSizeValue(),
                request.packSizeUnit(),
                request.servingDeclaredText(),
                request.nutrients().stream()
                        .map(n -> new NutrientContribution(n.nutrientCode(), n.amountPer100(), n.amountPerServing()))
                        .toList(),
                request.ingredientsRawText(),
                request.allergenCodes());
    }
}
