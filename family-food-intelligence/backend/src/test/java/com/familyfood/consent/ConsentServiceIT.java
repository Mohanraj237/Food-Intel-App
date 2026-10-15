package com.familyfood.consent;

import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end consent behaviour through the real HTTP + security + database stack.
 *
 * <p>{@link HouseholdAccess} is stubbed with {@code @MockBean}, exactly as
 * {@code ProfileControllerIT} does: the household module lands in parallel, and the stub is
 * wired to answer from whatever fixture rows each test inserts directly via
 * {@link JdbcTemplate}, so the authorization behaviour under test is {@code ConsentService}'s,
 * not the stub's.
 *
 * <p>The re-grant test is the one that actually needs real Postgres: it proves
 * {@code uq_consent_active_per_subject_purpose} is never transiently violated by
 * {@code ConsentService.grant}'s withdraw-then-insert sequence, which a mocked repository
 * cannot demonstrate.
 */
class ConsentServiceIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private HouseholdAccess householdAccess;

    @Test
    @DisplayName("GET /consent/policies is public and returns the seeded purposes")
    void policiesArePublic() throws Exception {
        // Order is fixed by consent_purpose.sort_order, V1 seed interleaved with V14's
        // two wellness-tracking purposes: ACCOUNT(10), PERSONAL_FIT(20),
        // HEALTH_TRACKING(25), CHILD_FOOD_SUITABILITY(30), CHILD_WELLNESS_TRACKING(35),
        // LABEL_IMAGE_PROCESSING(40), PRODUCT_IMPROVEMENT(50) — index 3 is the first
        // requires_verifiable purpose.
        mockMvc.perform(get("/api/v1/consent/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[3].purposeCode").value("CHILD_FOOD_SUITABILITY"))
                .andExpect(jsonPath("$[3].requiresVerifiable").value(true))
                .andExpect(jsonPath("$[0].requiresVerifiable").value(false));
    }

    @Test
    @DisplayName("an adult can grant, list and withdraw their own consent")
    void adultSelfGrantListWithdraw() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        mockMvc.perform(post(grantsPath(memberId))
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"PERSONAL_FIT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.subjectType").value("ADULT_SELF"))
                .andExpect(jsonPath("$.mechanism").value("SELF_ATTESTATION"));

        mockMvc.perform(get(grantsPath(memberId))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].evidence").doesNotExist());

        mockMvc.perform(delete(grantsPath(memberId) + "/PERSONAL_FIT")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        Integer activeCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ? and status = 'ACTIVE'",
                Integer.class, memberId);
        assertThat(activeCount).isZero();

        Integer withdrawnCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ? and status = 'WITHDRAWN'",
                Integer.class, memberId);
        assertThat(withdrawnCount).isEqualTo(1);
    }

    @Test
    @DisplayName("an adult cannot grant consent for another adult — ACCESS_DENIED")
    void adultCannotGrantForAnotherAdult() throws Exception {
        Account owner = registerAccount();
        Account outsider = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        mockMvc.perform(post(grantsPath(memberId))
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"PERSONAL_FIT"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("a guardian can grant CHILD_FOOD_SUITABILITY for their child; a non-guardian cannot")
    void guardianGrantsChildPurposeNonGuardianDenied() throws Exception {
        Account guardian = registerAccount();
        Account stranger = registerAccount();
        UUID householdId = insertHousehold(guardian.accountId());
        UUID childMemberId = insertChildMember(householdId);
        when(householdAccess.findMember(childMemberId)).thenReturn(Optional.of(
                new MemberInfo(childMemberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(stranger.accountId(), childMemberId)).thenReturn(false);
        when(householdAccess.isGuardianOf(guardian.accountId(), childMemberId)).thenReturn(true);

        mockMvc.perform(post(grantsPath(childMemberId))
                        .header("Authorization", "Bearer " + stranger.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"CHILD_FOOD_SUITABILITY"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_GUARDIAN"));

        mockMvc.perform(post(grantsPath(childMemberId))
                        .header("Authorization", "Bearer " + guardian.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"CHILD_FOOD_SUITABILITY"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectType").value("CHILD"))
                .andExpect(jsonPath("$.mechanism").value("GUARDIAN_ATTESTATION"));
    }

    @Test
    @DisplayName("granting the same purpose twice leaves exactly one ACTIVE record (partial unique index respected)")
    void reGrantLeavesExactlyOneActiveRecord() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(grantsPath(memberId))
                            .header("Authorization", "Bearer " + owner.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"purposeCode":"PERSONAL_FIT"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        Integer activeCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ? "
                        + "and purpose_code = 'PERSONAL_FIT' and status = 'ACTIVE'",
                Integer.class, memberId);
        assertThat(activeCount).isEqualTo(1);

        Integer totalCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ? "
                        + "and purpose_code = 'PERSONAL_FIT'",
                Integer.class, memberId);
        assertThat(totalCount).isEqualTo(3);
    }

    @Test
    @DisplayName("withdrawing with no active grant is a no-op (idempotent)")
    void withdrawWithNothingActiveIsNoOp() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        mockMvc.perform(delete(grantsPath(memberId) + "/PERSONAL_FIT")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        Integer totalCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ?",
                Integer.class, memberId);
        assertThat(totalCount).isZero();
    }

    // ------------------------------------------------------------------------ fixtures

    private String grantsPath(UUID memberId) {
        return "/api/v1/consent/members/%s/grants".formatted(memberId);
    }

    private record Account(UUID accountId, String accessToken) {}

    private Account registerAccount() throws Exception {
        String email = "consent-it-" + UUID.randomUUID() + "@example.com";
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
