package com.familyfood.household.api;

import com.familyfood.household.application.HouseholdService.InvitationIssued;

import java.time.Instant;
import java.util.UUID;

/**
 * @param token the raw invitation token. Returned exactly once, here — it is never
 *              stored and can never be retrieved again after this response.
 */
public record InvitationResponse(UUID invitationId, UUID householdId, String token, Instant expiresAt) {

    public static InvitationResponse from(InvitationIssued issued) {
        return new InvitationResponse(
                issued.invitationId(), issued.householdId(), issued.rawToken(), issued.expiresAt());
    }
}
