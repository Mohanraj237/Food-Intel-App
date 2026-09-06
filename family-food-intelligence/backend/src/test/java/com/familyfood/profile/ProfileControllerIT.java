package com.familyfood.profile;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end profile behaviour through the real HTTP + security + database stack.
 *
 * <p>{@link HouseholdAccess} is stubbed with {@code @MockBean} for the same reason as
 * {@code ProfileVersionChainIT}: the household module lands in parallel and has no
 * implementation yet. The stub is wired to answer from whatever fixture rows each test
 * inserts directly via {@link JdbcTemplate}, so the authorization behaviour under test is
 * exactly {@code ProfileService}'s, not the stub's.
 */
class ProfileControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private HouseholdAccess householdAccess;

    // --------------------------------------------------------------------------- adult

    @Test
    @DisplayName("an adult can read and replace their own profile")
    void adultSelfEditAllowedEndToEnd() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        mockMvc.perform(put(profilePath(householdId, memberId))
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"A18_30","dietaryPreference":"VEG",
                                 "goals":["INCREASE_FIBRE"],"allergens":["PEANUT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.ageBand").value("A18_30"));

        mockMvc.perform(get(profilePath(householdId, memberId))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("an adult cannot write another adult's profile (D-002) — ACCESS_DENIED")
    void adultCannotEditAnotherAdult() throws Exception {
        Account owner = registerAccount();
        Account outsider = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        mockMvc.perform(put(profilePath(householdId, memberId))
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"A18_30"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // --------------------------------------------------------------------------- child

    @Test
    @DisplayName("a non-guardian cannot write a child's profile — NOT_A_GUARDIAN")
    void nonGuardianCannotEditChild() throws Exception {
        Account guardian = registerAccount();
        Account stranger = registerAccount();
        UUID householdId = insertHousehold(guardian.accountId());
        UUID childMemberId = insertChildMember(householdId);
        when(householdAccess.findMember(childMemberId)).thenReturn(Optional.of(
                new MemberInfo(childMemberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(stranger.accountId(), childMemberId)).thenReturn(false);

        mockMvc.perform(put(profilePath(householdId, childMemberId))
                        .header("Authorization", "Bearer " + stranger.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"C4_6"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_GUARDIAN"));
    }

    @Test
    @DisplayName("a guardian can write and read a child's profile")
    void guardianCanEditChild() throws Exception {
        Account guardian = registerAccount();
        UUID householdId = insertHousehold(guardian.accountId());
        UUID childMemberId = insertChildMember(householdId);
        when(householdAccess.findMember(childMemberId)).thenReturn(Optional.of(
                new MemberInfo(childMemberId, householdId, null, "CHILD", true)));
        when(householdAccess.isGuardianOf(guardian.accountId(), childMemberId)).thenReturn(true);

        mockMvc.perform(put(profilePath(householdId, childMemberId))
                        .header("Authorization", "Bearer " + guardian.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"C4_6"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageBand").value("C4_6"));
    }

    // ---------------------------------------------------------------- closed vocabulary

    @Test
    @DisplayName("a goal code outside the closed vocabulary is rejected, not silently stored")
    void invalidGoalCodeRejected() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        MvcResult result = mockMvc.perform(put(profilePath(householdId, memberId))
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"A18_30","goals":["LOSE_WEIGHT_FAST"]}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        // The rejected value is never echoed back (same discipline as validation errors
        // elsewhere in this codebase — see AuthenticationIT).
        assertThat(result.getResponse().getContentAsString()).doesNotContain("LOSE_WEIGHT_FAST");
    }

    @Test
    @DisplayName("an allergen code outside the reference set is rejected")
    void invalidAllergenCodeRejected() throws Exception {
        Account owner = registerAccount();
        UUID householdId = insertHousehold(owner.accountId());
        UUID memberId = insertAdultMember(householdId, owner.accountId());
        when(householdAccess.findMember(memberId)).thenReturn(Optional.of(
                new MemberInfo(memberId, householdId, owner.accountId(), "ADULT", true)));

        mockMvc.perform(put(profilePath(householdId, memberId))
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"A18_30","allergens":["UNICORN_DANDER"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------ fixtures

    private String profilePath(UUID householdId, UUID memberId) {
        return "/api/v1/households/%s/members/%s/profile".formatted(householdId, memberId);
    }

    private record Account(UUID accountId, String accessToken) {}

    private Account registerAccount() throws Exception {
        String email = "profile-it-" + UUID.randomUUID() + "@example.com";
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
