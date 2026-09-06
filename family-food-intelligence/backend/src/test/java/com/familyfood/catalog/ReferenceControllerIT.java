package com.familyfood.catalog;

import com.familyfood.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Categories are reference data seeded by V1__extensions_and_reference_data.sql — this
 * test asserts against that seed rather than inserting its own rows.
 */
class ReferenceControllerIT extends IntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/reference/categories returns every seeded category, ordered by sortOrder")
    void listsCategoriesOrderedBySortOrder() throws Exception {
        mockMvc.perform(get("/api/v1/reference/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].code").value("BISCUITS"))
                .andExpect(jsonPath("$[0].name").value("Biscuits and cookies"))
                .andExpect(jsonPath("$[0].path").value("/biscuits"))
                .andExpect(jsonPath("$[0].sortOrder").value(10))
                .andExpect(jsonPath("$[0].typicalServingMinG").value(25))
                .andExpect(jsonPath("$[0].typicalServingMaxG").value(50))
                .andExpect(jsonPath("$[1].code").value("BREAKFAST_CEREAL"));
    }
}
