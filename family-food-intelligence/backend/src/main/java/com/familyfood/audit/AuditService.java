package com.familyfood.audit;

import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.platform.logging.CorrelationId;
import com.familyfood.platform.logging.SensitiveData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the append-only audit trail.
 *
 * <p>Two design points that matter:
 *
 * <p><b>Metadata is scrubbed on the way in.</b> The audit trail is a long-retention store
 * (7 years) and is therefore the worst possible place for a health value to land. Any key
 * matching the sensitive deny-list is redacted before the row is written — the same list
 * the log masker uses, so the two cannot drift apart.
 *
 * <p><b>Denials are audited as loudly as successes.</b> A refused attempt to read a child
 * profile is precisely the event a later investigation needs (Constitution §33), and an
 * audit trail that only records what worked is not an audit trail.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(AuditAction action, String resourceType, UUID resourceId,
                       UUID actorUserId, UUID householdId, AuditOutcome outcome,
                       Map<String, Object> metadata) {
        write(action, resourceType, resourceId, actorUserId, null, householdId, outcome, metadata);
    }

    public void success(AuditAction action, String resourceType, UUID resourceId,
                        UUID actorUserId, UUID householdId) {
        write(action, resourceType, resourceId, actorUserId, null, householdId,
                AuditOutcome.SUCCESS, Map.of());
    }

    public void denied(AuditAction action, String resourceType, UUID resourceId,
                       UUID actorUserId, UUID householdId, String reason) {
        write(action, resourceType, resourceId, actorUserId, null, householdId,
                AuditOutcome.DENIED, Map.of("reason", reason));
    }

    /**
     * Written in its own transaction so that an audit record survives the rollback of the
     * business transaction it describes. A denied or failed operation is exactly the case
     * where the surrounding transaction rolls back, and exactly the case we most need
     * recorded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditAction action, String resourceType, UUID resourceId,
                      UUID actorUserId, String actorRole, UUID householdId,
                      AuditOutcome outcome, Map<String, Object> metadata) {
        try {
            AuditEvent event = AuditEvent.of(
                    clock.instant(), actorUserId, actorRole, CorrelationId.current(),
                    action, resourceType, resourceId, householdId, outcome,
                    scrub(metadata));
            repository.save(event);
        } catch (RuntimeException e) {
            // An audit write must never take down the request it is describing, but a
            // silent failure would be worse than useless — surface it loudly.
            log.error("audit_write_failed action={} resourceType={}", action, resourceType, e);
        }
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
