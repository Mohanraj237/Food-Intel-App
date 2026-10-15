package com.familyfood.catalog.seed;

import com.familyfood.catalog.application.EnrichmentFacts;
import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.NutrientBasis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How one Open Food Facts row becomes catalogue facts (D-031, D-059): what is read, and —
 * more importantly — what is refused rather than guessed.
 */
class OpenFoodFactsRowParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("a NOVA group is accepted only when it is 1–4; the snapshot's junk values are dropped, not clamped")
    void novaGroupOnlyOneToFour() throws Exception {
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nova_group\":4}"))).isEqualTo(4);
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nutriments\":{\"nova-group\":3}}"))).isEqualTo(3);
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nutriments\":{\"nova-group\":10}}"))).isNull();
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nutriments\":{\"nova-group\":26}}"))).isNull();
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nutriments\":{\"nova-group\":0}}"))).isNull();
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nova_group\":\"2\"}"))).isEqualTo(2);
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{\"nova_group\":2.5}"))).isNull();
        assertThat(OpenFoodFactsRowParser.novaGroupOf(row("{}"))).isNull();
    }

    @Test
    @DisplayName("the diet flag comes only from ingredients_analysis_tags; non-vegan (milk) never means non-vegetarian")
    void dietFlagFromAnalysisTags() throws Exception {
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{\"ingredients_analysis_tags\":[\"en:palm-oil\",\"en:vegan\",\"en:vegetarian\"]}")))
                .isEqualTo(DietFlag.VEGAN);
        // A milk biscuit: non-vegan, vegetarian.
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{\"ingredients_analysis_tags\":[\"en:non-vegan\",\"en:vegetarian\"]}")))
                .isEqualTo(DietFlag.VEG);
        // Parle-G as Open Food Facts actually tags it (2026-09-15): no vegetarian claim can be made.
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{\"ingredients_analysis_tags\":[\"en:palm-oil\",\"en:non-vegan\",\"en:vegetarian-status-unknown\"]}")))
                .isEqualTo(DietFlag.UNKNOWN);
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{\"ingredients_analysis_tags\":[\"en:non-vegan\",\"en:maybe-vegetarian\"]}")))
                .isEqualTo(DietFlag.UNKNOWN);
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{\"ingredients_analysis_tags\":[\"en:non-vegan\",\"en:non-vegetarian\"]}")))
                .isEqualTo(DietFlag.NON_VEG);
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{\"ingredients_analysis_tags\":[\"en:vegan-status-unknown\",\"en:vegetarian-status-unknown\"]}")))
                .isEqualTo(DietFlag.UNKNOWN);
        assertThat(OpenFoodFactsRowParser.dietFlagOf(row("{}"))).isEqualTo(DietFlag.UNKNOWN);
    }

    @Test
    @DisplayName("nutrients declared per 100 ml keep PER_100ML as their basis; sodium is converted g → mg")
    void basisAndUnits() throws Exception {
        JsonNode node = row("{\"nutrition_data_per\":\"100ml\",\"nutriments\":{\"energy-kcal_100g\":45,\"sugars_100g\":10.9,\"sodium_100g\":0.04}}");

        var nutrients = OpenFoodFactsRowParser.nutrientsOf(node);

        assertThat(nutrients).extracting(IngestProductCommand.NutrientValue::nutrientCode)
                .containsExactly("ENERGY_KCAL", "TOTAL_SUGAR_G", "SODIUM_MG");
        assertThat(nutrients).allSatisfy(n -> assertThat(n.basisPer100()).isEqualTo(NutrientBasis.PER_100ML));
        assertThat(nutrients.get(2).amountPer100()).isEqualByComparingTo("40");
        assertThat(nutrients.get(2).unit()).isEqualTo("mg");
    }

    @Test
    @DisplayName("the fruit/veg/nut estimate is read as an estimate, and only when it is a plausible percentage")
    void fvnlEstimate() throws Exception {
        assertThat(OpenFoodFactsRowParser.fvnlEstimateOf(row("{\"nutriments\":{\"fruits-vegetables-nuts-estimate-from-ingredients_100g\":12.5}}")))
                .isEqualByComparingTo("12.5");
        assertThat(OpenFoodFactsRowParser.fvnlEstimateOf(row("{\"nutriments\":{\"fruits-vegetables-nuts-estimate-from-ingredients_100g\":120}}")))
                .isNull();
        assertThat(OpenFoodFactsRowParser.fvnlEstimateOf(row("{\"nutriments\":{}}"))).isNull();
    }

    @Test
    @DisplayName("serving size comes from serving_quantity, or is parsed from serving_size text, or stays absent")
    void servingSize() throws Exception {
        var fromQuantity = OpenFoodFactsRowParser.parseServing(row("{\"serving_size\":\"2 biscuits (18 g)\",\"serving_quantity\":18}"));
        assertThat(fromQuantity.value()).isEqualByComparingTo("18");
        assertThat(fromQuantity.declared()).isEqualTo("2 biscuits (18 g)");

        var fromText = OpenFoodFactsRowParser.parseServing(row("{\"serving_size\":\"30 g\",\"serving_quantity\":null}"));
        assertThat(fromText.value()).isEqualByComparingTo("30");

        var absent = OpenFoodFactsRowParser.parseServing(row("{\"serving_size\":\"1 pack\"}"));
        assertThat(absent.value()).isNull();
        assertThat(absent.declared()).isEqualTo("1 pack");
        assertThat(OpenFoodFactsRowParser.parseServing(row("{}")).declared()).isNull();
    }

    @Test
    @DisplayName("a full row becomes a command carrying every enrichment fact, still NEEDS_VERIFICATION")
    void fullRowToCommand() throws Exception {
        JsonNode node = row("""
                {"code":"8901719134845","product_name":"Parle-G","brands":"Parle, Parle Products",
                 "categories_tags":["en:snacks","en:biscuits"],"quantity":"250 g","nova_group":4,
                 "ingredients_analysis_tags":["en:vegetarian"],"serving_size":"4 biscuits (25 g)","serving_quantity":25,
                 "ingredients_text":"WHEAT FLOUR, SUGAR, RAISING AGENTS [INS 503(ii), 500(ii)]",
                 "allergens_tags":["en:gluten","en:milk"],"nutrition_data_per":"100g",
                 "nutriments":{"energy-kcal_100g":454,"sugars_100g":25.5,"fruits-vegetables-nuts-estimate-from-ingredients_100g":0}}
                """);

        IngestProductCommand cmd = OpenFoodFactsRowParser.toCommand(node, "8901719134845", "Parle-G");

        assertThat(cmd.brandName()).isEqualTo("Parle");
        assertThat(cmd.categoryCode()).isEqualTo("BISCUITS");
        assertThat(cmd.novaGroup()).isEqualTo(4);
        assertThat(cmd.dietFlag()).isEqualTo(DietFlag.VEG);
        assertThat(cmd.dietFlagEstimated()).isTrue();
        assertThat(cmd.servingSizeG()).isEqualByComparingTo("25");
        assertThat(cmd.servingSizeDeclared()).isEqualTo("4 biscuits (25 g)");
        assertThat(cmd.fvnlPctEstimated()).isEqualByComparingTo("0");
        assertThat(cmd.ingredientTokens()).containsExactly("WHEAT FLOUR", "SUGAR", "RAISING AGENTS [INS 503(ii), 500(ii)]");
        assertThat(cmd.allergens()).extracting(IngestProductCommand.AllergenValue::allergenCode).containsExactly("GLUTEN", "MILK");
        assertThat(cmd.versionStatus().name()).isEqualTo("NEEDS_VERIFICATION");
        assertThat(cmd.licenseNote()).contains("Open Database License");

        EnrichmentFacts facts = OpenFoodFactsRowParser.toEnrichmentFacts(node, "8901719134845");
        assertThat(facts.novaGroup()).isEqualTo(4);
        assertThat(facts.dietFlag()).isEqualTo(DietFlag.VEG);
        assertThat(facts.nutrients()).hasSize(2);
        assertThat(facts.sourceRef()).endsWith("/8901719134845");
    }

    private static JsonNode row(String json) throws Exception {
        return MAPPER.readTree(json);
    }
}
