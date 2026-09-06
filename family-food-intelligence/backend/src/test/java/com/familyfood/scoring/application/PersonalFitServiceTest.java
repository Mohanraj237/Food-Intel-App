package com.familyfood.scoring.application;

import com.familyfood.consent.application.ConsentGate;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.GoalCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link PersonalFitServiceImpl}, with {@link HouseholdAccess},
 * {@link ConsentGate}, {@link ProfileService} and {@link RuleEngine} all mocked — this
 * class has no database or Spring context dependency, matching {@code ProfileServiceTest}
 * and {@code ConsentGateTest}'s style in the sibling modules it calls.
 *
 * <p>Covers every {@code status} value the contract defines, the purpose-code selection
 * (D-024: {@code CHILD_FOOD_SUITABILITY} vs {@code PERSONAL_FIT}), the allergen-match
 * dimension being independent of the goal-verdict dimension (D-004), and the
 * most-severe-wins aggregation across every goal a profile records.
 */
class PersonalFitServiceTest {

    private final HouseholdAccess householdAccess = mock(HouseholdAccess.class);
    private final ConsentGate consentGate = mock(ConsentGate.class);
    private final ProfileService profileService = mock(ProfileService.class);
    private final RuleEngine ruleEngine = mock(RuleEngine.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);

    private PersonalFitServiceImpl service;

