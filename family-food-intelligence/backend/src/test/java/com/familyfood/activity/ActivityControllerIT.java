package com.familyfood.activity;

import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Activity/outcome capture (FR-012, D-005) against a real database and the real HTTP
 * security chain. Every event and every "recent" listing must be scoped to the caller's own
 * account only (D-002's self-only pattern) — this file's central question is account
 * isolation, plus the ordinary input-validation and not-found questions Phase 6 hardening
 * asks of every new endpoint.
 */
class ActivityControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductIngestionService productIngestionService;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "activity-it-" + UUID.randomUUID() + "@example.com";
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

    private UUID ingestProduct(String name) {
        IngestProductCommand cmd = new IngestProductCommand(
                name, "TestBrand", "CHIPS", null, null, null, null, null, null,
                null, List.of(), List.of(), List.of(),
                SourceType.MANUAL_ENTRY, CaptureMethod.MANUAL_ENTRY, null, null, null,
                VersionStatus.NEEDS_VERIFICATION, DataQuality.UNVERIFIED, null, null);
        return productIngestionService.ingestNewProduct(cmd);
    }

    private UUID recordActivity(Session session, UUID productId, UUID alternativeProductId, String eventType) throws Exception {
        String altField = alternativeProductId == null ? "null" : "\"" + alternativeProductId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/activity")
                        .header("Authorization", session.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","alternativeProductId":%s,"eventType":"%s"}
                                """.formatted(productId, altField, eventType)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    // ------------------------------------------------------------------------- authorization

    @Test
    @DisplayName("an anonymous caller cannot record or read activity")
    void anonymousCannotAccessActivity() throws Exception {
        UUID productId = ingestProduct("Anon Activity Product " + UUID.randomUUID());
        mockMvc.perform(post("/api/v1/activity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","eventType":"VIEWED"}
                                """.formatted(productId)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/activity/recent")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("one account can never read another account's activity")
    void oneAccountCannotReadAnothersActivity() throws Exception {
        Session accountA = registerNewAccount();
        Session accountB = registerNewAccount();
        UUID productId = ingestProduct("Isolation Test Product " + UUID.randomUUID());

        recordActivity(accountA, productId, null, "VIEWED");

        MvcResult resultB = mockMvc.perform(get("/api/v1/activity/recent")
                        .header("Authorization", accountB.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode recentForB = objectMapper.readTree(resultB.getResponse().getContentAsString());
        assertThat(recentForB.isArray()).isTrue();
        assertThat(recentForB).isEmpty();

        MvcResult resultA = mockMvc.perform(get("/api/v1/activity/recent")
                        .header("Authorization", accountA.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode recentForA = objectMapper.readTree(resultA.getResponse().getContentAsString());
        assertThat(recentForA).hasSize(1);
        assertThat(recentForA.get(0).get("product").get("id").asText()).isEqualTo(productId.toString());
    }

    // ------------------------------------------------------------------------------ event types

    @Test
    @DisplayName("every real event type persists")
    void everyRealEventTypePersists() throws Exception {
        Session session = registerNewAccount();
        UUID productId = ingestProduct("All Event Types Product " + UUID.randomUUID());
        UUID alternativeId = ingestProduct("All Event Types Alternative " + UUID.randomUUID());

        for (String type : List.of("VIEWED", "SCANNED", "COMPARED", "BOUGHT",
                "BOUGHT_ALTERNATIVE", "BOUGHT_NEITHER", "SKIPPED")) {
            UUID needsAlt = "BOUGHT_ALTERNATIVE".equals(type) ? alternativeId : null;
            recordActivity(session, productId, needsAlt, type);
        }

        Integer count = jdbc.queryForObject(
                "select count(*) from activity_event where account_id = ?", Integer.class, session.accountId());
        assertThat(count).isEqualTo(7);
    }

    @Test
    @DisplayName("an invalid event type is rejected, not silently coerced or stored")
    void invalidEventTypeIsRejected() throws Exception {
        Session session = registerNewAccount();
        UUID productId = ingestProduct("Invalid Event Type Product " + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/activity")
                        .header("Authorization", session.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","eventType":"NOT_A_REAL_EVENT"}
                                """.formatted(productId)))
                .andExpect(status().isBadRequest());

        Integer count = jdbc.queryForObject(
                "select count(*) from activity_event where account_id = ?", Integer.class, session.accountId());
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("a missing productId is rejected")
    void missingProductIdIsRejected() throws Exception {
        Session session = registerNewAccount();
        mockMvc.perform(post("/api/v1/activity")
                        .header("Authorization", session.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"VIEWED"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown productId is a real not-found, not silently recorded")
    void unknownProductIdIsNotFound() throws Exception {
        Session session = registerNewAccount();
        mockMvc.perform(post("/api/v1/activity")
                        .header("Authorization", session.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","eventType":"VIEWED"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        Integer count = jdbc.queryForObject(
                "select count(*) from activity_event where account_id = ?", Integer.class, session.accountId());
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("an unknown alternativeProductId is also rejected, even when productId is real")
    void unknownAlternativeProductIdIsRejected() throws Exception {
        Session session = registerNewAccount();
        UUID productId = ingestProduct("Real Product For Bad Alt " + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/activity")
                        .header("Authorization", session.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","alternativeProductId":"%s","eventType":"BOUGHT_ALTERNATIVE"}
                                """.formatted(productId, UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------- recent activity

    @Test
    @DisplayName("recent activity is newest-first and enriched with real product summaries")
    void recentActivityIsNewestFirstAndEnriched() throws Exception {
        Session session = registerNewAccount();
        UUID productOne = ingestProduct("Recent Order Product One " + UUID.randomUUID());
        UUID productTwo = ingestProduct("Recent Order Product Two " + UUID.randomUUID());

        recordActivity(session, productOne, null, "VIEWED");
        Thread.sleep(10); // ensure a distinct occurred_at ordering, not relying on same-millisecond luck
        recordActivity(session, productTwo, null, "VIEWED");

        MvcResult result = mockMvc.perform(get("/api/v1/activity/recent")
                        .header("Authorization", session.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode recent = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).get("product").get("id").asText()).isEqualTo(productTwo.toString());
        assertThat(recent.get(1).get("product").get("id").asText()).isEqualTo(productOne.toString());
    }

    @Test
    @DisplayName("a duplicate submission of the same event is recorded as a second row, not deduplicated or rejected — "
            + "this is an append-only log, not an idempotent toggle")
    void duplicateEventsAreRecordedAsSeparateRows() throws Exception {
        Session session = registerNewAccount();
        UUID productId = ingestProduct("Duplicate Event Product " + UUID.randomUUID());

        recordActivity(session, productId, null, "VIEWED");
        recordActivity(session, productId, null, "VIEWED");

        Integer count = jdbc.queryForObject(
                "select count(*) from activity_event where account_id = ? and product_id = ?",
                Integer.class, session.accountId(), productId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("BOUGHT_ALTERNATIVE carries both the product and the alternative through to recent activity")
    void boughtAlternativeCarriesBothProducts() throws Exception {
        Session session = registerNewAccount();
        UUID productId = ingestProduct("Original Product " + UUID.randomUUID());
        UUID alternativeId = ingestProduct("Alternative Product " + UUID.randomUUID());

        recordActivity(session, productId, alternativeId, "BOUGHT_ALTERNATIVE");

        MvcResult result = mockMvc.perform(get("/api/v1/activity/recent")
                        .header("Authorization", session.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode recent = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(recent.get(0).get("eventType").asText()).isEqualTo("BOUGHT_ALTERNATIVE");
        assertThat(recent.get(0).get("product").get("id").asText()).isEqualTo(productId.toString());
        assertThat(recent.get(0).get("alternativeProduct").get("id").asText()).isEqualTo(alternativeId.toString());
    }
}
