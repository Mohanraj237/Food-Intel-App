package com.familyfood.consent.api;

import com.familyfood.consent.application.ConsentService;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Grant, withdraw and list a household member's consent ledger.
 *
 * <p>Thin by design (Constitution §36): bind, extract the caller's own identity from
 * {@link CurrentUser} — never from the path — delegate to {@link ConsentService}, map the
 * result. All authorization (D-001, D-002), purpose validation and policy-version
 * resolution happen inside the service.
 *
 * <p>{@code memberId} in the path is not an authorization input, exactly as in
 * {@code ProfileController}: the service decides access solely from {@code memberId} plus
 * the caller's own account id, resolved through the published {@code HouseholdAccess}
 * interface.
 *
 * <p>{@code GET /policies} is the one public route here (see {@code SecurityConfig}); every
 * other route requires an authenticated caller.
 */
@RestController
@RequestMapping("/api/v1/consent")
@Tag(name = "Consent")
public class ConsentController {

    private final ConsentService consentService;
    private final CurrentUser currentUser;

    public ConsentController(ConsentService consentService, CurrentUser currentUser) {
        this.consentService = consentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/policies")
    @Operation(summary = "List the current policy version for every active consent purpose",
               description = "Public, unauthenticated reference data — the text a person is "
                           + "shown before granting any purpose.")
    public List<ConsentPolicyResponse> policies() {
        return consentService.listCurrentPolicies().stream()
                .map(ConsentPolicyResponse::from)
                .toList();
    }

    @PostMapping("/members/{memberId}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Grant consent for a purpose on behalf of a household member",
               description = "An adult may grant only for themselves; a child's purpose may "
                           + "be granted only by one of their guardians, always recorded as "
                           + "GUARDIAN_ATTESTATION. Any existing active grant for the same "
                           + "purpose is withdrawn first, so at most one stays active.")
    public ConsentRecordResponse grant(@PathVariable UUID memberId,
                                       @Valid @RequestBody GrantConsentRequest request) {
        return ConsentRecordResponse.from(consentService.grant(
                currentUser.require().accountId(), memberId,
                request.purposeCode(), request.evidence()));
    }

    @DeleteMapping("/members/{memberId}/grants/{purposeCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw an active consent grant",
               description = "Idempotent: withdrawing a purpose with no active grant "
                           + "succeeds without effect.")
    public void withdraw(@PathVariable UUID memberId, @PathVariable String purposeCode) {
        consentService.withdraw(currentUser.require().accountId(), memberId, purposeCode);
    }

    @GetMapping("/members/{memberId}/grants")
    @Operation(summary = "List the consent ledger for a household member",
               description = "Every record for the subject, any status. Evidence is never "
                           + "included in this listing.")
    public List<ConsentRecordResponse> list(@PathVariable UUID memberId) {
        return consentService.list(currentUser.require().accountId(), memberId).stream()
                .map(ConsentRecordResponse::from)
                .toList();
    }
}
