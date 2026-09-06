package com.familyfood.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An opaque refresh token, stored only as a hash.
 *
 * <p>Rotation with reuse detection: refreshing revokes the presented token and links it
 * to its replacement. If an already-revoked token is presented again, that is evidence of
 * theft — the whole chain for that account is revoked rather than just the one token.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent_hash")
    private String userAgentHash;

    @Column(name = "ip_hash")
    private String ipHash;

    protected RefreshToken() {
        // JPA
    }

    public static RefreshToken issue(UUID userAccountId, String tokenHash, Instant now,
                                     Instant expiresAt, String userAgentHash, String ipHash) {
        RefreshToken token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.userAccountId = userAccountId;
        token.tokenHash = tokenHash;
        token.issuedAt = now;
        token.expiresAt = expiresAt;
        token.userAgentHash = userAgentHash;
        token.ipHash = ipHash;
        return token;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }

    public void replaceWith(UUID newTokenId, Instant now) {
        revoke(now, "ROTATED");
        this.replacedById = newTokenId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
