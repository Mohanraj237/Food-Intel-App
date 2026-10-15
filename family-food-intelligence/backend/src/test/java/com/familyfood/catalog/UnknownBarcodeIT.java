package com.familyfood.catalog;

import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A barcode scan that matches nothing is remembered (V15 {@code unknown_barcode}) — GTIN,
 * first/last seen and a hit count, and nothing about who scanned it.
 */
class UnknownBarcodeIT extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a miss is recorded and counted; a malformed code is not stored; the row holds no person")
    void recordsMisses() throws Exception {
        String gtin = "99" + ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L);

        mockMvc.perform(get("/api/v1/products/barcode/{gtin}", gtin)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/barcode/{gtin}", gtin)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/barcode/{gtin}", "not-a-gtin")).andExpect(status().isNotFound());

        Map<String, Object> row = jdbc.queryForMap("SELECT hit_count, status FROM unknown_barcode WHERE gtin = ?", gtin);
        assertThat(row.get("hit_count")).isEqualTo(2);
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM unknown_barcode WHERE gtin = 'not-a-gtin'", Integer.class)).isZero();

        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'unknown_barcode'", String.class);
        assertThat(columns).noneMatch(c -> c.contains("account") || c.contains("user") || c.contains("member"));
    }
}
