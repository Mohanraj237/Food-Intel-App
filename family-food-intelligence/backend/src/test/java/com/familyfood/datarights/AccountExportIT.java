package com.familyfood.datarights;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Data export (NFR-09, AC-9.2) against a real database: only the caller's own account,
 * households, profile history, consent ledger and contributions — never another person's,
 * even one sharing the same household.
 */
class AccountExportIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private record Session(UUID accountId, String email, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "export-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(UUID.fromString(node.get("accountId").asText()), email, node.get("accessToken").asText());
    }

    private UUID createHousehold(Session owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Export Household","ownerDisplayLabel":"Owner"}
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

    private void setProfile(Session caller, UUID householdId, UUID memberId, String ageBand) throws Exception {
        mockMvc.perform(put("/api/v1/households/{h}/members/{m}/profile", householdId, memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"%s","goals":[],"allergens":[],"conditions":[]}
                                """.formatted(ageBand)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------- scope

    @Test
    @DisplayName("export returns the caller's own account, household membership, profile and consent — no secrets")
    void exportReturnsOwnData() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        MvcResult membersResult = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andReturn();
        UUID memberId = UUID.fromString(
                objectMapper.readTree(membersResult.getResponse().getContentAsString()).get(0).get("id").asText());
        setProfile(owner, householdId, memberId, "A31_45");
        mockMvc.perform(post("/api/v1/consent/members/{m}/grants", memberId)
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"PERSONAL_FIT"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"Test Snack"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/account/export").header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accountId").value(owner.accountId().toString()))
                .andExpect(jsonPath("$.account.email").value(owner.email()))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.households.length()").value(1))
                .andExpect(jsonPath("$.households[0].householdId").value(householdId.toString()))
                .andExpect(jsonPath("$.households[0].role").value("OWNER"))
                .andExpect(jsonPath("$.households[0].profileVersions.length()").value(1))
                .andExpect(jsonPath("$.households[0].profileVersions[0].ageBand").value("A31_45"))
                .andExpect(jsonPath("$.households[0].consent.length()").value(1))
                .andExpect(jsonPath("$.households[0].consent[0].purposeCode").value("PERSONAL_FIT"))
                .andExpect(jsonPath("$.households[0].consent[0].evidence").exists())
                .andExpect(jsonPath("$.contributions.length()").value(1))
                .andExpect(jsonPath("$.contributions[0].productName").value("Test Snack"));
    }

    @Test
    @DisplayName("a guardian's export includes the children they guard; a non-guardian co-member's export does not")
    void exportIncludesGuardedChildrenOnlyForTheirGuardian() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID childId = addChild(owner, householdId, "Kiddo");
        setProfile(owner, householdId, childId, "C4_6");
        Session otherAdult = addSecondAdult(owner, householdId);

        mockMvc.perform(get("/api/v1/account/export").header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.households[0].guardedChildren.length()").value(1))
                .andExpect(jsonPath("$.households[0].guardedChildren[0].memberId").value(childId.toString()))
                .andExpect(jsonPath("$.households[0].guardedChildren[0].profileVersions[0].ageBand").value("C4_6"));

        // The non-guardian co-member belongs to the same household, but is not this
        // child's guardian — their own export must not surface the child at all.
        mockMvc.perform(get("/api/v1/account/export").header("Authorization", otherAdult.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.households[0].guardedChildren.length()").value(0))
                .andExpect(jsonPath("$.households[0].role").value("ADULT"));
    }

    @Test
    @DisplayName("a household member's export never contains another member's profile")
    void exportNeverContainsAnotherMembersProfile() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        Session otherAdult = addSecondAdult(owner, householdId);

        MvcResult membersResult = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andReturn();
        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        UUID otherMemberId = null;
        for (JsonNode m : members) {
            if (otherAdult.accountId().toString().equals(m.path("userAccountId").asText(null))) {
                otherMemberId = UUID.fromString(m.get("id").asText());
            }
        }
        assertThat(otherMemberId).isNotNull();
        setProfile(otherAdult, householdId, otherMemberId, "A46_60");

        // The owner's export names their own membership only — never otherMemberId, and
        // carries no profile-version row for anyone but the owner.
        MvcResult exportResult = mockMvc.perform(get("/api/v1/account/export")
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.households.length()").value(1))
                .andReturn();
        JsonNode household = objectMapper.readTree(exportResult.getResponse().getContentAsString())
                .get("households").get(0);
        assertThat(household.get("memberId").asText()).isNotEqualTo(otherMemberId.toString());
        for (JsonNode version : household.get("profileVersions")) {
            assertThat(version.get("householdMemberId").asText()).isNotEqualTo(otherMemberId.toString());
        }
    }

    @Test
    @DisplayName("an unauthenticated export request is rejected")
    void unauthenticatedExportRejected() throws Exception {
        mockMvc.perform(get("/api/v1/account/export"))
                .andExpect(status().isUnauthorized());
    }
}
