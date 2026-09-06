package com.familyfood.identity.application;

import com.familyfood.identity.infra.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes refresh tokens in a transaction of its own.
 *
 * <p>This exists because of a real defect the integration suite caught: revoking a
 * compromised token chain and then throwing to reject the request meant the throw rolled
 * back the revocation. The attacker's replay was refused, but every live token — including
 * the one the attacker had just obtained — stayed valid. The security response was
 * silently undone by the error response.
 *
 * <p>{@code REQUIRES_NEW} makes the revocation commit independently, so it survives the
 * rollback of the request that triggered it. A separate component rather than a
 * self-invoked method, because Spring's proxy-based transactions do not apply to a call
 * a bean makes to itself — which is exactly how this class of bug reappears.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /**
     * Ends every live session for an account. Returns how many were revoked, which the
     * caller records in the audit trail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForAccount(UUID accountId, Instant now, String reason) {
        return refreshTokens.revokeAllForAccount(accountId, now, reason);
    }
}
