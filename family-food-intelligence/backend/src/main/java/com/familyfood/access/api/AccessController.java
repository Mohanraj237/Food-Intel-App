package com.familyfood.access.api;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.AccessGrantService;
import com.familyfood.access.application.AccessGrantView;
import com.familyfood.access.application.AccessLogService;
import com.familyfood.access.application.AccessLogService.AccessLogEntry;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Sharing, caregivers and the access log (D-044, D-045).
 *
 * <p>Thin: every rule lives in {@link AccessGrantService}, {@link HealthAccessPolicy} and
 * {@link AccessLogService}. The caller's identity comes from {@link CurrentUser}; path ids name
 * the resource to be authorized against, never the caller.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Access")
public class AccessController {

    private final HealthAccessPolicy policy;
    private final AccessGrantService grants;
    private final AccessLogService accessLog;
    private final CurrentUser currentUser;

    public AccessController(HealthAccessPolicy policy,
                            AccessGrantService grants,
                            AccessLogService accessLog,
                            CurrentUser currentUser) {
        this.policy = policy;
        this.grants = grants;
        this.accessLog = accessLog;
        this.currentUser = currentUser;
    }

    @GetMapping("/access/scopes")
    @Operation(summary = "Every scope a grant can name, and whether the product offers it yet",
               description = "A client builds its sharing screen from this list and never "
                           + "hardcodes it. A scope whose feature does not exist yet is listed "
                           + "with offered=false and must not be shown as a choice.")
    public List<ScopeResponse> scopes() {
        return Arrays.stream(AccessScope.values())
                .map(s -> new ScopeResponse(s.name(), s.kind().name(), s.isOffered()))
                .toList();
    }

    @GetMapping("/members/{memberId}/access")
    @Operation(summary = "The caller's relationship to a member and what they may see or do",
               description = "Lets a screen say \"Priya shares her activity with you\" instead of "
                           + "discovering the boundary through a 403.")
    public AccessResponse access(@PathVariable UUID memberId) {
        AccessDecision decision = policy.decide(currentUser.require().accountId(), memberId);
        return AccessResponse.from(decision);
    }

    @GetMapping("/members/{memberId}/access-grants")
    @Operation(summary = "What this member shares, and with whom (the member only)")
    public List<AccessGrantView> outgoing(@PathVariable UUID memberId) {
        return grants.listOutgoing(currentUser.require().accountId(), memberId);
    }

    @PostMapping("/members/{memberId}/access-grants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Share one scope of your own data with an adult in your household",
               description = "VIEW lets them see it; MANAGE lets them record and act on your "
                           + "behalf in that scope (a caregiver, D-045). Only the member may "
                           + "grant. Idempotent at the same level; a different level replaces "
                           + "the grant and keeps its history.")
    public AccessGrantView grant(@PathVariable UUID memberId, @Valid @RequestBody GrantAccessRequest request) {
        return grants.grant(currentUser.require().accountId(), memberId,
                request.audienceMemberId(), request.scope(), request.level());
    }

    @DeleteMapping("/members/{memberId}/access-grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop sharing. Takes effect on the other person's next request.")
    public void revoke(@PathVariable UUID memberId, @PathVariable UUID grantId) {
        grants.revoke(currentUser.require().accountId(), memberId, grantId);
    }

    @GetMapping("/me/shared-with-me")
    @Operation(summary = "What other household members have shared with the caller")
    public List<AccessGrantView> sharedWithMe() {
        return grants.listIncoming(currentUser.require().accountId());
    }

    @GetMapping("/members/{memberId}/access-log")
    @Operation(summary = "Who else viewed or recorded this member's health data, last 90 days",
               description = "The member themselves, or a guardian for a child. Refused attempts "
                           + "are included.")
    public List<AccessLogEntry> log(@PathVariable UUID memberId,
                                    @RequestParam(required = false) Integer limit) {
        return accessLog.accessLog(currentUser.require().accountId(), memberId, limit);
    }

    /* --------------------------------------------------------------------- shapes */

    public record GrantAccessRequest(@NotNull UUID audienceMemberId,
                                     @NotNull AccessScope scope,
                                     @NotNull AccessLevel level) {}

    public record ScopeResponse(String scope, String kind, boolean offered) {}

    public record ScopeLevel(String scope, String level) {}

    public record AccessResponse(UUID memberId, String relationship, List<ScopeLevel> scopes) {
        static AccessResponse from(AccessDecision decision) {
            return new AccessResponse(decision.subjectId(), decision.relationship().name(),
                    decision.levels().entrySet().stream()
                            .map(e -> new ScopeLevel(e.getKey().name(), e.getValue().name()))
                            .toList());
        }
    }
}
