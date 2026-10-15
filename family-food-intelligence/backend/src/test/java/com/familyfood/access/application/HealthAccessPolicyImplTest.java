package com.familyfood.access.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.domain.Relationship;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The D-044 decision as a matrix: relationship × scope × level × age × consent.
 *
 * <p>This is the test the architecture document promises ("one policy, tested as a matrix"). It
 * runs the real policy over an in-memory household, so what is asserted here is what every
 * health module actually gets — wellness, goals, habits, meals and the dashboards all ask this
 * one class.
 */
class HealthAccessPolicyImplTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final String RESOURCE = "HEALTH_OBSERVATION";

    private static final UUID HOME = UUID.randomUUID();
    private static final UUID ELSEWHERE = UUID.randomUUID();

    private static final UUID ME_ACCOUNT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID SPOUSE_ACCOUNT = UUID.randomUUID();
    private static final UUID SPOUSE = UUID.randomUUID();
    /** The spouse's second membership, in another household. */
    private static final UUID SPOUSE_ELSEWHERE = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID OUTSIDER_ACCOUNT = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    private HealthAccessFixture fixture;
    private HealthAccessPolicy policy;
    private AuditService audit;

    @BeforeEach
    void setUp() {
        fixture = new HealthAccessFixture(NOW);
        policy = fixture.policy;
        audit = fixture.audit;

        fixture.adult(HOME, ME_ACCOUNT, ME);
        fixture.adult(HOME, SPOUSE_ACCOUNT, SPOUSE);
        fixture.child(HOME, CHILD, ME_ACCOUNT);
        fixture.adult(ELSEWHERE, OUTSIDER_ACCOUNT, OUTSIDER);
    }

    private static AppException thrown(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("expected an AppException");
    }

    /* ------------------------------------------------------------------ SELF */

    @Test
    @DisplayName("SELF holds MANAGE on every scope")
    void selfManagesEverything() {
        AccessDecision decision = policy.decide(ME_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.SELF);
        assertThat(decision.levels()).hasSize(AccessScope.values().length);
        for (AccessScope scope : AccessScope.values()) {
            assertThat(decision.canManage(scope)).as("self MANAGE on %s", scope).isTrue();
        }
        assertThat(decision.isCrossMember()).isFalse();
    }

    @Test
    @DisplayName("SELF keeps seeing their own data after withdrawing tracking consent (D-048)")
    void selfIsNotGatedByOwnConsent() {
        fixture.withdrawConsent(ME);

        AccessDecision decision = policy.decide(ME_ACCOUNT, ME);

        // So that a person can still see, export and erase what was collected.
        assertThat(decision.relationship()).isEqualTo(Relationship.SELF);
        assertThat(decision.canView(AccessScope.BODY)).isTrue();
    }

    /* -------------------------------------------------------------- GUARDIAN */

    @Test
    @DisplayName("a GUARDIAN of a child holds MANAGE on every scope")
    void guardianManagesChild() {
        AccessDecision decision = policy.decide(ME_ACCOUNT, CHILD);

        assertThat(decision.relationship()).isEqualTo(Relationship.GUARDIAN);
        for (AccessScope scope : AccessScope.values()) {
            assertThat(decision.canManage(scope)).as("guardian MANAGE on %s", scope).isTrue();
        }
        assertThat(decision.isChildSubject()).isTrue();
        assertThat(decision.isCrossMember()).isTrue();
    }

    @Test
    @DisplayName("a non-guardian adult sees nothing of a child — even if a grant row exists")
    void nonGuardianAdultGetsNothingOfAChildEvenWithAGrantRow() {
        // A row that the grant service would never write — child data is not grantable — but
        // the policy must not honour it if it is ever there (D-038, restated in D-044).
        fixture.grant(CHILD, SPOUSE, AccessScope.ACTIVITY, AccessLevel.MANAGE);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, CHILD);

        assertThat(decision.relationship()).isEqualTo(Relationship.NONE);
        assertThat(decision.levels()).isEmpty();
        verify(fixture.grants, never()).findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(any(), any());
    }

    /* --------------------------------------------------------------- grants */

    @Test
    @DisplayName("a VIEW grant makes the audience SHARED on exactly that scope")
    void viewGrantIsSharedOnExactlyThatScope() {
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.SHARED);
        assertThat(decision.levels()).containsExactly(Map.entry(AccessScope.ACTIVITY, AccessLevel.VIEW));
        assertThat(decision.canView(AccessScope.ACTIVITY)).isTrue();
        assertThat(decision.canManage(AccessScope.ACTIVITY)).isFalse();
        assertThat(decision.canView(AccessScope.BODY)).isFalse();
        assertThat(decision.canView(AccessScope.CLINICAL)).isFalse();
    }

    @Test
    @DisplayName("any MANAGE grant makes the audience a CAREGIVER; VIEW scopes stay view-only")
    void manageGrantMakesCaregiver() {
        fixture.grant(ME, SPOUSE, AccessScope.BODY, AccessLevel.MANAGE);
        fixture.grant(ME, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.CAREGIVER);
        assertThat(decision.canManage(AccessScope.BODY)).isTrue();
        assertThat(decision.canView(AccessScope.SLEEP)).isTrue();
        assertThat(decision.canManage(AccessScope.SLEEP)).isFalse();
        assertThat(decision.viewableScopes()).containsExactlyInAnyOrder(AccessScope.BODY, AccessScope.SLEEP);
    }

    static Stream<Arguments> everyOfferedScopeAtEveryLevel() {
        return Arrays.stream(AccessScope.values())
                .filter(AccessScope::isOffered)
                .flatMap(scope -> Arrays.stream(AccessLevel.values()).map(level -> Arguments.of(scope, level)));
    }

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("everyOfferedScopeAtEveryLevel")
    @DisplayName("a single grant opens its own scope at its own level, and nothing else")
    void singleGrantOpensOnlyItself(AccessScope scope, AccessLevel level) {
        fixture.grant(ME, SPOUSE, scope, level);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship())
                .isEqualTo(level == AccessLevel.MANAGE ? Relationship.CAREGIVER : Relationship.SHARED);
        for (AccessScope other : AccessScope.values()) {
            assertThat(decision.canView(other)).as("VIEW %s", other).isEqualTo(other == scope);
            assertThat(decision.canManage(other)).as("MANAGE %s", other)
                    .isEqualTo(other == scope && level == AccessLevel.MANAGE);
        }
    }

    @Test
    @DisplayName("a grant naming the caller's membership in ANOTHER household is ignored")
    void grantToCallersOtherMembershipIsIgnored() {
        fixture.adult(ELSEWHERE, SPOUSE_ACCOUNT, SPOUSE_ELSEWHERE);
        fixture.grant(ME, SPOUSE_ELSEWHERE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        // Grants name a membership, and only the caller's membership in the subject's own
        // household is ever looked up.
        assertThat(decision.relationship()).isEqualTo(Relationship.NONE);
        assertThat(decision.levels()).isEmpty();
    }

    @Test
    @DisplayName("a caller who is not in the subject's household holds nothing")
    void callerOutsideHouseholdGetsNothing() {
        fixture.grant(ME, OUTSIDER, AccessScope.ACTIVITY, AccessLevel.VIEW);

        AccessDecision decision = policy.decide(OUTSIDER_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.NONE);
        assertThat(decision.levels()).isEmpty();
    }

    @Test
    @DisplayName("household membership alone is NONE — the default is closed")
    void householdMembershipAloneIsNone() {
        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.NONE);
        assertThat(decision.viewableScopes()).isEmpty();
    }

    @Test
    @DisplayName("the household OWNER role grants nothing and is never even consulted")
    void ownerRoleNeverGrantsAnything() {
        fixture.role(SPOUSE, "OWNER");

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.NONE);
        HouseholdAccess households = fixture.households;
        verify(households, never()).findMemberSummary(any());
        verify(households, never()).listActiveMemberSummaries(any());
    }

    /* ------------------------------------------------------ not found, consent */

    @Test
    @DisplayName("an unknown subject is RESOURCE_NOT_FOUND")
    void unknownSubjectIsNotFound() {
        assertThat(thrown(() -> policy.decide(ME_ACCOUNT, UUID.randomUUID())).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("a departed subject is RESOURCE_NOT_FOUND — for everyone, their former self included")
    void departedSubjectIsNotFound() {
        fixture.grant(SPOUSE, ME, AccessScope.ACTIVITY, AccessLevel.VIEW);
        fixture.depart(SPOUSE);

        assertThat(thrown(() -> policy.decide(ME_ACCOUNT, SPOUSE)).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(thrown(() -> policy.decide(SPOUSE_ACCOUNT, SPOUSE)).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("withdrawn tracking consent drops tracking scopes — NONE when nothing is left (D-048)")
    void withdrawnConsentDropsTrackingScopes() {
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);
        fixture.grant(ME, SPOUSE, AccessScope.BODY, AccessLevel.MANAGE);
        fixture.withdrawConsent(ME);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.NONE);
        assertThat(decision.levels()).isEmpty();
        verify(fixture.consentGate).isActive(ME, "HEALTH_TRACKING");
    }

    @Test
    @DisplayName("withdrawn tracking consent drops ONLY tracking scopes")
    void withdrawnConsentKeepsNonTrackingScopes() {
        // FOOD_PROFILE is not offered yet, so no such row can be made today; the rule still has
        // to be exact when it is.
        fixture.grant(ME, SPOUSE, AccessScope.FOOD_PROFILE, AccessLevel.VIEW);
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.MANAGE);
        fixture.withdrawConsent(ME);

        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThat(decision.relationship()).isEqualTo(Relationship.SHARED);
        assertThat(decision.viewableScopes()).containsExactly(AccessScope.FOOD_PROFILE);
    }

    @Test
    @DisplayName("restoring consent restores exactly the sharing that was chosen")
    void restoringConsentRestoresSharing() {
        fixture.grant(ME, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW);
        fixture.withdrawConsent(ME);
        assertThat(policy.decide(SPOUSE_ACCOUNT, ME).relationship()).isEqualTo(Relationship.NONE);

        fixture.giveConsent(ME);

        assertThat(policy.decide(SPOUSE_ACCOUNT, ME).viewableScopes()).containsExactly(AccessScope.SLEEP);
    }

    /* ---------------------------------------------------- require*, auditing */

    @Test
    @DisplayName("a refused VIEW is audited against the subject and thrown as ACCESS_DENIED")
    void requireViewRefusalIsAuditedAgainstTheSubject() {
        assertThat(thrown(() -> policy.requireView(SPOUSE_ACCOUNT, ME, AccessScope.BODY, RESOURCE)).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(audit).deniedForSubject(eq(AuditAction.HEALTH_DATA_ACCESS_DENIED), eq(RESOURCE), eq(ME),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(ME), anyString());
        verify(audit, never()).successForSubject(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("a refused attempt on a child's data lands in the strict child-data category")
    void refusalOnChildIsChildDataAccessDenied() {
        assertThat(thrown(() -> policy.requireView(SPOUSE_ACCOUNT, CHILD, AccessScope.NUTRITION, RESOURCE)).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(audit).deniedForSubject(eq(AuditAction.CHILD_DATA_ACCESS_DENIED), eq(RESOURCE), eq(CHILD),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(CHILD), anyString());
    }

    @Test
    @DisplayName("a VIEW grant does not satisfy requireManage — refused and audited")
    void viewerCannotManage() {
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        assertThat(thrown(() -> policy.requireManage(SPOUSE_ACCOUNT, ME, AccessScope.ACTIVITY, RESOURCE)).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(audit).deniedForSubject(eq(AuditAction.HEALTH_DATA_ACCESS_DENIED), eq(RESOURCE), eq(ME),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(ME), anyString());
    }

    @Test
    @DisplayName("an allowed requireManage audits nothing — the writer records the write itself")
    void allowedManageIsNotAuditedAsARead() {
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.MANAGE);

        AccessDecision decision = policy.requireManage(SPOUSE_ACCOUNT, ME, AccessScope.ACTIVITY, RESOURCE);

        assertThat(decision.relationship()).isEqualTo(Relationship.CAREGIVER);
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("an allowed cross-member VIEW is audited once, with the subject, scope and relationship")
    @SuppressWarnings("unchecked")
    void allowedCrossMemberViewIsAudited() {
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        policy.requireView(SPOUSE_ACCOUNT, ME, AccessScope.ACTIVITY, RESOURCE);

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audit).successForSubject(eq(AuditAction.HEALTH_DATA_ACCESSED), eq(RESOURCE), eq(ME),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(ME), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("scope", "ACTIVITY").containsEntry("relationship", "SHARED");
        // An adult's data is not child data.
        verify(audit, never()).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), any(), any(), any(),
                any(), any(), anyMap());
    }

    @Test
    @DisplayName("requireAnyView refuses a caller who can see nothing, so 'not allowed' never reads as 'empty'")
    void requireAnyViewRefusesNothingVisible() {
        assertThat(thrown(() -> policy.requireAnyView(SPOUSE_ACCOUNT, ME, "WELLNESS_GOAL")).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(audit).deniedForSubject(eq(AuditAction.HEALTH_DATA_ACCESS_DENIED), eq("WELLNESS_GOAL"), eq(ME),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(ME), anyString());

        fixture.grant(ME, SPOUSE, AccessScope.HYDRATION, AccessLevel.VIEW);
        assertThat(policy.requireAnyView(SPOUSE_ACCOUNT, ME, "WELLNESS_GOAL").viewableScopes())
                .containsExactly(AccessScope.HYDRATION);
    }

    @Test
    @DisplayName("auditRead is a no-op for SELF")
    void auditReadIsNoOpForSelf() {
        AccessDecision self = policy.decide(ME_ACCOUNT, ME);

        policy.auditRead(ME_ACCOUNT, self, List.of(AccessScope.ACTIVITY, AccessScope.BODY), RESOURCE);

        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("auditRead of a child writes CHILD_DATA_ACCESSED and HEALTH_DATA_ACCESSED, once each")
    @SuppressWarnings("unchecked")
    void auditReadOfChildWritesBothCategories() {
        AccessDecision guardian = policy.decide(ME_ACCOUNT, CHILD);

        policy.auditRead(ME_ACCOUNT, guardian, List.of(AccessScope.SLEEP, AccessScope.ACTIVITY), "TODAY");

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audit).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), eq("TODAY"), eq(CHILD),
                eq(ME_ACCOUNT), eq(HOME), eq(CHILD), anyMap());
        verify(audit).successForSubject(eq(AuditAction.HEALTH_DATA_ACCESSED), eq("TODAY"), eq(CHILD),
                eq(ME_ACCOUNT), eq(HOME), eq(CHILD), metadata.capture());
        // One event covering both scopes, not one per scope.
        assertThat(metadata.getValue()).containsEntry("scope", "ACTIVITY,SLEEP")
                .containsEntry("relationship", "GUARDIAN");
    }

    @Test
    @DisplayName("auditRead with no scopes records nothing")
    void auditReadWithNoScopesIsNoOp() {
        AccessDecision guardian = policy.decide(ME_ACCOUNT, CHILD);

        policy.auditRead(ME_ACCOUNT, guardian, List.of(), RESOURCE);

        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("a decision is immutable — nobody downstream can widen it")
    void decisionIsImmutable() {
        fixture.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);
        AccessDecision decision = policy.decide(SPOUSE_ACCOUNT, ME);

        assertThatThrownBy(() -> decision.levels().put(AccessScope.BODY, AccessLevel.MANAGE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