    private final UUID householdId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID callerAccountId = UUID.randomUUID();
    private final UUID productVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PersonalFitServiceImpl(householdAccess, consentGate, profileService, ruleEngine, clock);
    }

    private ProductAssessmentInput input(List<String> declaredAllergenCodes) {
        return new ProductAssessmentInput(productVersionId, "SNACK", List.of(), declaredAllergenCodes);
    }

    private ProfileView profileWith(Set<GoalCode> goals, Set<AllergenCode> allergens) {
        return new ProfileView(UUID.randomUUID(), memberId, 1, AgeBand.A18_30, null, null,
                goals, allergens, Set.of(), clock.instant(), callerAccountId);
    }

    // --------------------------------------------------------------- NOT_AUTHORIZED

    @Test
    @DisplayName("unknown member -> NOT_AUTHORIZED, no verdict, empty reasons/allergenMatches")
    void unknownMemberNotAuthorized() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.empty());

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
        assertThat(result.verdict()).isNull();
        assertThat(result.reasons()).isEmpty();
        assertThat(result.allergenMatches()).isEmpty();
        assertThat(result.ruleSetVersion()).isNull();
        verify(consentGate, never()).require(any(), any());
    }

    @Test
    @DisplayName("inactive (departed) member -> NOT_AUTHORIZED")
    void inactiveMemberNotAuthorized() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", false)));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
    }

    @Test
    @DisplayName("caller is not the adult subject themselves -> NOT_AUTHORIZED (D-002)")
    void adultAccessingAnotherAdultNotAuthorized() {
        UUID otherAccountId = UUID.randomUUID();
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, otherAccountId, "ADULT", true)));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
        verify(consentGate, never()).require(any(), any());
    }

    @Test
    @DisplayName("caller is not a guardian of the child subject -> NOT_AUTHORIZED")
    void nonGuardianAccessingChildNotAuthorized() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(false);

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
        verify(consentGate, never()).require(any(), any());
    }

    @Test
    @DisplayName("a genuinely unexpected AppException from ProfileService (already authorized) degrades to NOT_AUTHORIZED, never propagated")
    void unexpectedProfileServiceExceptionDegradesToNotAuthorized() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));

        when(profileService.getCurrentProfile(callerAccountId, memberId))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
    }

    // ------------------------------------------------------------------- consent

    @Test
    @DisplayName("no active consent grant -> CONSENT_REQUIRED, and the adult purpose is PERSONAL_FIT")
    void consentRequiredForAdult() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        doThrowAppException(ErrorCode.CONSENT_REQUIRED).when(consentGate).require(memberId, "PERSONAL_FIT");

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("CONSENT_REQUIRED");
        assertThat(result.verdict()).isNull();
        assertThat(result.ruleSetVersion()).isNull();
        verify(consentGate).require(memberId, "PERSONAL_FIT");
        verify(profileService, never()).getCurrentProfile(any(), any());
    }

    @Test
    @DisplayName("withdrawn consent -> CONSENT_WITHDRAWN")
    void consentWithdrawn() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        doThrowAppException(ErrorCode.CONSENT_WITHDRAWN).when(consentGate).require(memberId, "PERSONAL_FIT");

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("CONSENT_WITHDRAWN");
    }

    @Test
    @DisplayName("a guardian evaluating a child uses the CHILD_FOOD_SUITABILITY purpose")
    void childPurposeCodeSelected() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(callerAccountId, memberId)).thenReturn(true);
        doThrowAppException(ErrorCode.CONSENT_REQUIRED).when(consentGate)
                .require(memberId, "CHILD_FOOD_SUITABILITY");

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("CONSENT_REQUIRED");
        verify(consentGate).require(memberId, "CHILD_FOOD_SUITABILITY");
    }

    @Test
    @DisplayName("an AppException with an unrelated error code from ConsentGate is rethrown, not swallowed")
    void unexpectedConsentExceptionIsRethrown() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        doThrowAppException(ErrorCode.INTERNAL_ERROR).when(consentGate).require(memberId, "PERSONAL_FIT");

        assertThatThrownBy(() -> service.evaluate(callerAccountId, memberId, input(List.of())))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    // -------------------------------------------------------------------- profile

    @Test
    @DisplayName("no profile recorded yet -> NO_PROFILE")
    void noProfileYet() {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(profileService.getCurrentProfile(callerAccountId, memberId))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NO_PROFILE");
        assertThat(result.verdict()).isNull();
        verifyNoRuleEngineCalls();
    }

    // ----------------------------------------------------------- goal-based verdict

    @Test
    @DisplayName("a profile with no goals -> ASSESSED / INSUFFICIENT_DATA / reasons=[NO_GOALS_SET]")
    void noGoalsRecorded() {
        authorizedAdultWithProfile(profileWith(Set.of(), Set.of()));
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.reasons()).hasSize(1);
        assertThat(result.reasons().get(0).code()).isEqualTo("NO_GOALS_SET");
        assertThat(result.ruleSetVersion()).isNotNull();
        verifyNoRuleEngineCalls();
    }

    @Test
    @DisplayName("all goal outcomes INSUFFICIENT_DATA (today's empty active rule set, D-024) "
            + "-> overall INSUFFICIENT_DATA with the union of reasons, deduped by code")
    void allGoalsInsufficientDataUnionsReasons() {
        ProfileView profile = profileWith(
                Set.of(GoalCode.REDUCE_ADDED_SUGAR, GoalCode.REDUCE_SODIUM), Set.of());
        authorizedAdultWithProfile(profile);
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        Reason sharedReason = new Reason("NO_ACTIVE_RULES", null, null, null, null, null);
        Reason otherReason = new Reason("NO_ACTIVE_RULES_SODIUM", "SODIUM", null, null, null, null);

        when(ruleEngine.evaluateGoal(eq("v1"), eq("REDUCE_ADDED_SUGAR"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("INSUFFICIENT_DATA", List.of(sharedReason), List.of()));
        when(ruleEngine.evaluateGoal(eq("v1"), eq("REDUCE_SODIUM"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("INSUFFICIENT_DATA", List.of(sharedReason, otherReason), List.of()));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.ruleSetVersion()).isEqualTo("v1");
        // Deduped by code: sharedReason appears once even though both goals returned it.
        assertThat(result.reasons()).extracting(Reason::code)
                .containsExactlyInAnyOrder("NO_ACTIVE_RULES", "NO_ACTIVE_RULES_SODIUM");
    }

    @Test
    @DisplayName("most-severe-wins across goals: DOESNT_FIT beats LIMIT beats OCCASIONAL beats EVERYDAY")
    void mostSevereVerdictWinsAcrossGoals() {
        ProfileView profile = profileWith(
                Set.of(GoalCode.REDUCE_ADDED_SUGAR, GoalCode.REDUCE_SODIUM, GoalCode.INCREASE_FIBRE),
                Set.of());
        authorizedAdultWithProfile(profile);
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        when(ruleEngine.evaluateGoal(eq("v1"), eq("REDUCE_ADDED_SUGAR"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("OCCASIONAL", List.of(), List.of("rule-1")));
        when(ruleEngine.evaluateGoal(eq("v1"), eq("REDUCE_SODIUM"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("DOESNT_FIT", List.of(), List.of("rule-2")));
        when(ruleEngine.evaluateGoal(eq("v1"), eq("INCREASE_FIBRE"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("LIMIT", List.of(), List.of("rule-3")));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.verdict()).isEqualTo("DOESNT_FIT");
    }

    @Test
    @DisplayName("an INSUFFICIENT_DATA goal outcome does not participate in severity ranking "
            + "but still contributes its reasons")
    void insufficientDataGoalIgnoredForSeverityButReasonsStillUnioned() {
        ProfileView profile = profileWith(
                Set.of(GoalCode.REDUCE_ADDED_SUGAR, GoalCode.REDUCE_SODIUM), Set.of());
        authorizedAdultWithProfile(profile);
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        Reason insufficientReason = new Reason("NO_ACTIVE_RULES", null, null, null, null, null);
        when(ruleEngine.evaluateGoal(eq("v1"), eq("REDUCE_ADDED_SUGAR"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("INSUFFICIENT_DATA", List.of(insufficientReason), List.of()));

        Reason limitReason = new Reason("HIGH_SODIUM", "SODIUM", "PER_100G",
                new BigDecimal("700"), new BigDecimal("600"), "WHO sodium 2012");
        when(ruleEngine.evaluateGoal(eq("v1"), eq("REDUCE_SODIUM"), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("LIMIT", List.of(limitReason), List.of("rule-x")));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.verdict()).isEqualTo("LIMIT");
        assertThat(result.reasons()).extracting(Reason::code)
                .containsExactlyInAnyOrder("NO_ACTIVE_RULES", "HIGH_SODIUM");
    }

    // ---------------------------------------------------------------- allergen match

    @Test
    @DisplayName("allergen match is populated independently of the goal-verdict dimension (D-004, D-024.2)")
    void allergenMatchIndependentOfVerdict() {
        ProfileView profile = profileWith(Set.of(), Set.of(AllergenCode.PEANUT, AllergenCode.MILK));
        authorizedAdultWithProfile(profile);

        PersonalFitView result = service.evaluate(callerAccountId, memberId,
                input(List.of("PEANUT", "SOY")));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.verdict()).isEqualTo("INSUFFICIENT_DATA"); // no goals set
        assertThat(result.allergenMatches()).containsExactly("PEANUT");
    }

    @Test
    @DisplayName("no overlap between declared product allergens and the profile -> empty allergenMatches")
    void noAllergenOverlap() {
        ProfileView profile = profileWith(Set.of(), Set.of(AllergenCode.MILK));
        authorizedAdultWithProfile(profile);

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of("PEANUT")));

        assertThat(result.allergenMatches()).isEmpty();
    }

    @Test
    @DisplayName("computedAt comes from the injected Clock, never wall-clock time")
    void computedAtFromInjectedClock() {
        authorizedAdultWithProfile(profileWith(Set.of(), Set.of()));

        PersonalFitView result = service.evaluate(callerAccountId, memberId, input(List.of()));

        assertThat(result.computedAt()).isEqualTo(clock.instant());
    }

    // ------------------------------------------------------------------------ helpers

    private void authorizedAdultWithProfile(ProfileView profile) {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, callerAccountId, "ADULT", true)));
        when(profileService.getCurrentProfile(callerAccountId, memberId)).thenReturn(profile);
    }

    private void verifyNoRuleEngineCalls() {
        verify(ruleEngine, never()).evaluateGoal(any(), any(), any(), any());
    }

    private org.mockito.stubbing.Stubber doThrowAppException(ErrorCode code) {
        return org.mockito.Mockito.doThrow(new AppException(code));
    }
}
