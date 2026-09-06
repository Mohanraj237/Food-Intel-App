package com.familyfood.identity;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication behaviour against a real database.
 *
 * <p>Covers AC-1.1 through AC-1.5. The enumeration and reuse-detection tests are the ones
 * that matter most: both are properties a reader cannot verify by inspection, and both
 * are easy to regress with an innocuous-looking change to an error path.
 */
class AuthenticationIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String body(String email, String password) {
        return """
               {"email":"%s","password":"%s"}
               """.formatted(email, password);
    }

    // ------------------------------------------------------------------- AC-1.1

    @Test
    @DisplayName("registration creates an account and returns a session")
    void registrationSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), "a-sufficiently-long-passphrase")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("a password shorter than the policy is rejected before any account is created")
    void shortPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a validation error never echoes the rejected password back")
    void validationErrorDoesNotEchoPassword() throws Exception {
        String secret = "tiny";

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), secret)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    @Test
    @DisplayName("registering an already-used email reports a conflict")
    void duplicateEmailRejected() throws Exception {
        String email = uniqueEmail();
        String payload = body(email, "a-sufficiently-long-passphrase");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        // Deliberate disclosure — see AuthenticationService: without it a user who forgot
        // they registered can never recover their account.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    // ------------------------------------------------------------------- AC-1.2

    @Test
    @DisplayName("sign-in with correct credentials returns a session")
    void loginSucceeds() throws Exception {
        String email = uniqueEmail();
        String password = "a-sufficiently-long-passphrase";

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body(email, password)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ------------------------------------------------------------------- AC-1.3

    @Test
    @DisplayName("an unknown email and a wrong password are indistinguishable")
    void noAccountEnumeration() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, "a-sufficiently-long-passphrase")));

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "definitely-the-wrong-passphrase")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownAccount = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), "definitely-the-wrong-passphrase")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode a = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(unknownAccount.getResponse().getContentAsString());

        // Everything except the correlation id must match, or the difference is an oracle.
        ((com.fasterxml.jackson.databind.node.ObjectNode) a).remove("correlationId");
        ((com.fasterxml.jackson.databind.node.ObjectNode) b).remove("correlationId");

        assertThat(a).isEqualTo(b);
    }

    // ------------------------------------------------------------------- AC-1.5

    @Test
    @DisplayName("refresh rotates the token and the old one stops working")
    void refreshRotatesToken() throws Exception {
        String email = uniqueEmail();
        String password = "a-sufficiently-long-passphrase";

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, password)))
                .andReturn();

        String firstRefresh = objectMapper
                .readTree(registered.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        // Presenting the now-rotated token again is treated as theft.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(firstRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("reusing a revoked refresh token kills every session for the account")
    void reuseDetectionRevokesWholeChain() throws Exception {
        String email = uniqueEmail();
        String password = "a-sufficiently-long-passphrase";

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, password)))
                .andReturn();

        String original = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("refreshToken").asText();

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(original)))
                .andExpect(status().isOk())
                .andReturn();

        String current = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Attacker replays the stolen original.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(original)))
                .andExpect(status().isUnauthorized());

        // The legitimate holder's current token must now also be dead: we cannot tell
        // which party is which, so both are forced to sign in again.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(current)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- token handling

    @Test
    @DisplayName("a garbage bearer token is rejected rather than silently treated as anonymous")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("an authenticated endpoint requires a token")
    void authenticatedEndpointRequiresToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- error contract

    @Test
    @DisplayName("errors are problem+json with a correlation id and no stack trace")
    void errorContractIsRfc7807() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), "definitely-the-wrong-passphrase")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.messageKey").value("error.invalid_credentials"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn();

        String payload = result.getResponse().getContentAsString();
        assertThat(payload)
                .doesNotContain("Exception")
                .doesNotContain("com.familyfood")
                .doesNotContain("at java.");
    }

    @Test
    @DisplayName("an unmapped path is 404, not 500")
    void unmappedPathIsNotFound() throws Exception {
        // Regression: Spring 6 throws NoResourceFoundException when the static-resource
        // handler is the last thing tried, which reached the catch-all and surfaced a
        // mistyped URL as a server fault. This product's error contract depends on
        // "we do not have this" staying distinct from "we broke" (Constitution §46).
        mockMvc.perform(get("/api/v1/products/does-not-exist-yet"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("an unknown path outside the public surface is 401, not 404")
    void unknownProtectedPathDoesNotRevealItsAbsence() throws Exception {
        // Deliberate, and the more secure of the two options: if unknown paths returned
        // 404 while protected ones returned 401, the difference would let an anonymous
        // caller map the API surface. Everything outside the small public allow-list
        // answers identically whether or not it exists.
        mockMvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/households/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a wrong verb on a real path is 404, not 500")
    void wrongMethodIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the health endpoint is reachable without authentication")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
