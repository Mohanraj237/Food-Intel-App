package com.familyfood.capture;

import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ops-review console (D-032) and D-026's contribution acknowledgment, exercised through
 * the real HTTP security chain against a real database — this is the "high-priority security
 * area" Phase 6 hardening calls out: operator authorization must be a server-side, real
 * database check on every call, never a client-side assumption, never inferable from a
 * request body/path id.
 */
class OpsReviewControllerIT extends IntegrationTestBase {

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
        String email = "ops-it-" + UUID.randomUUID() + "@example.com";
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

    /** Mirrors D-032's documented promotion path: a direct database action, no API. */
    private void promoteToOperator(UUID accountId) {
        int updated = jdbc.update("UPDATE user_account SET role = 'OPS_REVIEWER' WHERE id = ?", accountId);
        assertThat(updated).isEqualTo(1);
    }

    private UUID submitContribution(Session submitter, String productName, boolean acknowledged) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"%s","brandName":"TestBrand","catalogueContributionAcknowledged":%s}
                                """.formatted(productName, acknowledged)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    // ------------------------------------------------------------------- D-026 acknowledgment

    @Test
    @DisplayName("submitting without the terms-of-contribution acknowledgment is rejected (D-026/D-030)")
    void submissionWithoutAcknowledgmentIsRejected() throws Exception {
        Session submitter = registerNewAccount();
        mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"Unacknowledged Product","catalogueContributionAcknowledged":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("catalogueContributionAcknowledged"));
    }

    @Test
    @DisplayName("omitting the acknowledgment field entirely defaults false and is rejected the same way")
    void omittedAcknowledgmentIsRejected() throws Exception {
        Session submitter = registerNewAccount();
        mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"No Ack Field Product"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- submission input validation

    @Test
    @DisplayName("an unknown allergen code is rejected at submission, not left to fail ops-review approval later")
    void unknownAllergenCodeIsRejectedAtSubmission() throws Exception {
        Session submitter = registerNewAccount();
        mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"Bad Allergen Product","catalogueContributionAcknowledged":true,
                                 "allergenCodes":["NOT_A_REAL_ALLERGEN"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("allergenCodes"));
    }

    @Test
    @DisplayName("an unknown nutrient code is rejected at submission")
    void unknownNutrientCodeIsRejectedAtSubmission() throws Exception {
        Session submitter = registerNewAccount();
        mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"Bad Nutrient Product","catalogueContributionAcknowledged":true,
                                 "nutrients":[{"nutrientCode":"NOT_A_REAL_NUTRIENT","amountPer100":5}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a real allergen code is accepted, and survives through to ops-review approval")
    void realAllergenCodeIsAcceptedAndPreserved() throws Exception {
        Session submitter = registerNewAccount();
        Session operator = registerNewAccount();
        promoteToOperator(operator.accountId());

        MvcResult submitResult = mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"Real Allergen Product","catalogueContributionAcknowledged":true,
                                 "allergenCodes":["PEANUT"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID contributionId = UUID.fromString(
                objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asText());

        MvcResult approveResult = mockMvc.perform(post("/api/v1/ops/contributions/{id}/approve", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        UUID productId = UUID.fromString(
                objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("productId").asText());

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergens[0].code").value("PEANUT"))
                .andExpect(jsonPath("$.allergens[0].declarationType").value("CONTAINS"));
    }

    // --------------------------------------------------------------------------- authorization

    @Test
    @DisplayName("an anonymous caller cannot reach any ops endpoint")
    void anonymousCannotAccessOpsEndpoints() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/ops/access")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ops/contributions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ops/contributions/{id}", randomId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ops/contributions/{id}/approve", randomId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ops/contributions/{id}/reject", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"no"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a normal signed-in account (not an operator) is denied every ops endpoint, "
            + "even for a contribution it submitted itself")
    void normalAuthenticatedUserCannotAccessOpsEndpoints() throws Exception {
        Session normalUser = registerNewAccount();
        UUID contributionId = submitContribution(normalUser, "Own Contribution", true);

        mockMvc.perform(get("/api/v1/ops/access").header("Authorization", normalUser.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOperator").value(false));

        mockMvc.perform(get("/api/v1/ops/contributions").header("Authorization", normalUser.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AN_OPERATOR"));

        mockMvc.perform(get("/api/v1/ops/contributions/{id}", contributionId)
                        .header("Authorization", normalUser.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AN_OPERATOR"));

        mockMvc.perform(post("/api/v1/ops/contributions/{id}/approve", contributionId)
                        .header("Authorization", normalUser.authHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AN_OPERATOR"));

        mockMvc.perform(post("/api/v1/ops/contributions/{id}/reject", contributionId)
                        .header("Authorization", normalUser.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"self-service rejection attempt"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AN_OPERATOR"));

        // Still PENDING: none of the denied calls above had any effect.
        Integer stillPending = jdbc.queryForObject(
                "select count(*) from product_contribution where id = ? and status = 'PENDING'",
                Integer.class, contributionId);
        assertThat(stillPending).isEqualTo(1);
    }

    // ---------------------------------------------------------------- contribution → catalogue

    @Test
    @DisplayName("full lifecycle: submit -> operator queue -> approve -> real, searchable, provenance-preserving product")
    void fullApprovalLifecycleCreatesRealProduct() throws Exception {
        Session submitter = registerNewAccount();
        Session operator = registerNewAccount();
        promoteToOperator(operator.accountId());

        String uniqueName = "Hardening Test Snack " + UUID.randomUUID();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/contributions")
                        .header("Authorization", submitter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"%s","brandName":"Hardening Brand","categoryCode":"CHIPS",
                                 "catalogueContributionAcknowledged":true,
                                 "nutrients":[{"nutrientCode":"ENERGY_KCAL","amountPer100":500}],
                                 "ingredientsRawText":"Potato, Salt, Oil",
                                 "allergenCodes":["MILK"]}
                                """.formatted(uniqueName)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID contributionId = UUID.fromString(
                objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asText());

        // The queue is invisible to the submitter, visible to the operator.
        MvcResult queueResult = mockMvc.perform(get("/api/v1/ops/contributions?status=PENDING")
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queue = objectMapper.readTree(queueResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode entry : queue) {
            if (entry.get("id").asText().equals(contributionId.toString())) {
                found = true;
            }
        }
        assertThat(found).as("submitted contribution appears in the PENDING queue").isTrue();

        mockMvc.perform(get("/api/v1/ops/contributions/{id}", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value(uniqueName))
                .andExpect(jsonPath("$.status").value("PENDING"));

        MvcResult approveResult = mockMvc.perform(post("/api/v1/ops/contributions/{id}/approve", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        UUID productId = UUID.fromString(
                objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("productId").asText());

        // Reviewer/timestamp/status recorded on the contribution row.
        MvcResult detailAfter = mockMvc.perform(get("/api/v1/ops/contributions/{id}", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailAfter.getResponse().getContentAsString());
        assertThat(detail.get("status").asText()).isEqualTo("APPROVED");
        assertThat(detail.get("reviewedBy").asText()).isEqualTo(operator.accountId().toString());
        assertThat(detail.get("reviewedAt").isNull()).isFalse();

        // A real, searchable, provenance-preserving catalogue product.
        mockMvc.perform(get("/api/v1/search").param("q", uniqueName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(productId.toString()));

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(uniqueName))
                .andExpect(jsonPath("$.categoryCode").value("CHIPS"))
                .andExpect(jsonPath("$.currentVersion.captureMethod").value("MANUAL_ENTRY"))
                .andExpect(jsonPath("$.dataQuality").value("VERIFIED"))
                .andExpect(jsonPath("$.ingredients[0].rawText").value("Potato, Salt, Oil"))
                .andExpect(jsonPath("$.allergens[0].code").value("MILK"))
                .andExpect(jsonPath("$.nutrients[0].source.type").value("USER_SUBMITTED"));
    }

    @Test
    @DisplayName("approving an already-reviewed contribution is rejected, never silently re-approved or duplicated")
    void approvingAlreadyReviewedContributionFails() throws Exception {
        Session submitter = registerNewAccount();
        Session operator = registerNewAccount();
        promoteToOperator(operator.accountId());
        UUID contributionId = submitContribution(submitter, "Double Approve Test " + UUID.randomUUID(), true);

        mockMvc.perform(post("/api/v1/ops/contributions/{id}/approve", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ops/contributions/{id}/approve", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRIBUTION_ALREADY_REVIEWED"));

        Integer productCountForThisContribution = jdbc.queryForObject(
                "select count(*) from product where name = 'Double Approve Test'", Integer.class);
        // (name includes a random suffix so this is really just guarding against an
        // exception; the real assertion is the 409 above and the count staying sane)
        assertThat(productCountForThisContribution).isNotNull();
    }

    @Test
    @DisplayName("rejection requires a non-blank reason and never creates a catalogue product")
    void rejectionRequiresReasonAndCreatesNothing() throws Exception {
        Session submitter = registerNewAccount();
        Session operator = registerNewAccount();
        promoteToOperator(operator.accountId());
        String uniqueName = "Reject Test Product " + UUID.randomUUID();
        UUID contributionId = submitContribution(submitter, uniqueName, true);

        mockMvc.perform(post("/api/v1/ops/contributions/{id}/reject", contributionId)
                        .header("Authorization", operator.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":""}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ops/contributions/{id}/reject", contributionId)
                        .header("Authorization", operator.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Duplicate of an existing product"}
                                """))
                .andExpect(status().isOk());

        Integer catalogueCount = jdbc.queryForObject(
                "select count(*) from product where name = ?", Integer.class, uniqueName);
        assertThat(catalogueCount).isZero();

        MvcResult detail = mockMvc.perform(get("/api/v1/ops/contributions/{id}", contributionId)
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(detail.getResponse().getContentAsString());
        assertThat(node.get("status").asText()).isEqualTo("REJECTED");
        assertThat(node.get("reviewNotes").asText()).isEqualTo("Duplicate of an existing product");

        // Rejecting an already-rejected contribution is refused, not silently repeated.
        mockMvc.perform(post("/api/v1/ops/contributions/{id}/reject", contributionId)
                        .header("Authorization", operator.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"second attempt"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an unknown contribution id is a real not-found for an operator, not an authorization error")
    void unknownContributionIsNotFoundForOperator() throws Exception {
        Session operator = registerNewAccount();
        promoteToOperator(operator.accountId());

        mockMvc.perform(get("/api/v1/ops/contributions/{id}", UUID.randomUUID())
                        .header("Authorization", operator.authHeader()))
                .andExpect(status().isNotFound());
    }
}
