package com.familyfood.access.application;

import com.familyfood.access.domain.Relationship;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditTrail;
import com.familyfood.audit.AuditTrail.SubjectAccessEntry;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "Who has looked at my health data?" (D-044).
 *
 * <p>The trust feature that makes sharing safe to offer: a person can grant their spouse their
 * activity, or their son their medicines, precisely because they can see afterwards what was
 * done with it. The subject sees their own log; a guardian sees a child's. Nobody else — a
 * caregiver cannot read the log of the person they care for, because the log is about them.
 *
 * <p>Reads from the audit trail, which it cannot alter, and shows a person-facing summary: who
 * (their household label), what kind of access, which scope, and when. It never shows audit
 * metadata, which is written for investigators.
 */
@Service
public class AccessLogService {

    /** Long enough to notice a pattern, short enough to be a cheap indexed read. */
    private static final Duration WINDOW = Duration.ofDays(90);
    private static final int DEFAULT_LIMIT = 100;

    /**
     * The audit actions a person-facing log shows, and how each reads to that person. The
     * strict {@code CHILD_DATA_ACCESSED} duplicate of a read is left out: the same read is also
     * recorded as {@code HEALTH_DATA_ACCESSED}, and showing both would double every entry.
     */
    private static final Map<AuditAction, Kind> SHOWN = Map.ofEntries(
            Map.entry(AuditAction.HEALTH_DATA_ACCESSED, Kind.VIEWED),
            Map.entry(AuditAction.HEALTH_METRIC_RECORDED, Kind.RECORDED),
            Map.entry(AuditAction.MEAL_LOGGED, Kind.RECORDED),
            Map.entry(AuditAction.MEAL_UPDATED, Kind.RECORDED),
            Map.entry(AuditAction.GOAL_CREATED, Kind.RECORDED),
            Map.entry(AuditAction.GOAL_ENDED, Kind.RECORDED),
            Map.entry(AuditAction.HABIT_CREATED, Kind.RECORDED),
            Map.entry(AuditAction.HABIT_UPDATED, Kind.RECORDED),
            Map.entry(AuditAction.HABIT_ARCHIVED, Kind.RECORDED),
            Map.entry(AuditAction.HABIT_REACTIVATED, Kind.RECORDED),
            Map.entry(AuditAction.HABIT_COMPLETION_RECORDED, Kind.RECORDED),
            Map.entry(AuditAction.HABIT_COMPLETION_REMOVED, Kind.RECORDED),
            Map.entry(AuditAction.HEALTH_DATA_ACCESS_DENIED, Kind.REFUSED),
            Map.entry(AuditAction.CHILD_DATA_ACCESS_DENIED, Kind.REFUSED),
            Map.entry(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE, Kind.REFUSED));

    /** What happened, in the terms a person thinks in. */
    public enum Kind { VIEWED, RECORDED, REFUSED }

    private final HealthAccessPolicy policy;
    private final AuditTrail auditTrail;
    private final HouseholdAccess households;
    private final Clock clock;

    public AccessLogService(HealthAccessPolicy policy, AuditTrail auditTrail,
                            HouseholdAccess households, Clock clock) {
        this.policy = policy;
        this.auditTrail = auditTrail;
        this.households = households;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AccessLogEntry> accessLog(UUID callerAccountId, UUID subjectMemberId, Integer limit) {
        AccessDecision decision = policy.decide(callerAccountId, subjectMemberId);
        if (decision.relationship() != Relationship.SELF && decision.relationship() != Relationship.GUARDIAN) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<SubjectAccessEntry> events = auditTrail.accessToSubject(
                decision.subjectId(), callerAccountId, clock.instant().minus(WINDOW),
                limit == null ? DEFAULT_LIMIT : limit);

        Map<UUID, Optional<MemberSummary>> actors = new HashMap<>();
        return events.stream()
                .filter(e -> SHOWN.containsKey(e.action()))
                .map(e -> {
                    Optional<MemberSummary> actor = e.actorAccountId() == null
                            ? Optional.empty()
                            : actors.computeIfAbsent(e.actorAccountId(), account -> households
                                    .findActiveMembership(decision.householdId(), account)
                                    .flatMap(m -> households.findMemberSummary(m.id())));
                    Kind kind = e.outcome() == AuditOutcome.DENIED ? Kind.REFUSED : SHOWN.get(e.action());
                    return new AccessLogEntry(e.id(), e.occurredAt(),
                            actor.map(MemberSummary::id).orElse(null),
                            actor.map(MemberSummary::displayLabel).orElse(null),
                            kind, e.resourceType(), e.scope(), e.relationship());
                })
                .toList();
    }

    /**
     * @param actorMemberId {@code null} when the actor is no longer in the household (or was a
     *                      system process) — shown as such rather than hidden
     * @param scope         the scope(s) the access concerned, when recorded
     */
    public record AccessLogEntry(UUID id, Instant occurredAt, UUID actorMemberId, String actorLabel,
                                 Kind kind, String resourceType, String scope, String relationship) {}
}
