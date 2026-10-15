package com.familyfood.search;

import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search v2 through HTTP and a real Postgres (D-061): server-side filters and sort over
 * {@code product_search_facts}, the missing-data count, the parser's interpreted terms,
 * suggestions, and "did you mean". Every product here carries a per-run token in its name so
 * leftover rows from other IT classes in the shared container cannot match.
 */
class SearchV2IT extends IntegrationTestBase {

    @Autowired private ProductIngestionService ingestion;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private UUID lowSugar;
    private UUID highSugar;
    private UUID noSugarDeclared;
    private UUID peanutOne;

    @BeforeEach
    void seed() {
        token = "zq" + UUID.randomUUID().toString().substring(0, 8);
        lowSugar = ingest(token + " Oat Crackers", DietFlag.VEG, 1,
                List.of(n("TOTAL_SUGAR_G", "3.5", "g"), n("SODIUM_MG", "420", "mg"), n("PROTEIN_G", "11", "g")), List.of());
        highSugar = ingest(token + " Cream Biscuits", DietFlag.VEG, 4,
                List.of(n("TOTAL_SUGAR_G", "31", "g"), n("SODIUM_MG", "180", "mg"), n("PROTEIN_G", "5", "g")), List.of());
        noSugarDeclared = ingest(token + " Plain Biscuits", DietFlag.UNKNOWN, null,
                List.of(n("SODIUM_MG", "300", "mg"), n("PROTEIN_G", "7", "g")), List.of());
        peanutOne = ingest(token + " Peanut Cookies", DietFlag.VEGAN, 4,
                List.of(n("TOTAL_SUGAR_G", "8", "g"), n("SODIUM_MG", "250", "mg"), n("PROTEIN_G", "150", "g")),
                List.of("PEANUT"));
    }

    @Test
    @DisplayName("a max-sugar filter keeps only products at or under it and counts the ones that do not declare sugar")
    void maxSugarFilterAndMissingDataCount() throws Exception {
        JsonNode body = search("q", token, "maxTotalSugar", "10");

        assertThat(ids(body)).containsExactlyInAnyOrder(lowSugar.toString(), peanutOne.toString());
        assertThat(body.get("total").asLong()).isEqualTo(2);
        assertThat(body.get("excludedForMissingData").asLong()).isEqualTo(1);   // the plain biscuit
    }

