package com.familyfood.scoring.api;

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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /households/{id}/products/fit:batch} (D-061): same scope as the single-product
 * fit — the caller's own entry plus guarded children, never another adult; membership enforced
 * even when nothing is found; unknown products reported, not fatal; the 50-product cap.
 */
class BatchFitIT extends IntegrationTestBase {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductIngestionService ingestion;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    @Test
    @DisplayName("returns one entry per found product with the caller's own member only; unknown ids are listed")
    void batchForMember() throws Exception {
        Session owner = register();
        UUID household = createHousehold(owner);
        UUID a = ingest("Batch Fit A " + UUID.randomUUID());
        UUID b = ingest("Batch Fit B " + UUID.randomUUID());
        UUID missing = UUID.randomUUID();

        JsonNode body = batch(owner, household, List.of(a, b, missing, a), 200);

        assertThat(body.get("items")).hasSize(2);
        assertThat(body.get("items").get(0).get("productId").asText()).isEqualTo(a.toString());
        assertThat(body.get("items").get(1).get("productId").asText()).isEqualTo(b.toString());
        assertThat(body.get("items").get(0).get("members")).hasSize(1);
        String memberStatus = body.get("items").get(0).get("members").get(0).get("status").asText();
        assertThat(memberStatus).isIn("CONSENT_REQUIRED", "NO_PROFILE", "ASSESSED");
        assertThat(body.get("notFound")).hasSize(1);
        assertThat(body.get("notFound").get(0).asText()).isEqualTo(missing.toString());
    }

    @Test
    @DisplayName("another adult in the household sees only their own entry, never the owner's")
    void otherAdultSeesOnlyThemselves() throws Exception {
        Session owner = register();
        Session adult = register();
        UUID household = createHousehold(owner);
        UUID adultMemberId = inviteAndAccept(owner, adult, household);
        UUID product = ingest("Batch Fit Shared " + UUID.randomUUID());

        JsonNode body = batch(adult, household, List.of(product), 200);

        JsonNode members = body.get("items").get(0).get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("memberId").asText()).isEqualTo(adultMemberId.toString());
    }

    @Test
    @DisplayName("a non-member is refused even when every id is unknown")
    void nonMemberRefused() throws Exception {
        Session owner = register();
        Session stranger = register();
        UUID household = createHousehold(owner);

        batch(stranger, household, List.of(UUID.randomUUID()), 403);
        batch(stranger, household, List.of(ingest("Batch Fit Private " + UUID.randomUUID())), 403);
    }

    @Test
    @DisplayName("more than 50 products, or none, is a 400")
    void capEnforced() throws Exception {
        Session owner = register();
        UUID household = createHousehold(owner);
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(UUID.randomUUID());
        }
        batch(owner, household, tooMany, 400);
        batch(owner, household, List.of(), 400);
    }

    @Test
    @DisplayName("anonymous callers are refused")
    void anonymousRefused() throws Exception {
        mockMvc.perform(post("/api/v1/households/{h}/products/fit:batch", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------------ fixtures

    private JsonNode batch(Session session, UUID household, List<UUID> ids, int expectedStatus) throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("productIds", ids));
        MvcResult result = mockMvc.perform(post("/api/v1/households/{h}/products/fit:batch", household)
                        .header("Authorization", session.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? null : objectMapper.readTree(body);
    }

    private Session register() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"batch-fit-%s@example.com","password":"a-sufficiently-long-passphrase"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(UUID.fromString(node.get("accountId").asText()), node.get("accessToken").asText());
    }

    private UUID createHousehold(Session owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Batch Fit Household\",\"ownerDisplayLabel\":\"Me\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID inviteAndAccept(Session inviter, Session invitee, UUID household) throws Exception {
        MvcResult invite = mockMvc.perform(post("/api/v1/households/{id}/members", household)
                        .header("Authorization", inviter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberType\":\"ADULT\",\"email\":\"invitee-%s@example.com\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(invite.getResponse().getContentAsString()).get("token").asText();
        MvcResult accept = mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"displayLabel\":\"Invitee\"}".formatted(token)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(accept.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID ingest(String name) {
        return ingestion.ingestNewProduct(new IngestProductCommand(
                name, "BatchFitBrand", "CHIPS", null, null, null, null, null, null, null, List.of(), List.of(), List.of(),
                SourceType.MANUAL_ENTRY, CaptureMethod.MANUAL_ENTRY, null, null, null,
                VersionStatus.NEEDS_VERIFICATION, DataQuality.UNVERIFIED, null, null));
    }
}
