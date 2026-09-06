package com.familyfood.household;

import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Household, membership, invitation and guardian-link behaviour against a real database.
 *
 * <p>The isolation tests are the ones that matter most: a caller who is not a live member
 * of a household must never be able to read or mutate it (Gate-2 IDOR posture), regardless
 * of how plausible the household id in the URL looks.
 */
class HouseholdIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HouseholdAccess householdAccess;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
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

    private UUID createHousehold(Session owner, String name, String ownerLabel) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerDisplayLabel":"%s"}
                                """.formatted(name, ownerLabel)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode inviteAdult(Session inviter, UUID householdId, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", inviter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"ADULT","email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // -------------------------------------------------------------- create household

    @Test
    @DisplayName("creating a household makes the caller its OWNER, alone — a valid terminal state")
    void createHouseholdCreatesOwnerMembership() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].memberType").value("ADULT"))
                .andExpect(jsonPath("$[0].userAccountId").value(owner.accountId().toString()))
                .andExpect(jsonPath("$[0].displayLabel").value("Me"));
    }

    // --------------------------------------------------------------- invite + accept

    @Test
    @DisplayName("inviting an adult creates only an invitation; accepting creates their own member row (D-002)")
    void inviteAndAcceptFlow() throws Exception {
        Session owner = registerNewAccount();
        Session invitee = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        // Before acceptance: still exactly one member. Inviting creates no member/profile.
        mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(jsonPath("$.length()").value(1));

        JsonNode invitation = inviteAdult(owner, householdId, "invitee-" + UUID.randomUUID() + "@example.com");
        String token = invitation.get("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Invitee"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADULT"))
                .andExpect(jsonPath("$.memberType").value("ADULT"))
                .andExpect(jsonPath("$.userAccountId").value(invitee.accountId().toString()));

        mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("accepting an already-accepted invitation a second time is rejected")
    void duplicateInvitationAcceptRejected() throws Exception {
        Session owner = registerNewAccount();
        Session invitee = registerNewAccount();
        Session other = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        JsonNode invitation = inviteAdult(owner, householdId, "invitee-" + UUID.randomUUID() + "@example.com");
        String token = invitation.get("token").asText();

        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Invitee"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        // Same token, a different account — reuse must not be allowed either.
        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", other.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Other"}
                                """.formatted(token)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    @DisplayName("an account that already has a live membership cannot accept a second invitation to the same household")
    void acceptingWhileAlreadyAMemberIsRejected() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        JsonNode invitation = inviteAdult(owner, householdId, "someone-" + UUID.randomUUID() + "@example.com");
        String token = invitation.get("token").asText();

        // The owner is already a live member of this household.
        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Me Again"}
                                """.formatted(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_HOUSEHOLD_MEMBER"));
    }

    // -------------------------------------------------------------------- add child

    @Test
    @DisplayName("adding a child creates a dependent member with no account and an active guardian link")
    void addChildCreatesDependentAndGuardianLink() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        MvcResult result = mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"CHILD","displayLabel":"Kiddo","relationship":"PARENT","authorityBasis":"parent"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberType").value("CHILD"))
                .andExpect(jsonPath("$.role").value("DEPENDENT"))
                .andExpect(jsonPath("$.userAccountId").doesNotExist())
                .andExpect(jsonPath("$.displayLabel").value("Kiddo"))
                .andReturn();

        UUID childMemberId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        assertThat(householdAccess.isGuardianOf(owner.accountId(), childMemberId)).isTrue();

        Session stranger = registerNewAccount();
        assertThat(householdAccess.isGuardianOf(stranger.accountId(), childMemberId)).isFalse();

        HouseholdAccess.MemberInfo info = householdAccess.findMember(childMemberId).orElseThrow();
        assertThat(info.memberType()).isEqualTo("CHILD");
        assertThat(info.userAccountId()).isNull();
        assertThat(info.active()).isTrue();
        assertThat(info.householdId()).isEqualTo(householdId);
    }

    // ------------------------------------------------------------------ isolation

    @Test
    @DisplayName("a caller who is not a member of the household is denied on every mutating and read path (Gate-2 IDOR)")
    void nonMemberIsDeniedEverywhere() throws Exception {
        Session owner = registerNewAccount();
        Session outsider = registerNewAccount();
        UUID householdId = createHousehold(owner, "Household A", "Me");

        mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", outsider.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_MEMBER"));

        mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", outsider.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"CHILD","displayLabel":"Intruder Kid","relationship":"PARENT","authorityBasis":"parent"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_MEMBER"));

        mockMvc.perform(delete("/api/v1/households/{id}/members/{memberId}", householdId, UUID.randomUUID())
                        .header("Authorization", outsider.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_MEMBER"));

        mockMvc.perform(post("/api/v1/households/{id}/invitations/{invId}/revoke",
                        householdId, UUID.randomUUID())
                        .header("Authorization", outsider.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_MEMBER"));
    }

    // ----------------------------------------------------------------- remove member

    @Test
    @DisplayName("only the OWNER may remove another member; any member may remove themselves")
    void removalRulesAreEnforced() throws Exception {
        Session owner = registerNewAccount();
        Session adult = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        JsonNode invitation = inviteAdult(owner, householdId, "adult-" + UUID.randomUUID() + "@example.com");
        MvcResult acceptResult = mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", adult.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Adult"}
                                """.formatted(invitation.get("token").asText())))
                .andExpect(status().isOk())
                .andReturn();
        UUID adultMemberId = UUID.fromString(
                objectMapper.readTree(acceptResult.getResponse().getContentAsString()).get("id").asText());

        // Find the owner's own member id.
        MvcResult listResult = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andReturn();
        JsonNode membersNode = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID ownerMemberId = null;
        for (JsonNode m : membersNode) {
            if (m.get("role").asText().equals("OWNER")) {
                ownerMemberId = UUID.fromString(m.get("id").asText());
            }
        }
        assertThat(ownerMemberId).isNotNull();

        // Non-owner cannot remove someone else.
        mockMvc.perform(delete("/api/v1/households/{hid}/members/{mid}", householdId, ownerMemberId)
                        .header("Authorization", adult.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Any member may remove themselves.
        mockMvc.perform(delete("/api/v1/households/{hid}/members/{mid}", householdId, adultMemberId)
                        .header("Authorization", adult.authHeader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(jsonPath("$.length()").value(1));

        // The OWNER may remove another member (re-invite and accept, then owner removes).
        JsonNode secondInvitation = inviteAdult(owner, householdId, "adult2-" + UUID.randomUUID() + "@example.com");
        Session secondAdult = registerNewAccount();
        MvcResult secondAccept = mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", secondAdult.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Second Adult"}
                                """.formatted(secondInvitation.get("token").asText())))
                .andExpect(status().isOk())
                .andReturn();
        UUID secondAdultMemberId = UUID.fromString(
                objectMapper.readTree(secondAccept.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(delete("/api/v1/households/{hid}/members/{mid}", householdId, secondAdultMemberId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isNoContent());
    }

    // -------------------------------------------------------------- revoke invitation

    @Test
    @DisplayName("a revoked invitation can no longer be accepted")
    void revokedInvitationCannotBeAccepted() throws Exception {
        Session owner = registerNewAccount();
        Session invitee = registerNewAccount();
        UUID householdId = createHousehold(owner, "The Test Household", "Me");

        JsonNode invitation = inviteAdult(owner, householdId, "invitee-" + UUID.randomUUID() + "@example.com");
        UUID invitationId = UUID.fromString(invitation.get("invitationId").asText());
        String token = invitation.get("token").asText();

        mockMvc.perform(post("/api/v1/households/{hid}/invitations/{iid}/revoke", householdId, invitationId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Invitee"}
                                """.formatted(token)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }
}
