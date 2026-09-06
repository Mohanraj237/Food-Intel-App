package com.familyfood.household.api;

import com.familyfood.household.api.AddHouseholdMemberRequest.MemberKind;
import com.familyfood.household.application.HouseholdService;
import com.familyfood.household.application.HouseholdService.InvitationIssued;
import com.familyfood.household.domain.Household;
import com.familyfood.household.domain.HouseholdMember;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Household, membership, invitation and guardian-link endpoints.
 *
 * <p>Thin by design: bind, extract the caller from {@link CurrentUser}, delegate, map the
 * result. The caller's account id never comes from the request — {@code householdId} and
 * {@code memberId} path segments name a target resource to be authorized against, not the
 * caller's own identity (Constitution §36; Gate-2 IDOR posture).
 */
@RestController
@RequestMapping("/api/v1/households")
@Tag(name = "Households")
public class HouseholdController {

    private final HouseholdService householdService;
    private final CurrentUser currentUser;

    public HouseholdController(HouseholdService householdService, CurrentUser currentUser) {
        this.householdService = householdService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Create a household",
               description = "Creates a household with the caller as its OWNER. A "
                           + "household of one is a fully valid, permanent state.")
    public ResponseEntity<HouseholdResponse> create(@Valid @RequestBody CreateHouseholdRequest request) {
        UUID callerId = currentUser.require().accountId();
        Household household = householdService.createHousehold(
                callerId, request.name(), request.ownerDisplayLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(HouseholdResponse.from(household));
    }

    @GetMapping
    @Operation(summary = "List the households the caller is currently an active member of")
    public List<HouseholdSummaryResponse> listMine() {
        UUID callerAccountId = currentUser.require().accountId();
        return householdService.listMyHouseholds(callerAccountId).stream()
                .map(HouseholdSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{householdId}/members")
    @Operation(summary = "List live members of a household",
               description = "Only a live member of the household may list it.")
    public List<MemberResponse> listMembers(@PathVariable UUID householdId) {
        UUID callerId = currentUser.require().accountId();
        return householdService.listMembers(callerId, householdId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PostMapping("/{householdId}/members")
    @Operation(summary = "Invite an adult, or add a child",
               description = "ADULT creates only an invitation (D-002) — no member row, "
                           + "profile or consent exists for them until they accept with "
                           + "their own account. CHILD creates a dependent member and a "
                           + "guardian link from the caller, immediately.")
    public ResponseEntity<?> addMember(@PathVariable UUID householdId,
                                       @Valid @RequestBody AddHouseholdMemberRequest request) {
        UUID callerId = currentUser.require().accountId();

        if (request.memberType() == MemberKind.ADULT) {
            InvitationIssued issued = householdService.inviteAdult(callerId, householdId, request.email());
            return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(issued));
        }

        HouseholdMember child = householdService.addChild(callerId, householdId,
                request.displayLabel(), request.relationship(), request.authorityBasis());
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(child));
    }

    @DeleteMapping("/{householdId}/members/{memberId}")
    @Operation(summary = "Remove a member, or leave",
               description = "The OWNER may remove any other member. Any member may "
                           + "remove themselves.")
    public ResponseEntity<Void> removeMember(@PathVariable UUID householdId,
                                             @PathVariable UUID memberId) {
        UUID callerId = currentUser.require().accountId();
        householdService.removeMember(callerId, householdId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept a household invitation with the caller's own account")
    public MemberResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        UUID callerId = currentUser.require().accountId();
        HouseholdMember member = householdService.acceptInvitation(
                callerId, request.token(), request.displayLabel());
        return MemberResponse.from(member);
    }

    @PostMapping("/{householdId}/invitations/{invitationId}/revoke")
    @Operation(summary = "Revoke a pending invitation")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID householdId,
                                                 @PathVariable UUID invitationId) {
        UUID callerId = currentUser.require().accountId();
        householdService.revokeInvitation(callerId, householdId, invitationId);
        return ResponseEntity.noContent().build();
    }
}
