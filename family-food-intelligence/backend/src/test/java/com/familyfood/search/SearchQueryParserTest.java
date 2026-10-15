package com.familyfood.search;

import com.familyfood.search.application.InterpretedTerm;
import com.familyfood.search.application.SearchFilters;
import com.familyfood.search.application.SearchQueryParser;
import com.familyfood.search.application.SearchSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deterministic query parser (D-061), table-tested. What matters as much as what it reads
 * is what it refuses to read: a phrase it does not know stays in the text, "non veg" never
 * becomes vegetarian, and a brand plus a category word stays a name search.
 */
class SearchQueryParserTest {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("biscuits", "BISCUITS"), Map.entry("biscuit", "BISCUITS"), Map.entry("cookies", "BISCUITS"),
            Map.entry("snacks", "NAMKEEN"), Map.entry("namkeen", "NAMKEEN"), Map.entry("chips", "CHIPS"),
            Map.entry("chocolate", "CHOCOLATE"), Map.entry("milk", "DAIRY"), Map.entry("butter", "DAIRY"),
            Map.entry("cereal", "BREAKFAST_CEREAL"), Map.entry("corn flakes", "BREAKFAST_CEREAL"),
            Map.entry("juice", "BEVERAGES"), Map.entry("drinks", "BEVERAGES"), Map.entry("noodles", "INSTANT_NOODLES"),
            Map.entry("peanut butter", "SAUCES_SPREADS"), Map.entry("tea", "BEVERAGES"), Map.entry("protein bar", "PROTEIN_FOODS"));

    private final SearchQueryParser parser = new SearchQueryParser(ALIASES);

    /**
     * query | residual | category | maxTotalSugar | maxAddedSugar | maxSodium | minProtein | minFibre | maxEnergy | maxSatFat | diet | allergens | maxNova | sort
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            biscuits under 10g sugar                  | -              | BISCUITS         | 10   | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            biscuits with less than 10 g sugar        | -              | BISCUITS         | 10   | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            cookies below 5g added sugar              | -              | BISCUITS         | -    | 5    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            sugar under 8 g                           | -              | -                | 8    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            chips under 300mg sodium                  | -              | CHIPS            | -    | -    | 300   | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            namkeen max 0.5 g sodium                  | -              | NAMKEEN          | -    | -    | 500.0 | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            snacks under 1g salt                      | -              | NAMKEEN          | -    | -    | 400.0 | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            cereal at least 10g protein               | -              | BREAKFAST_CEREAL | -    | -    | -     | 10 | -  | -   | -   | -     | -                 | - | RELEVANCE
            cereal with more than 5 g fibre           | -              | BREAKFAST_CEREAL | -    | -    | -     | -  | 5  | -   | -   | -     | -                 | - | RELEVANCE
            snacks over 6g fiber                      | -              | NAMKEEN          | -    | -    | -     | -  | 6  | -   | -   | -     | -                 | - | RELEVANCE
            drinks under 50 kcal                      | -              | BEVERAGES        | -    | -    | -     | -  | -  | 50  | -   | -     | -                 | - | RELEVANCE
            noodles under 3g saturated fat            | -              | INSTANT_NOODLES  | -    | -    | -     | -  | -  | -   | 3   | -     | -                 | - | RELEVANCE
            <= 200 calories snacks                    | -              | NAMKEEN          | -    | -    | -     | -  | -  | 200 | -   | -     | -                 | - | RELEVANCE
            high protein veg snacks                   | -              | NAMKEEN          | -    | -    | -     | -  | -  | -   | -   | VEG   | -                 | - | PROTEIN_DESC
            protein rich vegetarian biscuits          | -              | BISCUITS         | -    | -    | -     | -  | -  | -   | -   | VEG   | -                 | - | PROTEIN_DESC
            vegan chocolate                           | -              | CHOCOLATE        | -    | -    | -     | -  | -  | -   | -   | VEGAN | -                 | - | RELEVANCE
            low sugar biscuits                        | -              | BISCUITS         | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | SUGAR_ASC
            low added sugar cereal                    | -              | BREAKFAST_CEREAL | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | ADDED_SUGAR_ASC
            low salt namkeen                          | -              | NAMKEEN          | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | SODIUM_ASC
            high fibre cereal                         | -              | BREAKFAST_CEREAL | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | FIBRE_DESC
            healthiest juice                          | -              | BEVERAGES        | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | SCORE_DESC
            no nuts chocolate                         | -              | CHOCOLATE        | -    | -    | -     | -  | -  | -   | -   | -     | TREE_NUT          | - | RELEVANCE
            dairy free gluten free cookies            | -              | BISCUITS         | -    | -    | -     | -  | -  | -   | -   | -     | MILK;GLUTEN       | - | RELEVANCE
            peanut free eggless biscuits              | -              | BISCUITS         | -    | -    | -     | -  | -  | -   | -   | -     | PEANUT;EGG        | - | RELEVANCE
            without milk and without soy chocolate    | -              | CHOCOLATE        | -    | -    | -     | -  | -  | -   | -   | -     | MILK;SOY          | - | RELEVANCE
            minimally processed snacks                | -              | NAMKEEN          | -    | -    | -     | -  | -  | -   | -   | -     | -                 | 3 | RELEVANCE
            less processed breakfast cereal           | breakfast cereal | -              | -    | -    | -     | -  | -  | -   | -   | -     | -                 | 3 | RELEVANCE
            corn flakes under 8g sugar                | -              | BREAKFAST_CEREAL | 8    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            Amul butter                               | Amul butter    | -                | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            parle g biscuits                          | parle g biscuits | -              | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            peanut butter                             | -              | SAUCES_SPREADS   | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            tea biscuits                              | tea biscuits   | -                | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            Maggi                                     | Maggi          | -                | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            non veg snacks                            | non veg snacks | -                | -    | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            Britannia under 10g sugar                 | Britannia      | -                | 10   | -    | -     | -  | -  | -   | -   | -     | -                 | - | RELEVANCE
            """)
    void parses(String query, String residual, String category, String maxTotalSugar, String maxAddedSugar,
                String maxSodium, String minProtein, String minFibre, String maxEnergy, String maxSatFat,
                String diet, String allergens, String maxNova, String sort) {
        SearchQueryParser.ParsedQuery parsed = parser.parse(query);
        SearchFilters f = parsed.filters();

        assertThat(parsed.residual()).as("residual").isEqualTo(residual);
        assertThat(f.category()).as("category").isEqualTo(category);
        assertDecimal("maxTotalSugar", f.maxTotalSugar(), maxTotalSugar);
        assertDecimal("maxAddedSugar", f.maxAddedSugar(), maxAddedSugar);
        assertDecimal("maxSodium", f.maxSodium(), maxSodium);
        assertDecimal("minProtein", f.minProtein(), minProtein);
        assertDecimal("minFibre", f.minFibre(), minFibre);
        assertDecimal("maxEnergy", f.maxEnergy(), maxEnergy);
        assertDecimal("maxSatFat", f.maxSatFat(), maxSatFat);
        assertThat(f.dietary()).as("diet").isEqualTo(diet);
        assertThat(String.join(";", f.excludeAllergens())).as("allergens").isEqualTo(allergens == null ? "" : allergens);
        assertThat(f.maxNova()).as("maxNova").isEqualTo(maxNova == null ? null : Integer.valueOf(maxNova));
        assertThat(parsed.sort()).as("sort").isEqualTo(SearchSort.valueOf(sort));
    }

    @Test
    @DisplayName("every read term is echoed back for a chip, with canonical value and unit")
    void echoesInterpretedTerms() {
        SearchQueryParser.ParsedQuery parsed = parser.parse("veg biscuits under 500 mg sodium no nuts");

        assertThat(parsed.interpreted()).extracting(InterpretedTerm::type)
                .containsExactlyInAnyOrder("MAX_NUTRIENT", "DIET", "EXCLUDE_ALLERGEN", "CATEGORY");
        InterpretedTerm sodium = parsed.interpreted().stream().filter(t -> t.type().equals("MAX_NUTRIENT")).findFirst().orElseThrow();
        assertThat(sodium.code()).isEqualTo("SODIUM_MG");
        assertThat(sodium.value()).isEqualByComparingTo("500");
        assertThat(sodium.unit()).isEqualTo("mg");
        assertThat(sodium.source()).isEqualToIgnoringCase("under 500 mg sodium");
    }

    @Test
    @DisplayName("an explicit numeric sugar limit wins over the 'low sugar' sort phrase")
    void numericBeatsSortPhrase() {
        SearchQueryParser.ParsedQuery parsed = parser.parse("low sugar biscuits under 5g sugar");
        assertThat(parsed.filters().maxTotalSugar()).isEqualByComparingTo("5");
        assertThat(parsed.sort()).isEqualTo(SearchSort.SUGAR_ASC);
    }

    @Test
    @DisplayName("blank and null queries parse to nothing at all")
    void blankIsNothing() {
        assertThat(parser.parse(null).residual()).isNull();
        assertThat(parser.parse("   ").filters().hasAny()).isFalse();
        assertThat(parser.parse("   ").interpreted()).isEmpty();
    }

    @Test
    @DisplayName("mg of a gram-nutrient is converted to grams; g of sodium to mg")
    void unitConversion() {
        assertThat(parser.parse("under 2500 mg sugar").filters().maxTotalSugar()).isEqualByComparingTo("2.5");
        assertThat(parser.parse("under 0.3 g sodium").filters().maxSodium()).isEqualByComparingTo("300");
        assertThat(parser.parse("under 300 sodium").filters().maxSodium()).isEqualByComparingTo("300");
    }

    private static void assertDecimal(String name, BigDecimal actual, String expected) {
        if (expected == null) {
            assertThat(actual).as(name).isNull();
        } else {
            assertThat(actual).as(name).isNotNull();
            assertThat(actual).as(name).isEqualByComparingTo(expected);
        }
    }
}
