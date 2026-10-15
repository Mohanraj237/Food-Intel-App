package com.familyfood.datarights;

import com.familyfood.datarights.application.DataRightsService;
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
 * Account deletion (NFR-09, AC-9.3) against a real database.
 *
 * <p>{@link DataRightsService#performFullErasure} is called directly rather than through the
 * scheduler, to exercise the hard-erasure step without waiting on the real 30-day grace
 * period ({@code familyfood.privacy.erasure-grace-period}) — the request phase (blocking
 * sign-in, revoking sessions) is exercised through the real HTTP endpoint, exactly as a
 * caller would trigger it.
 */
class AccountErasureIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataRightsService dataRights;

    private record Session(UUID accountId, String email, String accessToken, String refreshToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "erasure-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(UUID.fromString(node.get("accountId").asText()), email,
                node.get("accessToken").asText(), node.get("refreshToken").asText());
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

    private UUID ownMemberId(Session session, UUID householdId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", session.authHeader()))
                .andReturn();
        JsonNode members = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode m : members) {
            if (session.accountId().toString().equals(m.path("userAccountId").asText(null))) {
                return UUID.fromString(m.get("id").asText());
            }
        }
        throw new IllegalStateException("caller has no membership in " + householdId);
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

    private Session acceptInvitation(String token, String displayLabel) throws Exception {
        Session invitee = registerNewAccount();
        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"%s"}
                                """.formatted(token, displayLabel)))
                .andExpect(status().isOk());
        return invitee;
    }

    private void setProfile(Session caller, UUID householdId, UUID memberId) throws Exception {
        mockMvc.perform(put("/api/v1/households/{h}/members/{m}/profile", householdId, memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"A31_45","goals":["INCREASE_FIBRE"],"allergens":[],"conditions":[]}
                                """))
                .andExpect(status().isOk());
    }

    /**
     * A child member's age band must satisfy {@code AgeBand.isChild()} or the profile
     * write is rejected with {@code AGE_BAND_NOT_VALID_FOR_MEMBER} (422) —
     * {@link #setProfile} above is adult-only for that reason. Mirrors the convention
     * already used by {@code ChildErasureIT.setProfile}.
     */
    private void setChildProfile(Session caller, UUID householdId, UUID memberId) throws Exception {
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

    // -------------------------------------------------------------- request phase

    @Test
    @DisplayName("requesting deletion blocks sign-in immediately and revokes every session")
    void requestingDeletionBlocksLoginAndRevokesSessions() throws Exception {
        Session session = registerNewAccount();

        mockMvc.perform(post("/api/v1/account/deletion").header("Authorization", session.authHeader()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("DELETION_REQUESTED"))
                .andExpect(jsonPath("$.erasureEffectiveAt").exists());

        // The refresh token issued at registration is revoked immediately.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(session.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // Signing back in with the correct password is refused, indistinguishably from a
        // wrong password — the account exists but is no longer usable.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(session.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String status = jdbc.queryForObject(
                "select status from user_account where id = ?", String.class, session.accountId());
        assertThat(status).isEqualTo("DELETION_REQUESTED");
    }

    @Test
    @DisplayName("an unauthenticated deletion request is rejected")
    void unauthenticatedDeletionRequestRejected() throws Exception {
        mockMvc.perform(post("/api/v1/account/deletion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("requesting deletion twice is safe — idempotent, no error, no double side effect")
    void repeatedDeletionRequestIsSafe() throws Exception {
        Session session = registerNewAccount();

        mockMvc.perform(post("/api/v1/account/deletion").header("Authorization", session.authHeader()))
                .andExpect(status().isAccepted());
        // The access token itself is still valid (JWTs are not revoked, only refresh
        // tokens) — a second call with the same token must not throw or corrupt state.
        mockMvc.perform(post("/api/v1/account/deletion").header("Authorization", session.authHeader()))
                .andExpect(status().isAccepted());

        String status = jdbc.queryForObject(
                "select status from user_account where id = ?", String.class, session.accountId());
        assertThat(status).isEqualTo("DELETION_REQUESTED");
    }

    // ---------------------------------------------------------------- hard erasure

    @Test
    @DisplayName("hard erasure deletes the profile, anonymises the account, and leaves consent_record + audit_event")
    void hardErasureDeletesPersonalDataButPreservesConsentAndAudit() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner, "Erasure Household", "Owner");
        UUID memberId = ownMemberId(owner, householdId);
        setProfile(owner, householdId, memberId);
        grantConsent(owner, memberId, "PERSONAL_FIT");

        String originalEmail = jdbc.queryForObject(
                "select email from user_account where id = ?", String.class, owner.accountId());

        dataRights.performFullErasure(owner.accountId());

        Map<String, Object> account = jdbc.queryForMap(
                "select email, status, phone_hash from user_account where id = ?", owner.accountId());
        assertThat(account.get("email")).isNotEqualTo(originalEmail);
        assertThat(account.get("status")).isEqualTo("ERASED");
        assertThat(account.get("phone_hash")).isNull();

        Map<String, Object> member = jdbc.queryForMap(
                "select display_label, left_at from household_member where id = ?", memberId);
        assertThat(member.get("display_label")).isEqualTo("Erased member");
        assertThat(member.get("left_at")).isNotNull();

        Integer profileCount = jdbc.queryForObject(
                "select count(*) from person_profile where household_member_id = ?", Integer.class, memberId);
        assertThat(profileCount).isZero();

        Integer consentCount = jdbc.queryForObject(
                "select count(*) from consent_record where subject_member_id = ? and purpose_code = 'PERSONAL_FIT'",
                Integer.class, memberId);
        assertThat(consentCount).isEqualTo(1);

        Integer accountDeletedAudit = jdbc.queryForObject(
                "select count(*) from audit_event where actor_user_id = ? and action = 'ACCOUNT_DELETED'",
                Integer.class, owner.accountId());
        assertThat(accountDeletedAudit).isEqualTo(1);

        Integer registeredAudit = jdbc.queryForObject(
                "select count(*) from audit_event where actor_user_id = ? and action = 'ACCOUNT_REGISTERED'",
                Integer.class, owner.accountId());
        assertThat(registeredAudit).isEqualTo(1);
    }

    @Test
    @DisplayName("erasing one adult's account never touches another household member, a child, or another household")
    void hardErasureDoesNotAffectUnrelatedData() throws Exception {
        // Household A: A1 (owner, to be erased), A2 (adult), a child.
        Session a1 = registerNewAccount();
        UUID householdA = createHousehold(a1, "Household A", "A1");
        JsonNode invitation = inviteAdult(a1, householdA, "a2-" + UUID.randomUUID() + "@example.com");
        Session a2 = acceptInvitation(invitation.get("token").asText(), "A2");
        UUID a2MemberId = ownMemberId(a2, householdA);
        setProfile(a2, householdA, a2MemberId);
        grantConsent(a2, a2MemberId, "PERSONAL_FIT");

        MvcResult childResult = mockMvc.perform(post("/api/v1/households/{id}/members", householdA)
                        .header("Authorization", a1.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"CHILD","displayLabel":"Kiddo A","relationship":"PARENT","authorityBasis":"parent"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID childAId = UUID.fromString(
                objectMapper.readTree(childResult.getResponse().getContentAsString()).get("id").asText());
        setChildProfile(a1, householdA, childAId);

        // Household B: an unrelated adult and child.
        Session b1 = registerNewAccount();
        UUID householdB = createHousehold(b1, "Household B", "B1");
        UUID b1MemberId = ownMemberId(b1, householdB);
        setProfile(b1, householdB, b1MemberId);

        String a1MemberDisplayLabelBefore = jdbc.queryForObject(
                "select display_label from household_member where household_id = ? and user_account_id = ?",
                String.class, householdA, a1.accountId());
        assertThat(a1MemberDisplayLabelBefore).isEqualTo("A1");

        dataRights.performFullErasure(a1.accountId());

        // A2 is untouched: profile survives, still an active member, can still sign in.
        Integer a2ProfileCount = jdbc.queryForObject(
                "select count(*) from person_profile where household_member_id = ?", Integer.class, a2MemberId);
        assertThat(a2ProfileCount).isEqualTo(1);
        String a2Status = jdbc.queryForObject(
                "select status from user_account where id = ?", String.class, a2.accountId());
        assertThat(a2Status).isEqualTo("ACTIVE");

        // The child in household A is untouched — account erasure never erases a child.
        Integer childAProfileCount = jdbc.queryForObject(
                "select count(*) from person_profile where household_member_id = ?", Integer.class, childAId);
        assertThat(childAProfileCount).isEqualTo(1);
        Boolean childAStillActive = jdbc.queryForObject(
                "select left_at is null from household_member where id = ?", Boolean.class, childAId);
        assertThat(childAStillActive).isTrue();

        // Household B is entirely untouched.
        Integer b1ProfileCount = jdbc.queryForObject(
                "select count(*) from person_profile where household_member_id = ?", Integer.class, b1MemberId);
        assertThat(b1ProfileCount).isEqualTo(1);
        String b1Status = jdbc.queryForObject(
                "select status from user_account where id = ?", String.class, b1.accountId());
        assertThat(b1Status).isEqualTo("ACTIVE");

        // A1 itself: anonymised, membership scrubbed.
        String a1MemberLabelAfter = jdbc.queryForObject(
                "select display_label from household_member where household_id = ? and user_account_id = ?",
                String.class, householdA, a1.accountId());
        assertThat(a1MemberLabelAfter).isEqualTo("Erased member");
    }
}
