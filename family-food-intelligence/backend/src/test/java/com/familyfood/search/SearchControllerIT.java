package com.familyfood.search;

import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.Category;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.infra.CategoryRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end search through the real HTTP + database stack, exercising the native
 * trigram/full-text query in {@code ProductSearchRepository} against a real PostgreSQL
 * (Testcontainers) — {@code to_tsquery}/{@code similarity()} have no in-memory substitute.
 *
 * <p>{@link IntegrationTestBase} reuses one Postgres container, without a rollback,
 * across every IT class in the run — so a category or search term another test already
 * used can carry leftover rows into this one. Assertions below check for containment of
 * the expected id (and, where it matters, absence of an excluded id) rather than an
 * exact {@code total}, except where the search term is unique enough in this suite that
 * an exact count is safe.
 */
class SearchControllerIT extends IntegrationTestBase {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVersionRepository versionRepository;
    @Autowired private ObjectMapper objectMapper;

    private Product activeProduct(String name, String normalizedName, java.util.UUID categoryId) {
        Product product = productRepository.save(Product.create(
                name, normalizedName, null, null, null, categoryId, ProductStatus.ACTIVE));
        versionRepository.save(ProductVersion.create(product.getId(), 1, VersionStatus.VERIFIED,
                true, null, null, null, null, null, null, null,
                CaptureMethod.MANUAL_ENTRY, null, DataQuality.VERIFIED));
        return product;
    }

    @Test
    @DisplayName("GET /api/v1/search?category= filters by category code")
    void searchFiltersByCategory() throws Exception {
        Category biscuits = categoryRepository.findByCode("BISCUITS").orElseThrow();
        Category sauces = categoryRepository.findByCode("SAUCES_SPREADS").orElseThrow();

        Product parle = activeProduct("Parle-G Original Glucose Biscuits",
                "parle-g original glucose biscuits", biscuits.getId());
        Product kissan = activeProduct("Kissan Mixed Fruit Jam", "kissan mixed fruit jam",
                sauces.getId());

        MvcResult result = mockMvc.perform(get("/api/v1/search").param("category", "BISCUITS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        java.util.List<String> ids = StreamSupport.stream(body.get("items").spliterator(), false)
                .map(item -> item.get("id").asText())
                .toList();

        // The BISCUITS category may already carry rows from another IT class sharing
        // the same container (see class javadoc) — assert containment/exclusion, not count.
        assertThat(ids).contains(parle.getId().toString());
        assertThat(ids).doesNotContain(kissan.getId().toString());
        assertThat(body.get("total").asLong()).isEqualTo(ids.size());
    }

    @Test
    @DisplayName("GET /api/v1/search?q= matches on product name via full-text/trigram search")
    void searchMatchesOnName() throws Exception {
        Category malt = categoryRepository.findByCode("MALT_DRINK").orElseThrow();
        Product bournvita = activeProduct("Cadbury Bournvita Health Drink",
                "cadbury bournvita health drink", malt.getId());
        activeProduct("Horlicks Classic Malt", "horlicks classic malt", malt.getId());

        mockMvc.perform(get("/api/v1/search").param("q", "Bournvita"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bournvita.getId().toString()));
    }

    @Test
    @DisplayName("GET /api/v1/search with no filters and a non-ACTIVE product excludes it")
    void searchExcludesNonActiveProducts() throws Exception {
        Category chips = categoryRepository.findByCode("CHIPS").orElseThrow();
        productRepository.save(Product.create("Unlisted Chips", "unlisted chips zzqq",
                null, null, null, chips.getId(), ProductStatus.DISCOVERED));

        mockMvc.perform(get("/api/v1/search").param("q", "zzqq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
