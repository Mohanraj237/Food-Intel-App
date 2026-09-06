package com.familyfood.identity.api;

import com.familyfood.identity.application.AuthenticationService.AuthResult;

import java.time.Instant;
import java.util.UUID;

/**
 * The session handed back to a client.
 *
 * <p>Carries no profile, household or role data. Those are fetched through their own
 * consent-checked endpoints, so that a stale session cannot carry a stale membership or a
 * withdrawn consent (D-018).
 */
public record AuthResponse(
        UUID accountId,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(
                result.accountId(),
                result.accessToken(),
                result.accessTokenExpiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt());
    }
}
