package com.familyfood.consent.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.ConsentStatus;
import com.familyfood.consent.infra.ConsentRecordRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single authority every module that touches a subject's health or personal data must
 * consult before doing so (D-018). {@code scoring}, {@code capture} and {@code activity}
 * do not exist yet, but this is the boundary they will call across — never the other way;
 * see {@code ModuleBoundaryTest#consentMustNotDependOnGatedModules}. This class has no
 * controller: it is a library other modules call directly, not an HTTP endpoint.
 *
 * <p>{@link ConsentRecord#permitsProcessing(Instant)} is the sole authority on "is this
 * currently valid." This gate does nothing but call it, translate a "no" into the right
 * {@link AppException}, and audit the denial — a refusal is exactly the event a later
 * investigation needs (Constitution §33).
 *
 * <p><b>"Never consented" vs. "withdrawn."</b> These are different truths, and a caller
 * that wants to react differently (e.g. show a first-time consent prompt vs. a "you
 * withdrew this" notice) needs to tell them apart cheaply. {@link ConsentRecordRepository
 * #findHistory} exists for exactly this purpose and is documented as never used to decide
 * permission, only to explain a denial. This gate looks at the single most recent record
 * for the pair:
 *
 * <ul>
 *   <li>no record at all, or the most recent record's status is anything other than
 *       {@code WITHDRAWN} (including a stale {@code ACTIVE} row whose {@code expiresAt}
 *       has simply passed) &rarr; {@link ErrorCode#CONSENT_REQUIRED};
 *   <li>the most recent record's status is {@code WITHDRAWN} &rarr;
 *       {@link ErrorCode#CONSENT_WITHDRAWN}.
 * </ul>
 */
@Component
public class ConsentGate {

    private static final String RESOURCE_TYPE = "ConsentRecord";

    private final ConsentRecordRepository consentRecords;
    private final AuditService audit;
    private final Clock clock;

    public ConsentGate(ConsentRecordRepository consentRecords, AuditService audit, Clock clock) {
        this.consentRecords = consentRecords;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Throws unless an ACTIVE, unexpired consent record exists for this subject and
     * purpose. Returns normally (permits processing) otherwise.
     */
    public void require(UUID subjectMemberId, String purposeCode) {
        Instant now = clock.instant();
        Optional<ConsentRecord> active = consentRecords.findActive(subjectMemberId, purposeCode);
        if (active.isPresent() && active.get().permitsProcessing(now)) {
            return;
        }

        ErrorCode code = mostRecentStatus(subjectMemberId, purposeCode) == ConsentStatus.WITHDRAWN
                ? ErrorCode.CONSENT_WITHDRAWN
                : ErrorCode.CONSENT_REQUIRED;

        // actorUserId/householdId are unknown here on purpose: the gate is called by a
        // module processing a subject's data, not by an authenticated HTTP request for
        // that subject, so there is no caller identity to attribute the denial to. The
        // subject is still recorded as the resource, which is what an investigation needs.
        audit.record(AuditAction.CONSENT_DENIED_AT_PROCESSING, RESOURCE_TYPE, subjectMemberId,
                null, null, AuditOutcome.DENIED,
                Map.of("purposeCode", purposeCode, "reason", code.name()));

        throw new AppException(code);
    }

    /**
     * Whether processing for this purpose is currently permitted, without throwing and without
     * auditing a denial.
     *
     * <p>For callers that must <em>choose</em> a path rather than refuse a request — a family
     * meal deciding whether a member's share can be written to their log now or has to wait
     * (D-041, D-048), or an overview deciding whether a member's data may be shown to someone
     * else. Neither of those is a refused attempt, so neither belongs in the audit trail as
     * one. Anything that is about to process data on the strength of the answer must still
     * call {@link #require} at the point of processing.
     */
    public boolean isActive(UUID subjectMemberId, String purposeCode) {
        return consentRecords.findActive(subjectMemberId, purposeCode)
                .map(active -> active.permitsProcessing(clock.instant()))
                .orElse(false);
    }

    private ConsentStatus mostRecentStatus(UUID subjectMemberId, String purposeCode) {
        List<ConsentRecord> history = consentRecords.findHistory(subjectMemberId, purposeCode);
        return history.isEmpty() ? null : history.get(0).getStatus();
    }
}
