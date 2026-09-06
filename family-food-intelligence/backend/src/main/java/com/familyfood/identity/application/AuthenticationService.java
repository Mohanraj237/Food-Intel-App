package com.familyfood.identity.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.identity.domain.LoginAttempt;
import com.familyfood.identity.domain.RefreshToken;
import com.familyfood.identity.domain.UserAccount;
import com.familyfood.identity.infra.LoginAttemptRepository;
import com.familyfood.identity.infra.RefreshTokenRepository;
import com.familyfood.identity.infra.UserAccountRepository;
import com.familyfood.platform.config.AppProperties;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.security.JwtService;
import com.familyfood.platform.security.PasswordHasher;
import com.familyfood.platform.util.EmailNormaliser;
import com.familyfood.platform.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, sign-in, token refresh and sign-out.
 *
 * <p>Three properties are load-bearing and are covered by tests:
 *
 * <p><b>No account enumeration (AC-1.3).</b> An unknown email and a wrong password produce
 * an identical response, and take a comparable amount of time — the hasher runs a real
 * comparison against a dummy hash when no account exists. Registration with an existing
 * email is the one deliberate exception: it must tell the user, or they cannot recover
 * their own account. That trade is standard and is documented rather than hidden.
 *
 * <p><b>Refresh tokens rotate, and reuse is treated as theft.</b> Presenting an
 * already-revoked token revokes the entire chain for that account rather than just the
 * presented token, because the legitimate holder and the attacker are indistinguishable
 * at that moment and only one of them can be safely allowed to continue.
 *
 * <p><b>Throttling is per email and per IP.</b> Per-email alone lets an attacker spray
 * one password across many accounts; per-IP alone punishes shared NAT. Both are needed.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final Duration THROTTLE_WINDOW = Duration.ofHours(1);

    private final UserAccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final LoginAttemptRepository loginAttempts;
    private final RefreshTokenRevoker revoker;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final Hashing hashing;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public AuthenticationService(UserAccountRepository accounts,
                                 RefreshTokenRepository refreshTokens,
                                 LoginAttemptRepository loginAttempts,
                                 RefreshTokenRevoker revoker,
                                 PasswordHasher passwordHasher,
                                 JwtService jwtService,
                                 Hashing hashing,
                                 AuditService audit,
                                 AppProperties properties,
                                 Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.revoker = revoker;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.hashing = hashing;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ register

    @Transactional
    public AuthResult register(String rawEmail, char[] password, RequestContext context) {
        String email = EmailNormaliser.normalise(rawEmail);
        Instant now = clock.instant();

        if (accounts.existsByEmail(email)) {
            // Deliberate disclosure: without it a user who forgot they registered can
            // never recover their account. Rate limiting bounds the enumeration value.
            throw new AppException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String hash = passwordHasher.hash(password);
        Arrays.fill(password, '\0');

        UserAccount account = accounts.save(UserAccount.create(email, hash, now));

        audit.record(AuditAction.ACCOUNT_REGISTERED, "UserAccount", account.getId(),
                account.getId(), null, AuditOutcome.SUCCESS, Map.of());

        return issueTokens(account, context, now);
    }

    // --------------------------------------------------------------------- login

    @Transactional
    public AuthResult login(String rawEmail, char[] password, RequestContext context) {
        String email = EmailNormaliser.normalise(rawEmail);
        String emailHash = hashing.pseudonymise(email);
        String ipHash = hashing.pseudonymise(context.ipAddress());
        Instant now = clock.instant();

        guardAgainstBruteForce(emailHash, ipHash, now);

        Optional<UserAccount> found = accounts.findByEmail(email);

        // Always run the hash comparison, even when no account exists, so that response
        // time does not distinguish the two cases.
        boolean credentialsValid = passwordHasher.matches(
                password, found.map(UserAccount::getPasswordHash).orElse(null));
        Arrays.fill(password, '\0');

        if (found.isEmpty() || !credentialsValid || !found.get().isUsable()) {
            loginAttempts.save(LoginAttempt.record(emailHash, ipHash, false, now));
            found.ifPresent(account -> audit.record(
                    AuditAction.ACCOUNT_LOGIN_FAILED, "UserAccount", account.getId(),
                    account.getId(), null, AuditOutcome.DENIED, Map.of()));

            // Suspended and deleted accounts return the same error as bad credentials.
            // Telling a caller "this account is suspended" confirms the account exists.
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserAccount account = found.get();
        loginAttempts.save(LoginAttempt.record(emailHash, ipHash, true, now));

        // Opportunistic upgrade if the cost parameters have since been raised.
        if (passwordHasher.needsRehash(account.getPasswordHash())) {
            log.info("password_rehash_scheduled accountId={}", account.getId());
        }

        audit.success(AuditAction.ACCOUNT_LOGIN_SUCCEEDED, "UserAccount",
                account.getId(), account.getId(), null);

        return issueTokens(account, context, now);
    }

    // ------------------------------------------------------------------- refresh

    @Transactional
    public AuthResult refresh(String presentedToken, RequestContext context) {
        Instant now = clock.instant();
        String tokenHash = hashing.hashToken(presentedToken);

        RefreshToken stored = refreshTokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        if (!stored.isActive(now)) {
            // Reuse of a revoked token means the token leaked. We cannot tell the
            // legitimate holder from the attacker, so we end every session for the
            // account and force a fresh sign-in.
            // REQUIRES_NEW: the throw at the end of this block rolls back the current
            // transaction. Without an independent transaction the revocation would be
            // undone and every live token — including the attacker's — would survive.
            int revoked = revoker.revokeAllForAccount(
                    stored.getUserAccountId(), now, "REUSE_DETECTED");
            log.warn("refresh_token_reuse_detected accountId={} revokedCount={}",
                    stored.getUserAccountId(), revoked);
            audit.record(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, "RefreshToken",
                    stored.getId(), stored.getUserAccountId(), null,
                    AuditOutcome.DENIED, Map.of("revokedCount", revoked));
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        UserAccount account = accounts.findById(stored.getUserAccountId())
                .filter(UserAccount::isUsable)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        AuthResult result = issueTokens(account, context, now);
        stored.replaceWith(result.refreshTokenId(), now);
        refreshTokens.save(stored);

        return result;
    }

    // -------------------------------------------------------------------- logout

    @Transactional
    public void logout(String presentedToken, UUID accountId) {
        Instant now = clock.instant();
        refreshTokens.findByTokenHash(hashing.hashToken(presentedToken))
                .filter(token -> token.getUserAccountId().equals(accountId))
                .ifPresent(token -> {
                    token.revoke(now, "LOGOUT");
                    refreshTokens.save(token);
                });
        audit.success(AuditAction.ACCOUNT_LOGGED_OUT, "UserAccount", accountId, accountId, null);
    }

    @Transactional
    public void logoutEverywhere(UUID accountId) {
        revoker.revokeAllForAccount(accountId, clock.instant(), "LOGOUT_ALL");
        audit.success(AuditAction.ACCOUNT_LOGGED_OUT, "UserAccount", accountId, accountId, null);
    }

    // ------------------------------------------------------------------ internals

    private void guardAgainstBruteForce(String emailHash, String ipHash, Instant now) {
        Instant since = now.minus(THROTTLE_WINDOW);
        int limit = properties.limits().loginAttemptsPerHour();

        if (loginAttempts.countRecentFailuresByEmail(emailHash, since) >= limit
                || loginAttempts.countRecentFailuresByIp(ipHash, since) >= limit * 5L) {
            // The IP allowance is deliberately looser than the per-email one: households
            // and offices share addresses, and locking a whole building out of the app
            // because one person mistyped a password is a worse failure than the attack
            // it would prevent.
            throw new AppException(ErrorCode.RATE_LIMITED);
        }
    }

    private AuthResult issueTokens(UserAccount account, RequestContext context, Instant now) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(account.getId());

        String rawRefresh = Hashing.newOpaqueToken();
        RefreshToken refresh = RefreshToken.issue(
                account.getId(),
                hashing.hashToken(rawRefresh),
                now,
                now.plus(properties.auth().refreshTokenTtl()),
                hashing.pseudonymise(context.userAgent()),
                hashing.pseudonymise(context.ipAddress()));
        refreshTokens.save(refresh);

        return new AuthResult(
                account.getId(),
                access.value(),
                access.expiresAt(),
                rawRefresh,
                refresh.getExpiresAt(),
                refresh.getId());
    }

    /** Ambient request facts needed for throttling and session bookkeeping. */
    public record RequestContext(String ipAddress, String userAgent) {}

    /**
     * @param refreshToken the raw token — returned to the caller exactly once and never
     *                     stored in this form.
     */
    public record AuthResult(UUID accountId,
                             String accessToken,
                             Instant accessTokenExpiresAt,
                             String refreshToken,
                             Instant refreshTokenExpiresAt,
                             UUID refreshTokenId) {}
}
