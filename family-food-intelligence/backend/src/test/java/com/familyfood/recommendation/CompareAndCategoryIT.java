package com.familyfood.recommendation;

import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.NutrientBasis;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compare (D-066), category pages and anonymous product alternatives (D-060) through HTTP and
 * a real Postgres. The ACTIVE rule set is the empty {@code v1}, so nothing is rated: the
 * best-overall answer is an honest NO_WINNER_INSUFFICIENT_DATA and rating rails are empty,
 * while the NOVA and declared-value parts work on real data.
 */
class CompareAndCategoryIT extends IntegrationTestBase {

    @Autowired private ProductIngestionService ingestion;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("compare 3 products anonymously: facts per product, no household, an honest no-winner")
    void compareAnonymous() throws Exception {
        UUID a = ingest("Compare A " + UUID.randomUUID(), "SAUCES_SPREADS", 4, "12");
        UUID b = ingest("Compare B " + UUID.randomUUID(), "SAUCES_SPREADS", 3, "5");
        UUID c = ingest("Compare C " + UUID.randomUUID(), "SAUCES_SPREADS", null, null);

        JsonNode body = json(mockMvc.perform(get("/api/v1/compare").param("ids", a + "," + b + "," + c))
                .andExpect(status().isOk()).andReturn());

        assertThat(body.get("items")).hasSize(3);
        assertThat(body.get("items").get(0).get("product").get("id").asText()).isEqualTo(a.toString());
        assertThat(body.get("items").get(0).get("novaGroup").asInt()).isEqualTo(4);
        assertThat(body.get("items").get(0).get("nutrients").get(0).get("code").asText()).isEqualTo("TOTAL_SUGAR_G");
        assertThat(body.get("items").get(0).get("members")).isEmpty();
        assertThat(body.get("bestOverall").get("outcome").asText()).isEqualTo("NO_WINNER_INSUFFICIENT_DATA");
        assertThat(body.get("bestOverall").has("productId")).isFalse();
    }

