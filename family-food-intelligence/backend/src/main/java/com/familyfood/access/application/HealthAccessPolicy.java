package com.familyfood.access.application;

import com.familyfood.access.domain.AccessScope;

import java.util.Collection;
import java.util.UUID;

/**
 * The one answer to "may this person see or act on that person's health data?" (D-044).
 *
 * <p>Published for every health module — wellness, goals, habits, nutrition, insights, the
 * dashboards, and the W4 records modules. None of them may reimplement any part of this
 * decision; before W1 the rule was copied into four services and had drifted in three different
 * directions, one of which leaked other adults' meals (D-042).
 *
 * <p>Evaluated in this order, and the first answer is final:
 * <ol>
 *   <li>unknown or departed subject → {@code RESOURCE_NOT_FOUND};</li>
 *   <li>the caller is the subject → SELF, MANAGE on every scope;</li>
 *   <li>the subject is a CHILD and the caller guards them → GUARDIAN, MANAGE on every scope;</li>
 *   <li>live grants from the subject to the caller's membership in the same household →
 *       CAREGIVER (any MANAGE) or SHARED (VIEW only) — and only for scopes whose consent
 *       purpose the subject currently has active (D-048: consent gates disclosure);</li>
 *   <li>otherwise NONE.</li>
 * </ol>
 * Household role is never an input. The metric age gate (D-036) is a separate check that runs
 * after this one: MANAGE never makes a metric permitted for a child.
 */
public interface HealthAccessPolicy {

    /** The caller's relationship and effective levels. Writes no audit event. */
    AccessDecision decide(UUID callerAccountId, UUID subjectMemberId);

    /**
     * Requires VIEW on {@code scope}. A refusal is audited against the subject and thrown as
     * {@code ACCESS_DENIED}; an allowed cross-member read is audited as an access.
     */
    AccessDecision requireView(UUID callerAccountId, UUID subjectMemberId, AccessScope scope,
                               String resourceType);

    /**
     * Requires MANAGE on {@code scope}. A refusal is audited and thrown; a success is not audited
     * here, because the writer records the write itself (with the subject attached).
     */
    AccessDecision requireManage(UUID callerAccountId, UUID subjectMemberId, AccessScope scope,
                                 String resourceType);

    /**
     * Requires VIEW on at least one scope, for list reads that are filtered afterwards (a goal
     * list shows only goals whose metric the caller may see). A caller who may see nothing at
     * all is refused, so that "not allowed" never masquerades as "nothing recorded".
     */
    AccessDecision requireAnyView(UUID callerAccountId, UUID subjectMemberId, String resourceType);

    /**
     * Records one cross-member read covering several scopes — a dashboard reading a member's
     * activity, sleep and water in one request writes one access event, not three. A no-op for
     * SELF.
     */
    void auditRead(UUID callerAccountId, AccessDecision decision, Collection<AccessScope> scopes,
                   String resourceType);
}