    @Test
    @DisplayName("'under 10g sugar' in the words becomes the same filter, echoed as an interpreted term")
    void naturalLanguageFilter() throws Exception {
        JsonNode body = search("q", token + " under 10g sugar");

        assertThat(ids(body)).containsExactlyInAnyOrder(lowSugar.toString(), peanutOne.toString());
        assertThat(body.get("residualQuery").asText()).isEqualTo(token);
        JsonNode term = body.get("interpreted").get(0);
        assertThat(term.get("type").asText()).isEqualTo("MAX_NUTRIENT");
        assertThat(term.get("code").asText()).isEqualTo("TOTAL_SUGAR_G");
        assertThat(term.get("value").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("sort is applied in SQL; undeclared values go last; an impossible 150 g protein never tops the list")
    void serverSideSort() throws Exception {
        assertThat(ids(search("q", token, "sort", "SUGAR_ASC")))
                .containsExactly(lowSugar.toString(), peanutOne.toString(), highSugar.toString(), noSugarDeclared.toString());
        assertThat(ids(search("q", token, "sort", "SODIUM_ASC")).get(0)).isEqualTo(highSugar.toString());
        List<String> byProtein = ids(search("q", token, "sort", "PROTEIN_DESC"));
        assertThat(byProtein.get(0)).isEqualTo(lowSugar.toString());
        assertThat(byProtein.get(byProtein.size() - 1)).isEqualTo(peanutOne.toString());
    }

    @Test
    @DisplayName("allergen exclusion, diet and processing-level filters; a diet filter counts UNKNOWN as missing data")
    void allergenDietNovaFilters() throws Exception {
        assertThat(ids(search("q", token, "excludeAllergens", "PEANUT"))).doesNotContain(peanutOne.toString()).hasSize(3);

        JsonNode veg = search("q", token, "dietary", "VEG");
        assertThat(ids(veg)).containsExactlyInAnyOrder(lowSugar.toString(), highSugar.toString(), peanutOne.toString());
        assertThat(veg.get("excludedForMissingData").asLong()).isEqualTo(1);
        assertThat(ids(search("q", token, "dietary", "VEGAN"))).containsExactly(peanutOne.toString());

        assertThat(ids(search("q", token, "maxNova", "3"))).containsExactly(lowSugar.toString());
    }

    @Test
    @DisplayName("summaries carry diet flag, NOVA group and declared allergen codes")
    void summariesCarryV2Facts() throws Exception {
        JsonNode item = StreamSupport.stream(search("q", token + " Peanut").get("items").spliterator(), false)
                .filter(i -> i.get("id").asText().equals(peanutOne.toString())).findFirst().orElseThrow();
        assertThat(item.get("dietFlag").asText()).isEqualTo("VEGAN");
        assertThat(item.get("novaGroup").asInt()).isEqualTo(4);
        assertThat(item.get("allergenCodes").get(0).asText()).isEqualTo("PEANUT");
        assertThat(item.has("assessment")).isFalse(); // nothing is scored under the empty ACTIVE v1
    }

    @Test
    @DisplayName("invalid filters are a 400, never silently ignored")
    void invalidFiltersRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("maxNova", "9")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search").param("maxTotalSugar", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search").param("excludeAllergens", "CHEESE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search").param("sort", "PRICE")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("suggestions offer matching products by prefix; a brand is offered by an alias typo")
    void suggestions() throws Exception {
        JsonNode products = json(mockMvc.perform(get("/api/v1/search/suggest").param("q", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> labels = new ArrayList<>();
        products.forEach(s -> labels.add(s.get("kind").asText() + ":" + s.get("label").asText()));
        assertThat(labels).contains("PRODUCT:" + token + " Oat Crackers");

        // "Paper Boat" is created by no other test class (the shared container keeps rows);
        // its seeded alias "paperboat" (BrandAliases / V15) must find it.
        ingestBranded("Paper Boat", token + " Aamras");
        JsonNode typo = json(mockMvc.perform(get("/api/v1/search/suggest").param("q", "paperboat"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(typo.get(0).get("kind").asText()).isEqualTo("BRAND");
        assertThat(typo.get(0).get("label").asText()).isEqualTo("Paper Boat");

        mockMvc.perform(get("/api/v1/search/suggest").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a query with no results and nothing close offers no suggestion rather than an invented one")
    void didYouMeanNeverInvents() throws Exception {
        JsonNode body = search("q", "qqxjjvvkkwwzz");
        assertThat(body.get("total").asLong()).isZero();
        assertThat(body.has("didYouMean")).isFalse();
        assertThat(body.has("related")).isFalse();
    }

    // ------------------------------------------------------------------------------ fixtures

    private JsonNode search(String... params) throws Exception {
        var request = get("/api/v1/search");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return json(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String s) throws Exception {
        return objectMapper.readTree(s);
    }

    private static List<String> ids(JsonNode body) {
        return StreamSupport.stream(body.get("items").spliterator(), false).map(i -> i.get("id").asText()).toList();
    }

    private UUID ingest(String name, DietFlag diet, Integer nova, List<IngestProductCommand.NutrientValue> nutrients,
                        List<String> allergens) {
        return ingestion.ingestNewProduct(new IngestProductCommand(
                name, "SearchTestBrand", "BISCUITS", null, null, null, null, null, null, null, List.of(), nutrients,
                allergens.stream().map(a -> new IngestProductCommand.AllergenValue(a, AllergenDeclaration.CONTAINS)).toList(),
                SourceType.MANUAL_ENTRY, CaptureMethod.MANUAL_ENTRY, null, null, null,
                VersionStatus.NEEDS_VERIFICATION, DataQuality.RECENT, null, null, nova, diet, true, null));
    }

    private void ingestBranded(String brand, String name) {
        ingestion.ingestNewProduct(new IngestProductCommand(
                name, brand, "PROTEIN_FOODS", null, null, null, null, null, null, null, List.of(), List.of(), List.of(),
                SourceType.MANUAL_ENTRY, CaptureMethod.MANUAL_ENTRY, null, null, null,
                VersionStatus.NEEDS_VERIFICATION, DataQuality.RECENT, null, null));
    }

    private static IngestProductCommand.NutrientValue n(String code, String per100, String unit) {
        return new IngestProductCommand.NutrientValue(code, new BigDecimal(per100), unit, NutrientBasis.PER_100G);
    }
}
