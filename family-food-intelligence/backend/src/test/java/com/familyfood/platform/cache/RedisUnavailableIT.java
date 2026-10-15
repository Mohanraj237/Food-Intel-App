package com.familyfood.platform.cache;

import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis configured but not there at all (D-056): the application starts, serves every read
 * from the database, and reports DEGRADED — never DOWN — while liveness and readiness stay UP.
 */
@TestPropertySource(properties = {
        "familyfood.cache.type=redis",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1"   // nothing listens on port 1: every connection is refused
})
class RedisUnavailableIT extends IntegrationTestBase {

    @Autowired private CacheCircuitBreaker breaker;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVersionRepository versionRepository;

    @Test
    @DisplayName("products and categories are served from the database, and the cache is then skipped")
    void readsFallBackToTheDatabase() throws Exception {
        Product product = productRepository.save(Product.create(
                "Uncached Upma Mix", "uncached upma mix", null, null, null, null, ProductStatus.ACTIVE));
        versionRepository.save(ProductVersion.create(product.getId(), 1, VersionStatus.VERIFIED,
                true, null, null, null, null, null, null, null,
                CaptureMethod.MANUAL_ENTRY, null, DataQuality.UNVERIFIED));

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Uncached Upma Mix"));
        mockMvc.perform(get("/api/v1/reference/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists());
        mockMvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(breaker.isBypassing()).isTrue();
    }

    @Test
    @DisplayName("health is DEGRADED with HTTP 200; liveness and readiness stay UP")
    void healthIsDegradedNotDown() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
