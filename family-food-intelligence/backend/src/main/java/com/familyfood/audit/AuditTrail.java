package com.familyfood.audit;

import com.familyfood.audit.AuditEvent.AuditOutcome;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The published read side of the audit trail. Other modules read audit events through this
 * class and never through {@link AuditEventRepository}, for the same reason every other
 * module's repositories are private to it.
 *
 * <p>It performs no authorization: the caller decides whose trail may be shown to whom (the
 * access module shows a person their own trail, or a guardian a child's). It returns only the
 * fields a person-facing log needs — never metadata, which is written for investigators and
 * may carry reasons or internal identifiers.
 */
@Service
public class AuditTrail {

    /** A person-facing page should not be able to request an unbounded scan of the trail. */
    private static final int MAX_LIMIT = 200;

    private final AuditEventRepository repository;

    public AuditTrail(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SubjectAccessEntry> accessToSubject(UUID subjectMemberId,
                                                    UUID excludeActorAccountId,
                                                    Instant since,
                                                    int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        return repository.findBySubjectExcludingActor(
                        subjectMemberId, excludeActorAccountId, since, PageRequest.of(0, bounded))
                .stream()
                .map(e -> new SubjectAccessEntry(e.getId(), e.getOccurredAt(), e.getActorUserId(),
                        e.getAction(), e.getResourceType(), e.getOutcome(),
                        stringOrNull(e.getMetadata().get("scope")),
                        stringOrNull(e.getMetadata().get("relationship"))))
                .toList();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * @param scope        the access scope the event concerned, when the writer recorded one
     * @param relationship how the actor was related to the subject at the time (CAREGIVER, …)
     */
    public record SubjectAccessEntry(UUID id, Instant occurredAt, UUID actorAccountId,
                                     AuditAction action, String resourceType, AuditOutcome outcome,
                                     String scope, String relationship) {}
}
