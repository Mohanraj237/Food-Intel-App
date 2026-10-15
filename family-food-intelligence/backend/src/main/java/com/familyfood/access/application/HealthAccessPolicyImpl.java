package com.familyfood.access.application;

import com.familyfood.access.domain.AccessGrant;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.domain.Relationship;
import com.familyfood.access.infra.AccessGrantRepository;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only implementation of the D-044 decision. See {@link HealthAccessPolicy} for the rule.
 */
@Service
public class HealthAccessPolicyImpl implements HealthAccessPolicy {

    private static final String CHILD_MEMBER_TYPE = "CHILD";

    private final HouseholdAccess households;
    private final AccessGrantRepository grants;
    private final TrackingConsent trackingConsent;
    private final AuditService audit;

    public HealthAccessPolicyImpl(HouseholdAccess households,
                                 AccessGrantRepository grants,
                                 TrackingConsent trackingConsent,
                                 AuditService audit) {
        this.households = households;
        this.grants = grants;
        this.trackingConsent = trackingConsent;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public AccessDecision decide(UUID callerAccountId, UUID subjectMemberId) {
        MemberInfo subject = households.findMember(subjectMemberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (callerAccountId != null && callerAccountId.equals(subject.userAccountId())) {
            return new AccessDecision(subject, Relationship.SELF, AccessDecision.everything());
        }

        boolean childSubject = CHILD_MEMBER_TYPE.equals(subject.memberType());
        if (childSubject) {
            // A child's data is reachable through a guardian link and nothing else: there is no
            // grant path to it, whatever rows might exist (D-038, restated in D-044).
            return households.isGuardianOf(callerAccountId, subject.id())
                    ? new AccessDecision(subject, Relationship.GUARDIAN, AccessDecision.everything())
                    : none(subject);
        }

        // Grants name a membership, not an account: resolve the caller's place in the
        // subject's household. Someone outside the household holds nothing, by construction.
        Optional<MemberInfo> audience = households.findActiveMembership(subject.householdId(), callerAccountId);
        if (audience.isEmpty()) {
            return none(subject);
        }

        Map<AccessScope, AccessLevel> levels = new EnumMap<>(AccessScope.class);
        for (AccessGrant grant : grants.findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(
                subject.id(), audience.get().id())) {
            levels.merge(grant.getScope(), grant.getLevel(), AccessLevel::stronger);
        }

        // D-048: consent gates disclosure. If the subject has withdrawn tracking consent, what
        // they shared stops being shown to anyone else — the grant rows stay, so re-granting
        // consent restores exactly the sharing they had chosen.
        if (levels.keySet().stream().anyMatch(AccessScope::isTracking) && !trackingConsent.isActive(subject)) {
            levels.keySet().removeIf(AccessScope::isTracking);
        }

        if (levels.isEmpty()) {
            return none(subject);
        }
        Relationship relationship = levels.containsValue(AccessLevel.MANAGE)
                ? Relationship.CAREGIVER
                : Relationship.SHARED;
        return new AccessDecision(subject, relationship, levels);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessDecision requireView(UUID callerAccountId, UUID subjectMemberId, AccessScope scope,
                                      String resourceType) {
        AccessDecision decision = decide(callerAccountId, subjectMemberId);
        if (!decision.canView(scope)) {
            refuse(callerAccountId, decision, "no VIEW on " + scope, resourceType);
        }
        auditRead(callerAccountId, decision, List.of(scope), resourceType);
        return decision;
    }

    @Override
    @Transactional(readOnly = true)
    public AccessDecision requireManage(UUID callerAccountId, UUID subjectMemberId, AccessScope scope,
                                        String resourceType) {
        AccessDecision decision = decide(callerAccountId, subjectMemberId);
        if (!decision.canManage(scope)) {
            refuse(callerAccountId, decision, "no MANAGE on " + scope, resourceType);
        }
        return decision;
    }

    @Override
    @Transactional(readOnly = true)
    public AccessDecision requireAnyView(UUID callerAccountId, UUID subjectMemberId, String resourceType) {
        AccessDecision decision = decide(callerAccountId, subjectMemberId);
        if (decision.levels().isEmpty()) {
            refuse(callerAccountId, decision, "no visibility of any scope", resourceType);
        }
        return decision;
    }

    @Override
    public void auditRead(UUID callerAccountId, AccessDecision decision, Collection<AccessScope> scopes,
                          String resourceType) {
        if (!decision.isCrossMember() || scopes.isEmpty()) {
            return;
        }
        Map<String, Object> metadata = Map.of(
                "scope", scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                "relationship", decision.relationship().name());
        // A child's data additionally lands in the strict child-data category, so the question
        // "who has looked at my child's data" is one query.
        if (decision.isChildSubject()) {
            audit.successForSubject(AuditAction.CHILD_DATA_ACCESSED, resourceType, decision.subjectId(),
                    callerAccountId, decision.householdId(), decision.subjectId(), metadata);
        }
        audit.successForSubject(AuditAction.HEALTH_DATA_ACCESSED, resourceType, decision.subjectId(),
                callerAccountId, decision.householdId(), decision.subjectId(), metadata);
    }

    /* ------------------------------------------------------------------ internals */

    private static AccessDecision none(MemberInfo subject) {
        return new AccessDecision(subject, Relationship.NONE, Map.of());
    }

    /**
     * Audits and throws. The subject is attached, so an attempt to read someone's data appears
     * in their own access log — the attempt is exactly what a person would want to know about.
     */
    private void refuse(UUID callerAccountId, AccessDecision decision, String reason, String resourceType) {
        AuditAction action = decision.isChildSubject()
                ? AuditAction.CHILD_DATA_ACCESS_DENIED
                : AuditAction.HEALTH_DATA_ACCESS_DENIED;
        audit.deniedForSubject(action, resourceType, decision.subjectId(), callerAccountId,
                decision.householdId(), decision.subjectId(), reason);
        throw new AppException(ErrorCode.ACCESS_DENIED);
    }
}
