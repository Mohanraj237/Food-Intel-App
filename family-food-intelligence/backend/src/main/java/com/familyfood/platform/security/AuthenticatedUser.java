package com.familyfood.platform.security;

import java.util.UUID;

/**
 * The authenticated principal.
 *
 * <p>Carries the account id and nothing else. In particular it does <em>not</em> carry a
 * household id or a role: those are resolved from the database per request by the
 * permission layer. Putting them in the token would mean a stale token could still assert
 * a membership that has since been revoked, and it would make the client's copy of the
 * token an authorization input — which is exactly the IDOR shape this design forbids
 * (Constitution §32).
 */
public record AuthenticatedUser(UUID accountId, String tokenId) {

    public AuthenticatedUser {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
    }
}
