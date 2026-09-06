package com.familyfood.datarights;

import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Child erasure (CH-008, FR-010's child-erasure gap) against a real database.
 *
 * <p>Distinct from {@code HouseholdIT}'s {@code removalRulesAreEnforced}: that test proves
 * {@code DELETE .../members/{memberId}} only ever soft-leaves a membership. These tests prove
 * the new {@code POST .../members/{memberId}/erase} endpoint actually erases the child's
 * profile, while leaving {@code consent_record} and {@code audit_event} exactly as AC-9.3
 * requires.
 */
class ChildErasureIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "child-erasure-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(UUID.fromString(node.get("accountId").asText()), node.get("accessToken").asText());
    }

    private UUID createHousehold(Session owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test household","ownerDisplayLabel":"Owner"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID addChild(Session guardian, UUID householdId, String label) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", guardian.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"CHILD","displayLabel":"%s","relationship":"PARENT","authorityBasis":"parent"}
                                """.formatted(label)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private Session addSecondAdult(Session owner, UUID householdId) throws Exception {
        MvcResult inviteResult = mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"ADULT","email":"second-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).get("token").asText();

        Session second = registerNewAccount();
        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", second.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Second Adult"}
                                """.formatted(token)))
                .andExpect(status().isOk());
        return second;
    }

    private void setProfile(Session caller, UUID householdId, UUID memberId) throws Exception {
        mockMvc.perform(put("/api/v1/households/{h}/members/{m}/profile", householdId, memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"C4_6","goals":[],"allergens":[],"conditions":[]}
                                """))
                .andExpect(status().isOk());
    }

    private void grantConsent(Session caller, UUID memberId, String purposeCode) throws Exception {
        mockMvc.perform(post("/api/v1/consent/members/{m}/grants", memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"%s"}
                                """.formatted(purposeCode)))
                .andExpect(status().isCreated());
    }

    private String erasePath(UUID householdId, UUID memberId) {
        return "/api/v1/households/%s/members/%s/erase".formatted(householdId, memberId);
    }

    // --------------------------------------------------------------------- happy path

    @Test
    @DisplayName("a guardian can erase their child: profile gone, consent/audit retained, guardian intact, child unreachable after")
    void guardianErasesChild() throws Exception {
        Session guardian = registerNewAccount();
        UUID householdId = createHousehold(guardian);
        UUID childId = addChild(guardian, householdId, "Kiddo");
        setProfile(guardian, householdId, childId);
        grantConsent(guardian, childId, "CHILD_FOOD_SUITABILITY");

        mockMvc.perform(post(erasePath(householdId, childId)).header("Authorization", guardian.authHeader()))
                .andExpect(status().isNoContent());

        Integer profileCount = jdbc.queryForObject(
                "select count(*) from person_profile where household_member_id = ?", Integer.class, childId);
        assertThat(profileCount).isZero();

        Map<String, Object> member = jdbc.queryForMap(
                "select display_label, left_at from household_member where id = ?", childId);
        assertThat(member.get("display_label")).isEqualTo("Erased member");
        assertThat(member.get("left_at")).isNotNull();

        Integer consentCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ? and purpose_code = 'CHILD_FOOD_SUITABILITY'",
                Integer.class, childId);
        assertThat(consentCount).isEqualTo(1);

        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit_event where resource_id = ? and action = 'CHILD_DATA_DELETED'",
                Integer.class, childId);
        assertThat(auditCount).isEqualTo(1);

        Integer guardianLinkActiveCount = jdbc.queryForObject(
                "select count(*) from guardian_link where child_member_id = ? and revoked_at is null",
                Integer.class, childId);
        assertThat(guardianLinkActiveCount).isZero();

        // The guardian's own account and household standing are untouched.
        String guardianStatus = jdbc.queryForObject(
                "select status from user_account where id = ?", String.class, guardian.accountId());
        assertThat(guardianStatus).isEqualTo("ACTIVE");

        // The erased child can no longer be read.
        mockMvc.perform(get("/api/v1/households/{h}/members/{m}/profile", householdId, childId)
                        .header("Authorization", guardian.authHeader()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------- IDOR

    @Test
    @DisplayName("an adult in the same household who is not this child's guardian cannot erase them")
    void nonGuardianAdultInSameHouseholdCannotErase() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID childId = addChild(owner, householdId, "Kiddo");
        Session otherAdult = addSecondAdult(owner, householdId);

        mockMvc.perform(post(erasePath(householdId, childId)).header("Authorization", otherAdult.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_GUARDIAN"));

        Boolean stillActive = jdbc.queryForObject(
                "select left_at is null from household_member where id = ?", Boolean.class, childId);
        assertThat(stillActive).isTrue();
    }

    @Test
    @DisplayName("an adult from an unrelated household cannot erase a child they have no membership in")
    void adultFromAnotherHouseholdCannotErase() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID childId = addChild(owner, householdId, "Kiddo");

        Session stranger = registerNewAccount();
        createHousehold(stranger); // a household of their own, unrelated

        mockMvc.perform(post(erasePath(householdId, childId)).header("Authorization", stranger.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_MEMBER"));

        Boolean stillActive = jdbc.queryForObject(
                "select left_at is null from household_member where id = ?", Boolean.class, childId);
        assertThat(stillActive).isTrue();
    }

    @Test
    @DisplayName("erasure cannot target an ADULT member — an adult's own data leaves only through account deletion")
    void cannotEraseAnAdultMember() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        Session otherAdult = addSecondAdult(owner, householdId);

        MvcResult listResult = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andReturn();
        JsonNode members = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID otherAdultMemberId = null;
        for (JsonNode m : members) {
            if (otherAdult.accountId().toString().equals(m.path("userAccountId").asText(null))) {
                otherAdultMemberId = UUID.fromString(m.get("id").asText());
            }
        }
        assertThat(otherAdultMemberId).isNotNull();

        mockMvc.perform(post(erasePath(householdId, otherAdultMemberId)).header("Authorization", owner.authHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("erasing an already-erased child a second time is a safe not-found, not a crash")
    void erasingAlreadyErasedChildIsNotFound() throws Exception {
        Session guardian = registerNewAccount();
        UUID householdId = createHousehold(guardian);
        UUID childId = addChild(guardian, householdId, "Kiddo");

        mockMvc.perform(post(erasePath(householdId, childId)).header("Authorization", guardian.authHeader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(erasePath(householdId, childId)).header("Authorization", guardian.authHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("erasing a child in one household never touches an unrelated child in another household")
    void erasureIsIsolatedToTheTargetHousehold() throws Exception {
        Session guardianA = registerNewAccount();
        UUID householdA = createHousehold(guardianA);
        UUID childA = addChild(guardianA, householdA, "Kiddo A");

        Session guardianB = registerNewAccount();
        UUID householdB = createHousehold(guardianB);
        UUID childB = addChild(guardianB, householdB, "Kiddo B");
        setProfile(guardianB, householdB, childB);

        mockMvc.perform(post(erasePath(householdA, childA)).header("Authorization", guardianA.authHeader()))
                .andExpect(status().isNoContent());

        Integer childBProfileCount = jdbc.queryForObject(
                "select count(*) from person_profile where household_member_id = ?", Integer.class, childB);
        assertThat(childBProfileCount).isEqualTo(1);
        Boolean childBActive = jdbc.queryForObject(
                "select left_at is null from household_member where id = ?", Boolean.class, childB);
        assertThat(childBActive).isTrue();
    }
}
