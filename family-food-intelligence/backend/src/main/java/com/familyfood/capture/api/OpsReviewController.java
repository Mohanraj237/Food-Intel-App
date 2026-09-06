package com.familyfood.capture.api;

import com.familyfood.capture.application.ContributionService;
import com.familyfood.capture.application.ContributionService.ContributionDetailView;
import com.familyfood.capture.application.ContributionService.ContributionQueueEntry;
import com.familyfood.capture.domain.ContributionStatus;
import com.familyfood.identity.application.OperatorAccess;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The ops-review console backend (docs/product/post-mvp-backlog.md's "correction queue ops
 * console" item; system-architecture.md §6's "ops review ... never auto-published" step).
 * Deliberately small: a queue, a detail view, approve, reject — nothing else.
 *
 * <p>Every method re-checks {@link OperatorAccess} against the real database role on every
 * call ({@code ContributionService.requireOperator}) — never a client-side assumption, never
 * cached in the JWT (mirrors {@code AuthenticatedUser}'s own documented reasoning for
 * excluding household id/role from the token: a stale token must not be able to assert a
 * permission that has since been revoked). This route has no entry in {@code
 * SecurityConfig}'s permit-all list, so the coarse first gate is "signed in"; the real
 * authorization decision is the operator check inside the service.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "Ops review")
public class OpsReviewController {

    private final ContributionService contributionService;
    private final OperatorAccess operatorAccess;
    private final CurrentUser currentUser;

    public OpsReviewController(ContributionService contributionService,
                                OperatorAccess operatorAccess,
                                CurrentUser currentUser) {
        this.contributionService = contributionService;
        this.operatorAccess = operatorAccess;
        this.currentUser = currentUser;
    }

    @GetMapping("/access")
    @Operation(summary = "Whether the signed-in caller holds the ops-review role",
               description = "A UI affordance only (whether to show the ops nav entry) — "
                           + "every ops endpoint re-checks this server-side regardless of "
                           + "what this returns.")
    public AccessResponse access() {
        UUID accountId = currentUser.require().accountId();
        return new AccessResponse(operatorAccess.isOperator(accountId));
    }

    @GetMapping("/contributions")
    @Operation(summary = "The contribution review queue for one status, oldest first")
    public List<ContributionQueueEntry> queue(
            @RequestParam(name = "status", defaultValue = "PENDING") ContributionStatus status) {
        UUID accountId = currentUser.require().accountId();
        return contributionService.listQueue(accountId, status);
    }

    @GetMapping("/contributions/{id}")
    @Operation(summary = "Full detail for one contribution, for review")
    public ContributionDetailView detail(@PathVariable UUID id) {
        UUID accountId = currentUser.require().accountId();
        return contributionService.getDetail(accountId, id);
    }

    @PostMapping("/contributions/{id}/approve")
    @Operation(summary = "Approve a pending contribution",
               description = "Creates a real catalogue product/version from the submission "
                           + "and marks it APPROVED, in one transaction.")
    public ApprovalResponse approve(@PathVariable UUID id) {
        UUID accountId = currentUser.require().accountId();
        UUID productId = contributionService.approve(accountId, id);
        return new ApprovalResponse(productId);
    }

    @PostMapping("/contributions/{id}/reject")
    @Operation(summary = "Reject a pending contribution",
               description = "A reason is required; the contribution itself is retained, "
                           + "never deleted.")
    public void reject(@PathVariable UUID id, @RequestBody RejectRequest request) {
        UUID accountId = currentUser.require().accountId();
        contributionService.reject(accountId, id, request.reason());
    }

    public record AccessResponse(boolean isOperator) {
    }

    public record ApprovalResponse(UUID productId) {
    }

    public record RejectRequest(@NotBlank String reason) {
    }
}
