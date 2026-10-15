package com.familyfood.audit;

import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.platform.logging.CorrelationId;
import com.familyfood.platform.logging.SensitiveData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the append-only audit trail.
 *
 * <p>Three design points that matter:
 *
 * <p><b>Metadata is scrubbed on the way in.</b> The audit trail is a long-retention store
 * (7 years) and is therefore the worst possible place for a health value to land. Any key
 * matching the sensitive deny-list is redacted before the row is written — the same list
 * the log masker uses, so the two cannot drift apart.
 *
 * <p><b>Denials are audited as loudly as successes, and survive the rollback of the request
 * they describe.</b> A refused attempt to read a child profile is precisely the event a later
 * investigation needs (Constitution §33), and it is precisely the case where the surrounding
 * transaction rolls back. So a DENIED or ERROR event is written in its own transaction,
 * immediately.
 *
 * <p><b>A success is recorded only if it happened.</b> Inside a read-write business
 * transaction a SUCCESS event is written after that transaction commits, so a write that is
 * later rolled back does not leave a "recorded" event describing something that never
 * happened. Inside a read-only transaction (every cross-member read, D-038/D-044) — or with
 * no transaction at all — it is written straight away in its own transaction.
 *
 * <p><i>Why this is done programmatically.</i> An earlier version put
 * {@code @Transactional(REQUIRES_NEW)} on {@code write()} and reached it from
 * {@code success()}/{@code denied()} on {@code this}. Spring applies that annotation only to
 * calls that come through the proxy, so the self-invocation silently ran in the caller's
 * transaction: denials vanished with the rolled-back request, and reads audited inside a
 * read-only transaction were never persisted at all (found in the W1 review, D-042). A
 * {@link TransactionTemplate} does not depend on how it is called.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    public AuditService(AuditEventRepository repository,
                        Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(AuditAction action, String resourceType, UUID resourceId,
                       UUID actorUserId, UUID householdId, AuditOutcome outcome,
                       Map<String, Object> metadata) {
        write(action, resourceType, resourceId, actorUserId, null, householdId, null, outcome, metadata);
    }

    public void success(AuditAction action, String resourceType, UUID resourceId,
                        UUID actorUserId, UUID householdId) {
        write(action, resourceType, resourceId, actorUserId, null, householdId, null,
                AuditOutcome.SUCCESS, Map.of());
    }

    public void denied(AuditAction action, String resourceType, UUID resourceId,
                       UUID actorUserId, UUID householdId, String reason) {
        write(action, resourceType, resourceId, actorUserId, null, householdId, null,
                AuditOutcome.DENIED, Map.of("reason", reason));
    }

    /**
     * A success that touched someone's health data (D-044). {@code subjectMemberId} is whose
     * data it was, which is what lets that person see the event in their access log.
     */
    public void successForSubject(AuditAction action, String resourceType, UUID resourceId,
                                  UUID actorUserId, UUID householdId, UUID subjectMemberId,
                                  Map<String, Object> metadata) {
        write(action, resourceType, resourceId, actorUserId, null, householdId, subjectMemberId,
                AuditOutcome.SUCCESS, metadata);
    }

    /** A refused attempt on someone's health data, attributed to that person (D-044). */
    public void deniedForSubject(AuditAction action, String resourceType, UUID resourceId,
                                 UUID actorUserId, UUID householdId, UUID subjectMemberId,
                                 String reason) {
        write(action, resourceType, resourceId, actorUserId, null, householdId, subjectMemberId,
                AuditOutcome.DENIED, Map.of("reason", reason));
    }

    /** The pre-W1 signature, kept for callers that have no subject to attribute. */
    public void write(AuditAction action, String resourceType, UUID resourceId,
                      UUID actorUserId, String actorRole, UUID householdId,
                      AuditOutcome outcome, Map<String, Object> metadata) {
        write(action, resourceType, resourceId, actorUserId, actorRole, householdId, null,
                outcome, metadata);
    }

    public void write(AuditAction action, String resourceType, UUID resourceId,
                      UUID actorUserId, String actorRole, UUID householdId, UUID subjectMemberId,
                      AuditOutcome outcome, Map<String, Object> metadata) {
        AuditEvent event;
        try {
            event = AuditEvent.of(
                    clock.instant(), actorUserId, actorRole, CorrelationId.current(),
                    action, resourceType, resourceId, householdId, subjectMemberId, outcome,
                    scrub(metadata));
        } catch (RuntimeException e) {
            log.error("audit_event_build_failed action={} resourceType={}", action, resourceType, e);
            return;
        }

        if (outcome == AuditOutcome.SUCCESS && insideWritableTransaction()) {
            // Recorded only once the thing it describes has actually been committed.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    persist(event);
                }
            });
            return;
        }
        persist(event);
    }

    /**
     * Its own transaction, always. Also the only safe way to write from {@code afterCommit},
     * where the finished transaction's resources are still bound to the thread.
     */
    private void persist(AuditEvent event) {
        try {
            ownTransaction.executeWithoutResult(status -> repository.save(event));
        } catch (RuntimeException e) {
            // An audit write must never take down the request it is describing, but a
            // silent failure would be worse than useless — surface it loudly.
            log.error("audit_write_failed action={} resourceType={}",
                    event.getAction(), event.getResourceType(), e);
        }
    }

    private static boolean insideWritableTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    /**
     * Redacts any denied key before persistence. Deliberately shallow-plus-one-level:
     * audit metadata is small and flat by convention, and an unbounded walk over
     * arbitrary nesting invites a denial-of-service on a hot path.
     */
    private static Map<String, Object> scrub(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (SensitiveData.isDenied(key)) {
                out.put(key, SensitiveData.REDACTED);
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> inner = new LinkedHashMap<>();
                nested.forEach((k, v) -> inner.put(String.valueOf(k),
                        SensitiveData.isDenied(String.valueOf(k)) ? SensitiveData.REDACTED : v));
                out.put(key, inner);
            } else {
                out.put(key, value);
            }
        });
        return out;
    }
}
