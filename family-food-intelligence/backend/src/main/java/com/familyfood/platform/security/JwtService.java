package com.familyfood.platform.security;

import com.familyfood.platform.config.AppProperties;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies access tokens.
 *
 * <p>Access tokens are short-lived and stateless. Refresh tokens are <em>not</em> JWTs —
 * they are opaque random values whose hash is stored in {@code refresh_token}, so that
 * revocation is real rather than advisory. A stateless refresh token cannot be revoked,
 * which is unacceptable for a product holding health data.
 *
 * <p>The token deliberately carries no household, role or profile claim. Authorization is
 * resolved from the database on every request (Constitution §32).
 */
@Component
public class JwtService {

    private static final String CLAIM_TOKEN_ID = "jti";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        byte[] secret = Base64.getDecoder().decode(properties.auth().jwtSecret());
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "familyfood.auth.jwt-secret must decode to at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = properties.auth().jwtIssuer();
        this.accessTtl = properties.auth().accessTokenTtl();
        this.clock = clock;
    }

    public IssuedToken issueAccessToken(UUID accountId) {
        Instant now = clock.instant();
        Instant expiry = now.plus(accessTtl);
        String tokenId = UUID.randomUUID().toString();

        String jwt = Jwts.builder()
                .issuer(issuer)
                .subject(accountId.toString())
                .id(tokenId)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();

        return new IssuedToken(jwt, expiry);
    }

    /**
     * Verifies signature, issuer, type and expiry. Any failure is an
     * {@link AppException} carrying no detail about which check failed — a verbose
     * verifier is an oracle.
     */
    public AuthenticatedUser verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                // A refresh token presented as an access token, or a forged type.
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }
            UUID accountId = UUID.fromString(claims.getSubject());
            return new AuthenticatedUser(accountId, claims.get(CLAIM_TOKEN_ID, String.class));

        } catch (ExpiredJwtException e) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
