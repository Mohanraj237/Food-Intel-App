package com.familyfood.access.application;

import com.familyfood.access.domain.AccessGrant;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.infra.AccessGrantRepository;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Making, changing and withdrawing grants (D-044, D-045).
 *
 * <p>The rules, each of which is a sentence a person could be told:
 * <ul>
 *   <li><b>Only you share your data.</b> Not a guardian on an adult's behalf, not the household
 *       owner, not a caregiver onward (D-002).</li>
 *   <li><b>A child's data is never shared this way.</b> A child has no account, so there is no
 *       "self" to grant; their guardians reach their data through the guardian link only.</li>
 *   <li><b>You share with an adult in your household.</b> Not with a child, not with yourself,
 *       not with anyone outside it.</li>
 *   <li><b>Changing your mind is immediate.</b> A revocation takes effect on the other person's
 *       next request, and the record of who could see what, and when, is kept.</li>
 * </ul>
 */
@Service
public class AccessGrantService {

    private static final String RESOURCE_TYPE = "HEALTH_ACCESS_GRANT";
    private static final String ADULT_MEMBER_TYPE = "ADULT";

    private final AccessGrantRepository grants;
    private final HouseholdAccess households;
    private final AuditService audit;
    private final Clock clock;

    public AccessGrantService(AccessGrantRepository grants,
                              HouseholdAccess households,
                              AuditService audit,
                              Clock clock) {
        this.grants = grants;
        this.households = households;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Grants {@code level} on {@code scope} to {@code audienceMemberId}. Idempotent at the same
     * level. At a different level, the live grant is revoked and a new one made, so the history
     * shows exactly when someone went from seeing to managing.
     */
    @Transactional
    public AccessGrantView grant(UUID callerAccountId,
                                 UUID subjectMemberId,
                                 UUID audienceMemberId,
                                 AccessScope scope,
                                 AccessLevel level) {
        MemberInfo subject = requireActive(subjectMemberId);
        requireSelf(callerAccountId, subject, AuditAction.ACCESS_GRANTED);

        if (scope == null || level == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        if (!scope.isOffered()) {
            throw notAllowed("SCOPE_NOT_OFFERED");
        }

        MemberSummary audience = households.findMemberSummary(audienceMemberId)
                .filter(MemberSummary::active)
                .orElseThrow(() -> notAllowed("AUDIENCE_NOT_FOUND"));
        if (audience.id().equals(subject.id())) {
            throw notAllowed("SELF_GRANT");
        }
        if (!audience.householdId().equals(subject.householdId())) {
            throw notAllowed("NOT_SAME_HOUSEHOLD");
        }
        if (!ADULT_MEMBER_TYPE.equals(audience.memberType()) || audience.userAccountId() == null) {
            // A child cannot hold a grant: they have no account to use it with, and a child
            // being handed an adult's health data is not a design anyone intends.
            throw notAllowed("AUDIENCE_NOT_ADULT");
        }

        Instant now = clock.instant();
        Optional<AccessGrant> live = grants
                .findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(subject.id(), audience.id())
                .stream()
                .filter(g -> g.getScope() == scope)
                .findFirst();
        if (live.isPresent()) {
            if (live.get().getLevel() == level) {
                return viewOf(live.get());
            }
            // Level change: end the old grant before starting the new one. Flushed now so the
            // partial unique index (one live grant per subject/audience/scope) never sees two.
            live.get().revoke(now);
            grants.saveAndFlush(live.get());
        }

        AccessGrant saved = grants.save(
                AccessGrant.grant(subject.id(), audience.id(), scope, level, callerAccountId, now));

        audit.successForSubject(AuditAction.ACCESS_GRANTED, RESOURCE_TYPE, saved.getId(), callerAccountId,
                subject.householdId(), subject.id(),
                Map.of("scope", scope.name(), "level", level.name(), "audienceMemberId", audience.id()));

        return viewOf(saved);
    }

    /** Withdraws one grant. Only the subject may; the row is kept, marked revoked. */
    @Transactional
    public void revoke(UUID callerAccountId, UUID subjectMemberId, UUID grantId) {
        MemberInfo subject = requireActive(subjectMemberId);
        requireSelf(callerAccountId, subject, AuditAction.ACCESS_REVOKED);

        AccessGrant grant = grants.findByIdAndSubjectMemberId(grantId, subject.id())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!grant.isLive()) {
            return; // already withdrawn — the state the caller wants
        }
        grant.revoke(clock.instant());
        grants.save(grant);

        audit.successForSubject(AuditAction.ACCESS_REVOKED, RESOURCE_TYPE, grant.getId(), callerAccountId,
                subject.householdId(), subject.id(),
                Map.of("scope", grant.getScope().name(), "level", grant.getLevel().name(),
                        "audienceMemberId", grant.getAudienceMemberId()));
    }

    /** What I currently share, and with whom. Subject only. */
    @Transactional(readOnly = true)
    public List<AccessGrantView> listOutgoing(UUID callerAccountId, UUID subjectMemberId) {
        MemberInfo subject = requireActive(subjectMemberId);
        requireSelf(callerAccountId, subject, null);
        return grants.findBySubjectMemberIdAndRevokedAtIsNullOrderByGrantedAtDesc(subject.id()).stream()
                .map(this::viewOf)
                .toList();
    }

    /**
     * What other people have shared with me, across every household I belong to. Only live
     * grants, and only from subjects who are still members: a person who left takes their
     * sharing with them.
     */
    @Transactional(readOnly = true)
    public List<AccessGrantView> listIncoming(UUID callerAccountId) {
        List<UUID> callerMemberIds = households.findActiveMembershipsOf(callerAccountId).stream()
                .map(MemberInfo::id)
                .toList();
        if (callerMemberIds.isEmpty()) {
            return List.of();
        }
        return grants.findByAudienceMemberIdInAndRevokedAtIsNull(callerMemberIds).stream()
                .map(this::viewOf)
                .filter(view -> view.subjectLabel() != null)
                .toList();
    }

    /* -------------------------------------------------------------- data rights (D-028) */

    /** Everything a subject ever shared, revoked grants included — for their export. */
    @Transactional(readOnly = true)
    public List<AccessGrantView> exportForSubject(UUID subjectMemberId) {
        return grants.findBySubjectMemberIdOrderByGrantedAtDesc(subjectMemberId).stream()
                .map(this::viewOf)
                .toList();
    }

    /**
     * Erasure: the member's own grants go with their data. Grants others made <em>to</em> this
     * member are revoked rather than deleted — they are the other person's sharing history, and
     * the audience reference now points at a scrubbed membership that identifies no one.
     */
    @Transactional
    public void eraseForMember(UUID memberId) {
        grants.deleteBySubjectMemberId(memberId);
        Instant now = clock.instant();
        for (AccessGrant received : grants.findByAudienceMemberIdAndRevokedAtIsNull(memberId)) {
            received.revoke(now);
            grants.save(received);
        }
    }

    /* ------------------------------------------------------------------ internals */

    private AccessGrantView viewOf(AccessGrant grant) {
        Function<UUID, String> label = id -> households.findMemberSummary(id)
                .filter(MemberSummary::active)
                .map(MemberSummary::displayLabel)
                .orElse(null);
        return AccessGrantView.of(grant, label.apply(grant.getSubjectMemberId()),
                label.apply(grant.getAudienceMemberId()));
    }

    private MemberInfo requireActive(UUID memberId) {
        return households.findMember(memberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Only the subject may share or unshare their own data (D-002, D-044). */
    private void requireSelf(UUID callerAccountId, MemberInfo subject, AuditAction auditIfDenied) {
        if (callerAccountId.equals(subject.userAccountId())) {
            return;
        }
        if (auditIfDenied != null) {
            audit.deniedForSubject(auditIfDenied, RESOURCE_TYPE, subject.id(), callerAccountId,
                    subject.householdId(), subject.id(), "only the subject may change their sharing");
        }
        throw new AppException(ErrorCode.ACCESS_DENIED);
    }

    private static AppException notAllowed(String reason) {
        return new AppException(ErrorCode.ACCESS_GRANT_NOT_ALLOWED, Map.of("reason", reason));
    }
}