    @Test
    @DisplayName("fewer than 2 or more than 5 products, or a bad id, is a 400; an unknown id is a 404")
    void compareValidation() throws Exception {
        UUID a = ingest("Compare V " + UUID.randomUUID(), "CHIPS", null, null);
        mockMvc.perform(get("/api/v1/compare").param("ids", a.toString())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/compare").param("ids", a + "," + a)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/compare").param("ids", "not-a-uuid," + a)).andExpect(status().isBadRequest());
        StringBuilder many = new StringBuilder(a.toString());
        for (int i = 0; i < 5; i++) {
            many.append(",").append(UUID.randomUUID());
        }
        mockMvc.perform(get("/api/v1/compare").param("ids", many.toString())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/compare").param("ids", a + "," + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("naming a household needs a principal; a member gets their own fit per product")
    void compareWithHousehold() throws Exception {
        UUID a = ingest("Compare H1 " + UUID.randomUUID(), "CHIPS", null, null);
        UUID b = ingest("Compare H2 " + UUID.randomUUID(), "CHIPS", null, null);
        UUID household = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/compare").param("ids", a + "," + b).param("householdId", household.toString()))
                .andExpect(status().isUnauthorized());

        String token = register();
        UUID realHousehold = createHousehold(token);
        JsonNode body = json(mockMvc.perform(get("/api/v1/compare").param("ids", a + "," + b)
                        .param("householdId", realHousehold.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        assertThat(body.get("items").get(0).get("members")).hasSize(1);

        String stranger = register();
        mockMvc.perform(get("/api/v1/compare").param("ids", a + "," + b).param("householdId", realHousehold.toString())
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("category page: counts and rails of declared values only; unknown category is a 404")
    void categoryIntelligence() throws Exception {
        UUID declared = ingest("Rail Declared " + UUID.randomUUID(), "MALT_DRINK", 1, "0.5");
        UUID undeclared = ingest("Rail Undeclared " + UUID.randomUUID(), "MALT_DRINK", null, null);

        JsonNode body = json(mockMvc.perform(get("/api/v1/categories/MALT_DRINK/intelligence"))
                .andExpect(status().isOk()).andReturn());

        assertThat(body.get("category").get("code").asText()).isEqualTo("MALT_DRINK");
        assertThat(body.get("counts").get("products").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(body.get("counts").get("rated").asLong()).isZero();
        List<String> lowestSugar = ids(body.get("rails").get("lowestSugar"));
        assertThat(lowestSugar).contains(declared.toString()).doesNotContain(undeclared.toString());
        assertThat(ids(body.get("rails").get("leastProcessed"))).contains(declared.toString()).doesNotContain(undeclared.toString());
        assertThat(body.get("rails").get("betterChoices")).isEmpty();
        assertThat(body.get("rails").has("popular")).isTrue();

        mockMvc.perform(get("/api/v1/categories/NOT_A_CATEGORY/intelligence")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("'popular' needs at least three different viewers, so it never reveals one person's views")
    void popularThreshold() throws Exception {
        UUID three = ingest("Popular Three " + UUID.randomUUID(), "EDIBLE_OIL", null, null);
        UUID two = ingest("Popular Two " + UUID.randomUUID(), "EDIBLE_OIL", null, null);
        for (int i = 0; i < 3; i++) {
            String token = register();
            view(token, three);
            if (i < 2) {
                view(token, two);
            }
        }
        List<String> popular = ids(json(mockMvc.perform(get("/api/v1/categories/EDIBLE_OIL/intelligence"))
                .andExpect(status().isOk()).andReturn()).get("rails").get("popular"));
        assertThat(popular).contains(three.toString()).doesNotContain(two.toString());
    }

    @Test
    @DisplayName("anonymous product alternatives: a less processed product in the category is offered with its trade-offs")
    void anonymousAlternatives() throws Exception {
        // CHOCOLATE is used by no other IT class, so the candidate pool is exactly these rows.
        String category = "CHOCOLATE";
        UUID current = ingest("Alt Current " + UUID.randomUUID(), category, 4, "20");
        UUID lessProcessed = ingest("Alt Less Processed " + UUID.randomUUID(), category, 1, "3");
        UUID sameGroup = ingest("Alt Same Group " + UUID.randomUUID(), category, 4, "30");

        JsonNode body = json(mockMvc.perform(get("/api/v1/products/{id}/alternatives", current))
                .andExpect(status().isOk()).andReturn());

        List<String> offered = StreamSupport.stream(body.get("alternatives").spliterator(), false)
                .map(a -> a.get("productId").asText()).toList();
        assertThat(offered).containsExactly(lessProcessed.toString());   // same NOVA group, no rating: no difference
        JsonNode match = body.get("alternatives").get(0);
        assertThat(match.get("betterOn").get(0).get("code").asText()).isEqualTo("LESS_PROCESSED");
        assertThat(match.get("betterOn").get(0).get("candidateValue").asInt()).isEqualTo(1);
        assertThat(body.get("alternatives").toString()).doesNotContain("memberId");
        assertThat(offered).doesNotContain(sameGroup.toString());
    }

    // ------------------------------------------------------------------------------ fixtures

    private void view(String token, UUID productId) throws Exception {
        mockMvc.perform(post("/api/v1/activity").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"eventType\":\"VIEWED\"}".formatted(productId)))
                .andExpect(status().isCreated());
    }

    private String register() throws Exception {
        JsonNode node = json(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cmp-%s@example.com\",\"password\":\"a-sufficiently-long-passphrase\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn());
        return node.get("accessToken").asText();
    }

    private UUID createHousehold(String token) throws Exception {
        return UUID.fromString(json(mockMvc.perform(post("/api/v1/households").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Compare Household\",\"ownerDisplayLabel\":\"Me\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode products) {
        return StreamSupport.stream(products.spliterator(), false).map(p -> p.get("id").asText()).toList();
    }

    private UUID ingest(String name, String category, Integer nova, String sugar) {
        List<IngestProductCommand.NutrientValue> nutrients = sugar == null ? List.of()
                : List.of(new IngestProductCommand.NutrientValue("TOTAL_SUGAR_G", new BigDecimal(sugar), "g", NutrientBasis.PER_100G));
        UUID id = ingestion.ingestNewProduct(new IngestProductCommand(
                name, "CompareBrand", category, null, null, null, null, null, null, null, List.of(), nutrients, List.of(),
                SourceType.MANUAL_ENTRY, CaptureMethod.MANUAL_ENTRY, null, null, null,
                VersionStatus.NEEDS_VERIFICATION, DataQuality.RECENT, null, null, nova, DietFlag.UNKNOWN, true, null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product WHERE id = ?", Integer.class, id)).isEqualTo(1);
        return id;
    }
}
