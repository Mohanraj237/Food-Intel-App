package com.familyfood.datarights.api;

import com.familyfood.datarights.application.DataRightsService;
import com.familyfood.identity.application.AccountErasureService;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Data rights: export and account/child erasure (NFR-09, CH-008, FR-010's erasure gap).
 *
 * <p>Thin by design, like every other controller: bind, extract the caller from {@link
 * CurrentUser}, delegate, map the result. The account-level endpoints never accept a
 * target account id from the request — there is no path or body field for one, so there is
 * nothing for an attacker to substitute (a stronger guarantee than an authorization check
 * that could be gotten wrong, the same reasoning {@code AuthController} already applies to
 * {@code logout}/{@code logout-all}). The child-erasure endpoint's {@code householdId}/
 * {@code memberId} are targets to be authorized against, not the caller's identity —
 * {@link DataRightsService#eraseChild} enforces that.
 */
@RestController
@Tag(name = "Data rights")
public class DataRightsController {

    private final DataRightsService dataRights;
    private final AccountErasureService accountErasure;
    private final CurrentUser currentUser;

    public DataRightsController(DataRightsService dataRights,
                                AccountErasureService accountErasure,
                                CurrentUser currentUser) {
        this.dataRights = dataRights;
        this.accountErasure = accountErasure;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/account/export")
    @Operation(summary = "Export the caller's own data",
               description = "Every household the caller belongs to (their own membership, "
                           + "profile history, consent ledger and health record: "
                           + "observations, goals, habits with the days they were done, the "
                           + "meals they had a share of with only their own share, and what "
                           + "they shared with whom), every child they currently guard (same "
                           + "shape), and their own product contributions. Never another "
                           + "person's data (AC-9.2).")
    public AccountExportResponse export() {
        UUID accountId = currentUser.require().accountId();
        return AccountExportResponse.from(dataRights.exportMyData(accountId));
    }

    @PostMapping("/api/v1/account/deletion")
    @Operation(summary = "Request account deletion",
               description = "Immediate: blocks sign-in and ends every session right away. "
                           + "Personal data is erased after the grace period configured by "
                           + "familyfood.privacy.erasure-grace-period; consent_record and "
                           + "audit_event survive as evidence (AC-9.3). There is no "
                           + "cancellation endpoint — once requested, sign-in is blocked for "
                           + "the rest of the grace period and erasure proceeds.")
    public ResponseEntity<AccountDeletionResponse> requestDeletion() {
        UUID accountId = currentUser.require().accountId();
        Instant effectiveAt = accountErasure.requestDeletion(accountId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AccountDeletionResponse.requested(effectiveAt));
    }

    @PostMapping("/api/v1/households/{householdId}/members/{memberId}/erase")
    @Operation(summary = "Erase a child's data",
               description = "Only an active guardian of this specific child may call this. "
                           + "Distinct from removing/leaving a household (DELETE .../members/"
                           + "{memberId}), which only ends the membership: this erases the "
                           + "child's profile and health record (observations, goals, habits, "
                           + "their share of every meal) and scrubs their membership record. "
                           + "A meal others also ate survives without the child's share. consent_"
                           + "record and audit_event survive as evidence (AC-9.3). Never "
                           + "usable against an ADULT member — an adult erases their own "
                           + "data only through account deletion.")
    public ResponseEntity<Void> eraseChild(@PathVariable UUID householdId,
                                           @PathVariable UUID memberId) {
        UUID callerId = currentUser.require().accountId();
        dataRights.eraseChild(callerId, householdId, memberId);
        return ResponseEntity.noContent().build();
    }
}
