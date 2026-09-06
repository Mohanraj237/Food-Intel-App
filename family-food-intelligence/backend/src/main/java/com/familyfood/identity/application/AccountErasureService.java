package com.familyfood.identity.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.identity.domain.UserAccount;
import com.familyfood.identity.domain.UserAccount.AccountStatus;
import com.familyfood.identity.infra.UserAccountRepository;
import com.familyfood.platform.config.AppProperties;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Account deletion (NFR-09 / AC-9.3), split into the two phases {@code user_account}'s own
 * schema already anticipated (see its {@code deleted_at} column comment and {@code
 * familyfood.privacy.erasure-grace-period}):
 *
 * <ol>
 *   <li>{@link #requestDeletion} — immediate. Blocks sign-in, ends every live session, and
 *       records the request. Reversible in principle during the grace period, but no
 *       "cancel" endpoint exists to act on that: once {@code isUsable()} is false the
 *       account cannot sign back in to ask for one, so a grace period without a cancel path
 *       is honestly just a delay, not an undo — flagged in the final report, not solved
 *       here (out of this session's scope: it is a new UX flow, not a data-lifecycle gap).
 *   <li>{@link #eraseIdentityRow} — the scheduled hard-erasure step, run by {@code
 *       datarights.application.AccountErasureScheduler} once the grace period elapses.
 *       Anonymises this module's own row; {@code datarights.application.DataRightsService}
 *       is the one place that also erases the account's household/profile/capture data in
 *       the same transaction, because this module has no dependency on any of those (see
 *       its own class comment).
 * </ol>
 *
 * <p>This service never deletes the {@code user_account} row itself — {@code
 * consent_record.granted_by_user_id}, {@code household_member.user_account_id} and {@code
 * audit_event.actor_user_id} may still reference it with no {@code ON DELETE} action (V3's
 * own schema comments explain why), so the row must keep existing for referential integrity.
 * Erasure overwrites its identifying fields instead, exactly like {@code audit_event}'s own
 * documented "pseudonymise the actor, don't remove the record" treatment.
 */
@Service
public class AccountErasureService {

    private final UserAccountRepository accounts;
    private final RefreshTokenRevoker revoker;
    private final PasswordHasher passwordHasher;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public AccountErasureService(UserAccountRepository accounts,
                                 RefreshTokenRevoker revoker,
                                 PasswordHasher passwordHasher,
                                 AuditService audit,
                                 AppProperties properties,
                                 Clock clock) {
        this.accounts = accounts;
        this.revoker = revoker;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return the instant hard erasure becomes eligible to run (request time + grace
     *         period) — surfaced to the caller so the API response can say when.
     */
    @Transactional
    public Instant requestDeletion(UUID accountId) {
        Instant now = clock.instant();
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> AppException.notFound("UserAccount"));

        account.markDeletionRequested(now);
        accounts.save(account);

        // Ends every session immediately — a request to delete an account is also, at
        // minimum, a request to stop trusting whatever session made it (and any other
        // live session for the same account).
        int revoked = revoker.revokeAllForAccount(accountId, now, "ACCOUNT_DELETION_REQUESTED");

        audit.record(AuditAction.ACCOUNT_DELETION_REQUESTED, "UserAccount", accountId,
                accountId, null, AuditOutcome.SUCCESS, Map.of("sessionsRevoked", revoked));

        return now.plus(properties.privacy().erasureGracePeriod());
    }

    /** The non-secret fields of an account, for data export (AC-9.2). Never the password hash. */
    @Transactional(readOnly = true)
    public AccountSummary getSummary(UUID accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> AppException.notFound("UserAccount"));
        return new AccountSummary(account.getId(), account.getEmail(),
                account.getCreatedAt(), account.getStatus().name());
    }

    public record AccountSummary(UUID accountId, String email, Instant createdAt, String status) {}

    /** Accounts whose grace period has elapsed and are still waiting on the hard-erasure step. */
    @Transactional(readOnly = true)
    public List<UUID> findAccountsDueForErasure() {
        Instant cutoff = clock.instant().minus(properties.privacy().erasureGracePeriod());
        return accounts.findByStatusAndDeletedAtBefore(AccountStatus.DELETION_REQUESTED, cutoff)
                .stream().map(UserAccount::getId).toList();
    }

    /**
     * The identity-module-only half of hard erasure. Must run in the same transaction as
     * {@code datarights.application.DataRightsService}'s household/profile/capture erasure
     * so the two either both commit or both roll back — this method does not commit on its
     * own and carries no {@code @Transactional} of its own propagation boundary beyond
     * {@code REQUIRED} (the default), so it simply joins whatever transaction the caller is
     * already in.
     */
    @Transactional
    public void eraseIdentityRow(UUID accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR,
                        Map.of("reason", "account_missing_during_erasure")));

        String erasedEmail = "erased-" + accountId + "@erased.invalid";
        String unusableHash = passwordHasher.hash(UUID.randomUUID().toString().toCharArray());
        account.eraseIdentity(erasedEmail, unusableHash, clock.instant());
        accounts.save(account);

        audit.record(AuditAction.ACCOUNT_DELETED, "UserAccount", accountId, accountId, null,
                AuditOutcome.SUCCESS, Map.of());
    }
}
