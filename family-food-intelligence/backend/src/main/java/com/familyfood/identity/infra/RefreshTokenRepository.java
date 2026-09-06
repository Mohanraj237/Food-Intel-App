package com.familyfood.identity.infra;

import com.familyfood.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every live token for an account. Used on logout-everywhere, on password
     * change, and on refresh-token reuse detection, where a single revocation would leave
     * the attacker's chain alive.
     */
    @Modifying
    @Query("""
           update RefreshToken t
              set t.revokedAt = :now, t.revokedReason = :reason
            where t.userAccountId = :userAccountId
              and t.revokedAt is null
           """)
    int revokeAllForAccount(@Param("userAccountId") UUID userAccountId,
                            @Param("now") Instant now,
                            @Param("reason") String reason);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
