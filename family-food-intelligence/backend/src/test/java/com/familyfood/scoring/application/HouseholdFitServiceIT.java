package com.familyfood.scoring.application;

import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link HouseholdFitService} against a real database and the real household module.
 *
 * <p>These are the tests with real security weight (D-002, D-024 point 4): household fit is
 * caller-scoped, never "everyone in the household". Every scenario below is a variant of one
 * question — does the caller see exactly their own row plus the children they guard, and
 * nothing else, ever — and each one is written to fail loudly (a wrong member id in the
 * result, or a missing 403) rather than merely pass by omission.
 */
class HouseholdFitServiceIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HouseholdFitService householdFitService;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "hf-it-" + UUID.randomUUID() + "@example.com";
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

    private UUID createHousehold(Session owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerDisplayLabel":"Me"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    /** Invites {@code invitee} into {@code householdId} and accepts on their behalf, returning their member id. */
    private UUID inviteAndAcceptAdult(Session inviter, Session invitee, UUID householdId) throws Exception {
        MvcResult inviteResult = mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", inviter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"ADULT","email":"invitee-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).get("token").asText();

        MvcResult acceptResult = mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"Invitee"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(acceptResult.getResponse().getContentAsString()).get("id").asText());
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

    private UUID ownerMemberId(Session owner, UUID householdId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode membersNode = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode m : membersNode) {
            if (m.get("role").asText().equals("OWNER")) {
                return UUID.fromString(m.get("id").asText());
            }
        }
        throw new IllegalStateException("no OWNER member found for household " + householdId);
    }

    private void removeMember(Session actingOwnerOrSelf, UUID householdId, UUID memberId) throws Exception {
        mockMvc.perform(delete("/api/v1/households/{hid}/members/{mid}", householdId, memberId)
                        .header("Authorization", actingOwnerOrSelf.authHeader()))
                .andExpect(status().isNoContent());
    }

    private ProductAssessmentInput sampleInput() {
        return new ProductAssessmentInput(UUID.randomUUID(), "SNACK", List.of(), List.of());
    }

    private static List<UUID> memberIds(HouseholdFitView view) {
        return view.members().stream().map(PersonalFitView::memberId).toList();
    }

    // ------------------------------------------------------------------- household isolation

    @Test
    @DisplayName("a live member of household A gets NOT_HOUSEHOLD_MEMBER asking for household B's fit, "
            + "even though B is real and populated")
    void householdIsolationDeniesRealButForeignHousehold() throws Exception {
        Session ownerA = registerNewAccount();
        UUID householdA = createHousehold(ownerA, "Household A");

        Session ownerB = registerNewAccount();
        Session adultB = registerNewAccount();
        UUID householdB = createHousehold(ownerB, "Household B");
        inviteAndAcceptAdult(ownerB, adultB, householdB);
        addChild(ownerB, householdB, "Kid B");

        assertThatThrownBy(() -> householdFitService.evaluate(ownerA.accountId(), householdB, sampleInput()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.NOT_HOUSEHOLD_MEMBER);
    }

    // ------------------------------------------------------- caller-scoped visibility (D-002)

    @Test
    @DisplayName("caller sees only their own adult row plus the children they guard — never another adult")
    void callerSeesOnlySelfAndGuardedChildren() throws Exception {
        Session adult1 = registerNewAccount();
        Session adult2 = registerNewAccount();
        UUID householdId = createHousehold(adult1, "Two Adults One Child");

        UUID adult1MemberId = ownerMemberId(adult1, householdId);
        UUID adult2MemberId = inviteAndAcceptAdult(adult1, adult2, householdId);
        UUID childMemberId = addChild(adult1, householdId, "Kiddo"); // guarded by adult1 only

        HouseholdFitView asAdult1 = householdFitService.evaluate(adult1.accountId(), householdId, sampleInput());
        assertThat(memberIds(asAdult1)).containsExactlyInAnyOrder(adult1MemberId, childMemberId);
        assertThat(memberIds(asAdult1)).doesNotContain(adult2MemberId);

        HouseholdFitView asAdult2 = householdFitService.evaluate(adult2.accountId(), householdId, sampleInput());
        assertThat(memberIds(asAdult2)).containsExactly(adult2MemberId);
    }

    @Test
    @DisplayName("a child guarded by only one of two guardians is excluded from the unlinked guardian's result")
    void unlinkedGuardianDoesNotSeeAnotherGuardiansChild() throws Exception {
        Session guardian1 = registerNewAccount();
        Session guardian2 = registerNewAccount();
        UUID householdId = createHousehold(guardian1, "Two Guardians One Child");

        UUID guardian1MemberId = ownerMemberId(guardian1, householdId);
        UUID guardian2MemberId = inviteAndAcceptAdult(guardian1, guardian2, householdId);
        UUID childMemberId = addChild(guardian1, householdId, "Kiddo"); // link created for guardian1 only

        HouseholdFitView asGuardian2 = householdFitService.evaluate(guardian2.accountId(), householdId, sampleInput());
        assertThat(memberIds(asGuardian2)).containsExactly(guardian2MemberId);
        assertThat(memberIds(asGuardian2)).doesNotContain(childMemberId);

        HouseholdFitView asGuardian1 = householdFitService.evaluate(guardian1.accountId(), householdId, sampleInput());
        assertThat(memberIds(asGuardian1)).containsExactlyInAnyOrder(guardian1MemberId, childMemberId);
    }

    // --------------------------------------------------------------------- departed members

    @Test
    @DisplayName("a departed member is never included in the result and can never call this as caller")
    void departedMemberExcludedAndDeniedAsCaller() throws Exception {
        Session owner = registerNewAccount();
        Session adult = registerNewAccount();
        UUID householdId = createHousehold(owner, "Household With A Leaver");

        UUID ownerMemberId = ownerMemberId(owner, householdId);
        UUID adultMemberId = inviteAndAcceptAdult(owner, adult, householdId);

        // The adult leaves (self-removal).
        removeMember(adult, householdId, adultMemberId);

        HouseholdFitView asOwner = householdFitService.evaluate(owner.accountId(), householdId, sampleInput());
        assertThat(memberIds(asOwner)).containsExactly(ownerMemberId);
        assertThat(memberIds(asOwner)).doesNotContain(adultMemberId);

        assertThatThrownBy(() -> householdFitService.evaluate(adult.accountId(), householdId, sampleInput()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.NOT_HOUSEHOLD_MEMBER);
    }

    // ------------------------------------------------------------------------- determinism

    @Test
    @DisplayName("evaluate is deterministic: identical inputs produce byte-for-byte identical content, "
            + "aside from the computedAt timestamps")
    void evaluateIsDeterministic() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner, "Determinism Household");
        addChild(owner, householdId, "Kiddo");

        ProductAssessmentInput input = sampleInput();

        HouseholdFitView first = householdFitService.evaluate(owner.accountId(), householdId, input);
        HouseholdFitView second = householdFitService.evaluate(owner.accountId(), householdId, input);

        assertThat(first.householdId()).isEqualTo(second.householdId());
        assertThat(first.productVersionId()).isEqualTo(second.productVersionId());
        assertThat(first.members())
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*[Cc]omputedAt.*")
                .isEqualTo(second.members());
        // Order itself must be stable too — usingRecursiveComparison on a List checks order,
        // so the assertion above already covers it, but assert member ids explicitly as well
        // for a readable failure if it regresses.
        assertThat(memberIds(first)).isEqualTo(memberIds(second));
    }
}
