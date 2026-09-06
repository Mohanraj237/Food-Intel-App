package com.familyfood.scoring.application;

import com.familyfood.consent.application.ConsentService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end {@link PersonalFitService} behaviour through the real Spring context, real
 * {@link ProfileService} and {@link ConsentService}, and real Postgres — proving the
 * cross-module wiring (household-membership authorization, consent-at-processing-time,
 * profile lookup) actually holds together, not just each collaborator in isolation.
 *
 * <p>{@link HouseholdAccess} is stubbed with {@code @MockBean}, exactly as
 * {@code ProfileControllerIT} and {@code ConsentServiceIT} do: the household module lands
 * in parallel with this one, and the stub is wired to answer from whatever
 * {@code household_member} fixture rows each test inserts directly via {@link JdbcTemplate}
 * — the same rows the real, Postgres-backed {@code ConsentRecordRepository} and
 * {@code PersonProfileRepository} foreign keys require.
 *
 * <p>{@link RuleEngine} is also stubbed with {@code @MockBean}. The Scoring Domain
 * workstream that owns its real implementation and the "v1" empty {@code rule_set} (D-024
 * point 1) lands in parallel with this file, so this suite cannot depend on it existing —
 * doing so would make this test's pass/fail depend on unrelated, concurrently-changing
 * code. Instead the stub reproduces exactly the documented behaviour of today's real
 * active rule set (zero rules &rarr; every goal evaluates to {@code INSUFFICIENT_DATA})
 * for the one test that needs it, which is the behaviour worth locking in here; the rule
 * engine's own internal correctness is that workstream's test suite's job, and the
 * aggregation logic over whatever a rule engine returns is already exhaustively covered by
 * {@code PersonalFitServiceTest} with a mocked {@link RuleEngine}.
 */
class PersonalFitServiceIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private PersonalFitService personalFitService;

    @MockBean
    private HouseholdAccess householdAccess;

    @MockBean
    private RuleEngine ruleEngine;

    private final UUID productVersionId = UUID.randomUUID();

    // --------------------------------------------------------------------- happy paths

    @Test
    @DisplayName("a profile with no goals -> ASSESSED / INSUFFICIENT_DATA / reasons=[NO_GOALS_SET]")
    void profileWithNoGoals() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(), Set.of());
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        PersonalFitView result = personalFitService.evaluate(owner.accountId(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.reasons()).extracting(Reason::code).containsExactly("NO_GOALS_SET");
        assertThat(result.ruleSetVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("a profile with goals against today's real empty active rule set -> INSUFFICIENT_DATA "
            + "(D-024 point 1: no fabricated numeric threshold, honest rather than a guess)")
    void profileWithGoalsAgainstEmptyRuleSet() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(GoalCode.REDUCE_ADDED_SUGAR, GoalCode.REDUCE_SODIUM), Set.of(), Set.of());

        // Reproduces the documented shape of the real "v1" rule set having zero
        // scoring_rule rows: every goal comes back INSUFFICIENT_DATA / NO_ACTIVE_RULES.
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");
        when(ruleEngine.evaluateGoal(eq("v1"), any(), any(), eq("A18_30")))
                .thenReturn(new RuleOutcome("INSUFFICIENT_DATA",
                        List.of(new Reason("NO_ACTIVE_RULES", null, null, null, null, null)),
                        List.of()));

        PersonalFitView result = personalFitService.evaluate(owner.accountId(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.verdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.reasons()).extracting(Reason::code).containsExactly("NO_ACTIVE_RULES");
        assertThat(result.ruleSetVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("declared product allergens overlapping the member's own profile are reported as allergenMatches")
    void allergenMatch() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(AllergenCode.PEANUT, AllergenCode.MILK), Set.of());
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        PersonalFitView result = personalFitService.evaluate(owner.accountId(), memberId,
                input(List.of("PEANUT", "SOY")));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.allergenMatches()).containsExactly("PEANUT");
    }

    @Test
    @DisplayName("a guardian evaluating their child's fit is ASSESSED once child consent is active")
    void guardianEvaluatesChild() throws Exception {
        Account guardian = registerAccount();
        UUID householdId = insertHousehold(guardian.accountId());
        UUID childMemberId = insertChildMember(householdId);
        when(householdAccess.findMember(childMemberId)).thenReturn(Optional.of(
                new MemberInfo(childMemberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(guardian.accountId(), childMemberId)).thenReturn(true);

        consentService.grant(guardian.accountId(), childMemberId, "CHILD_FOOD_SUITABILITY", Map.of());
        profileService.createOrUpdateProfile(guardian.accountId(), childMemberId, AgeBand.C4_6, null, null,
                Set.of(), Set.of(), Set.of());
        when(ruleEngine.activeRuleSetVersion()).thenReturn("v1");

        PersonalFitView result = personalFitService.evaluate(guardian.accountId(), childMemberId, input(List.of()));

        assertThat(result.status()).isEqualTo("ASSESSED");
        assertThat(result.memberId()).isEqualTo(childMemberId);
    }

    // ------------------------------------------------------------------------ non-ASSESSED

    @Test
    @DisplayName("no profile recorded yet -> NO_PROFILE")
    void noProfileYet() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        // No profileService.createOrUpdateProfile call: the member has never recorded one.

        PersonalFitView result = personalFitService.evaluate(owner.accountId(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NO_PROFILE");
        assertThat(result.verdict()).isNull();
    }

    @Test
    @DisplayName("withdrawn consent -> CONSENT_WITHDRAWN, even though a profile exists")
    void consentWithdrawn() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(), Set.of());
        consentService.withdraw(owner.accountId(), memberId, "PERSONAL_FIT");

        PersonalFitView result = personalFitService.evaluate(owner.accountId(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("CONSENT_WITHDRAWN");
        assertThat(result.verdict()).isNull();
    }

    @Test
    @DisplayName("no consent ever granted -> CONSENT_REQUIRED")
    void consentNeverGranted() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        PersonalFitView result = personalFitService.evaluate(owner.accountId(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("CONSENT_REQUIRED");
    }

    @Test
    @DisplayName("an adult may not evaluate another adult's fit — NOT_AUTHORIZED (D-002)")
    void unauthorizedAccessToAnotherAdult() throws Exception {
        Account owner = registerAccount();
        Account outsider = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        stubAdult(memberId, householdId, owner.accountId());

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(), Set.of());

        PersonalFitView result = personalFitService.evaluate(outsider.accountId(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
        assertThat(result.verdict()).isNull();
    }

    @Test
    @DisplayName("a non-guardian may not evaluate a child's fit — NOT_AUTHORIZED")
    void nonGuardianAccessToChild() throws Exception {
        Account guardian = registerAccount();
        Account stranger = registerAccount();
        UUID householdId = insertHousehold(guardian.accountId());
        UUID childMemberId = insertChildMember(householdId);
        when(householdAccess.findMember(childMemberId)).thenReturn(Optional.of(
                new MemberInfo(childMemberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(stranger.accountId(), childMemberId)).thenReturn(false);

        PersonalFitView result = personalFitService.evaluate(stranger.accountId(), childMemberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
    }

    @Test
    @DisplayName("an unknown member id -> NOT_AUTHORIZED")
    void unknownMember() {
        UUID memberId = UUID.randomUUID();
        when(householdAccess.findMember(memberId)).thenReturn(Optional.empty());

        PersonalFitView result = personalFitService.evaluate(UUID.randomUUID(), memberId, input(List.of()));

        assertThat(result.status()).isEqualTo("NOT_AUTHORIZED");
    }

    // ------------------------------------------------------------------------ fixtures

    private ProductAssessmentInput input(List<String> declaredAllergenCodes) {
        return new ProductAssessmentInput(productVersionId, "SNACK", List.of(), declaredAllergenCodes);
    }

    private void stubAdult(UUID memberId, UUID householdId, UUID userAccountId) {
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, userAccountId, "ADULT", true)));
    }

    private record Account(UUID accountId, String accessToken) {}

    private Account registerAccount() throws Exception {
        String email = "personal-fit-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Account(UUID.fromString(body.get("accountId").asText()),
                body.get("accessToken").asText());
    }

    private UUID insertHousehold(UUID createdByUserId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into household (id, name, created_by_user_id)
                values (?, 'Test household', ?)
                """, id, createdByUserId);
        return id;
    }

    private UUID insertAdultMember(UUID householdId, UUID userAccountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into household_member
                    (id, household_id, user_account_id, member_type, role, display_label)
                values (?, ?, ?, 'ADULT', 'OWNER', 'Owner')
                """, id, householdId, userAccountId);
        return id;
    }

    private UUID insertChildMember(UUID householdId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into household_member
                    (id, household_id, user_account_id, member_type, role, display_label)
                values (?, ?, null, 'CHILD', 'DEPENDENT', 'Kiddo')
                """, id, householdId);
        return id;
    }
}
