package com.familyfood.catalog.seed;

import com.familyfood.catalog.application.EnrichmentFacts;
import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.DietFlag;
import com.familyfood.catalog.domain.GtinType;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns one Open Food Facts product row (a line of {@code openfoodfacts-india.jsonl}) into
 * either a full {@link IngestProductCommand} (first import) or the {@link EnrichmentFacts} a
 * re-fetched row may fill into an existing version. Pure parsing — no I/O, no repositories —
 * shared by {@link OpenFoodFactsSeedRunner} and {@link OpenFoodFactsEnrichmentRunner} so
 * both read a row the same way, and unit-testable without a Spring context.
 *
 * <p>What is read, and what is refused (D-031, D-059): a NOVA group only when it is 1–4 (the
 * snapshot carries junk values like 10 or 26 for some rows — those are dropped, never
 * clamped); a diet flag only from {@code ingredients_analysis_tags}, always marked
 * estimated; the fruit/veg/nut estimate only as an estimate; nutrients declared per 100 mL
 * keep {@code PER_100ML} as their basis. A field absent from the row stays absent.
 */
public final class OpenFoodFactsRowParser {

    static final String LICENSE_NOTE =
            "Data from Open Food Facts (https://world.openfoodfacts.org), licensed under the "
          + "Open Database License (ODbL) v1.0 (https://opendatacommons.org/licenses/odbl/1.0/). "
          + "Contents (nutrition/ingredients facts) additionally under the Database Contents "
          + "License (DbCL).";

    /** OFF categories_tags substring -> our closed category code (V1__extensions_and_reference_data.sql). */
    static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("instant-noodles", "INSTANT_NOODLES");
        CATEGORY_KEYWORDS.put("noodles", "INSTANT_NOODLES");
        CATEGORY_KEYWORDS.put("pastas", "INSTANT_NOODLES");
        CATEGORY_KEYWORDS.put("breakfast-cereal", "BREAKFAST_CEREAL");
        CATEGORY_KEYWORDS.put("mueslis", "BREAKFAST_CEREAL");
        CATEGORY_KEYWORDS.put("corn-flakes", "BREAKFAST_CEREAL");
        CATEGORY_KEYWORDS.put("oat-flakes", "BREAKFAST_CEREAL");
        CATEGORY_KEYWORDS.put("porridge", "BREAKFAST_CEREAL");
        CATEGORY_KEYWORDS.put("biscuits", "BISCUITS");
        CATEGORY_KEYWORDS.put("cookies", "BISCUITS");
        CATEGORY_KEYWORDS.put("crackers", "BISCUITS");
        CATEGORY_KEYWORDS.put("malted-drink", "MALT_DRINK");
        CATEGORY_KEYWORDS.put("malt-based-drink", "MALT_DRINK");
        CATEGORY_KEYWORDS.put("cocoa-and-malt", "MALT_DRINK");
        CATEGORY_KEYWORDS.put("indian-snacks", "NAMKEEN");
        CATEGORY_KEYWORDS.put("namkeen", "NAMKEEN");
        CATEGORY_KEYWORDS.put("bhujia", "NAMKEEN");
        CATEGORY_KEYWORDS.put("chips", "CHIPS");
        CATEGORY_KEYWORDS.put("crisps", "CHIPS");
        CATEGORY_KEYWORDS.put("wafers", "CHIPS");
        CATEGORY_KEYWORDS.put("chocolates", "CHOCOLATE");
        CATEGORY_KEYWORDS.put("confectioneries", "CHOCOLATE");
        CATEGORY_KEYWORDS.put("candies", "CHOCOLATE");
        CATEGORY_KEYWORDS.put("sweets", "CHOCOLATE");
        CATEGORY_KEYWORDS.put("flours", "STAPLES");
        CATEGORY_KEYWORDS.put("doughs", "STAPLES");
        CATEGORY_KEYWORDS.put("breads", "STAPLES");
        CATEGORY_KEYWORDS.put("atta", "STAPLES");
        CATEGORY_KEYWORDS.put("rices", "STAPLES");
        CATEGORY_KEYWORDS.put("dairies", "DAIRY");
        CATEGORY_KEYWORDS.put("milks", "DAIRY");
        CATEGORY_KEYWORDS.put("yogurts", "DAIRY");
        CATEGORY_KEYWORDS.put("cheeses", "DAIRY");
        CATEGORY_KEYWORDS.put("paneer", "DAIRY");
        CATEGORY_KEYWORDS.put("vegetable-oils", "EDIBLE_OIL");
        CATEGORY_KEYWORDS.put("sunflower-oils", "EDIBLE_OIL");
        CATEGORY_KEYWORDS.put("mustard-oils", "EDIBLE_OIL");
        CATEGORY_KEYWORDS.put("ghee", "EDIBLE_OIL");
        CATEGORY_KEYWORDS.put("oils-and-fats", "EDIBLE_OIL");
        CATEGORY_KEYWORDS.put("sauces", "SAUCES_SPREADS");
        CATEGORY_KEYWORDS.put("condiments", "SAUCES_SPREADS");
        CATEGORY_KEYWORDS.put("spreads", "SAUCES_SPREADS");
        CATEGORY_KEYWORDS.put("ketchups", "SAUCES_SPREADS");
        CATEGORY_KEYWORDS.put("jams", "SAUCES_SPREADS");
        CATEGORY_KEYWORDS.put("chutneys", "SAUCES_SPREADS");
        CATEGORY_KEYWORDS.put("protein-bars", "PROTEIN_FOODS");
        CATEGORY_KEYWORDS.put("protein-powders", "PROTEIN_FOODS");
        CATEGORY_KEYWORDS.put("energy-drinks", "BEVERAGES");
        CATEGORY_KEYWORDS.put("sodas", "BEVERAGES");
        CATEGORY_KEYWORDS.put("fruit-juices", "BEVERAGES");
        CATEGORY_KEYWORDS.put("waters", "BEVERAGES");
        CATEGORY_KEYWORDS.put("carbonated-drinks", "BEVERAGES");
        CATEGORY_KEYWORDS.put("beverages", "BEVERAGES");
        CATEGORY_KEYWORDS.put("teas", "BEVERAGES");
        CATEGORY_KEYWORDS.put("coffees", "BEVERAGES");
    }

    /** OFF allergens_tags -> our closed allergen code (V1__extensions_and_reference_data.sql). */
    static final Map<String, String> ALLERGEN_TAGS = Map.ofEntries(
            Map.entry("en:milk", "MILK"),
            Map.entry("en:eggs", "EGG"),
            Map.entry("en:fish", "FISH"),
            Map.entry("en:crustaceans", "CRUSTACEAN"),
            Map.entry("en:nuts", "TREE_NUT"),
            Map.entry("en:peanuts", "PEANUT"),
            Map.entry("en:wheat", "WHEAT"),
            Map.entry("en:gluten", "GLUTEN"),
            Map.entry("en:soybeans", "SOY"),
            Map.entry("en:sesame-seeds", "SESAME"),
            Map.entry("en:sulphur-dioxide-and-sulphites", "SULPHITE"),
            Map.entry("en:mustard", "MUSTARD"));

    /** OFF nutriments {@code <key>_100g} field -> our nutrient code, unit, and g->mg factor. */
    record NutrientMapping(String offKey, String code, String unit, double multiplier) {
    }

    static final List<NutrientMapping> NUTRIENT_MAPPINGS = List.of(
            new NutrientMapping("energy-kcal_100g", "ENERGY_KCAL", "kcal", 1.0),
            new NutrientMapping("energy-kj_100g", "ENERGY_KJ", "kJ", 1.0),
            new NutrientMapping("proteins_100g", "PROTEIN_G", "g", 1.0),
            new NutrientMapping("carbohydrates_100g", "CARB_G", "g", 1.0),
            new NutrientMapping("sugars_100g", "TOTAL_SUGAR_G", "g", 1.0),
            new NutrientMapping("added-sugars_100g", "ADDED_SUGAR_G", "g", 1.0),
            new NutrientMapping("fiber_100g", "FIBRE_G", "g", 1.0),
            new NutrientMapping("fat_100g", "TOTAL_FAT_G", "g", 1.0),
            new NutrientMapping("saturated-fat_100g", "SAT_FAT_G", "g", 1.0),
            new NutrientMapping("trans-fat_100g", "TRANS_FAT_G", "g", 1.0),
            new NutrientMapping("sodium_100g", "SODIUM_MG", "mg", 1000.0),
            new NutrientMapping("salt_100g", "SALT_G", "g", 1.0),
            new NutrientMapping("cholesterol_100g", "CHOLESTEROL_MG", "mg", 1000.0),
            new NutrientMapping("calcium_100g", "CALCIUM_MG", "mg", 1000.0),
            new NutrientMapping("iron_100g", "IRON_MG", "mg", 1000.0));

    static final String FVNL_ESTIMATE_KEY = "fruits-vegetables-nuts-estimate-from-ingredients_100g";

    private static final Pattern QUANTITY_PATTERN =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(kg|g|l|ml|L|G|Kg|KG|ML|Ltr|ltr)");

    private static final Pattern SERVING_PATTERN =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(g|ml|G|ML|mL)\\b");

    private OpenFoodFactsRowParser() {
    }

    // ------------------------------------------------------------------------- full import

    public static IngestProductCommand toCommand(JsonNode node, String gtin, String name) {
        String brandName = firstOf(node.path("brands").asText(null));
        String categoryCode = mapCategory(node.path("categories_tags"));
        GtinType gtinType = inferGtinType(gtin);

        ParsedQuantity packSize = parseQuantity(node.path("quantity").asText(null));
        ParsedQuantity serving = parseServing(node);

        String ingredientsRaw = trimToNull(node.path("ingredients_text").asText(null));
        List<String> ingredientTokens = splitIngredients(ingredientsRaw);

        List<IngestProductCommand.NutrientValue> nutrients = nutrientsOf(node);

        List<IngestProductCommand.AllergenValue> allergenValues = new ArrayList<>();
        for (JsonNode tagNode : node.path("allergens_tags")) {
            String code = ALLERGEN_TAGS.get(tagNode.asText(""));
            if (code != null) {
                allergenValues.add(new IngestProductCommand.AllergenValue(code, AllergenDeclaration.CONTAINS));
            }
        }

        boolean complete = !nutrients.isEmpty() && ingredientsRaw != null;
        DataQuality dataQuality = complete ? DataQuality.RECENT : DataQuality.UNVERIFIED;

        return new IngestProductCommand(
                name.trim(),
                brandName,
                categoryCode,
                packSize.value(),
                packSize.unit(),
                gtin,
                gtinType,
                serving.value(),
                serving.declared(),
                ingredientsRaw,
                ingredientTokens,
                nutrients,
                allergenValues,
                SourceType.OPEN_FOOD_FACTS,
                CaptureMethod.OFF_IMPORT,
                null,
                "https://world.openfoodfacts.org/product/" + gtin,
                LICENSE_NOTE,
                VersionStatus.NEEDS_VERIFICATION,
                dataQuality,
                null,
                null,
                novaGroupOf(node),
                dietFlagOf(node),
                true,
                fvnlEstimateOf(node));
    }

    // -------------------------------------------------------------------------- enrichment

    /** What a re-fetched row may fill into an existing version (null → value only, downstream). */
    public static EnrichmentFacts toEnrichmentFacts(JsonNode node, String gtin) {
        ParsedQuantity serving = parseServing(node);
        return new EnrichmentFacts(
                novaGroupOf(node),
                dietFlagOf(node),
                true,
                serving.value(),
                serving.declared(),
                fvnlEstimateOf(node),
                nutrientsOf(node),
                SourceType.OPEN_FOOD_FACTS,
                "https://world.openfoodfacts.org/product/" + gtin,
                LICENSE_NOTE);
    }

    // ------------------------------------------------------------------------------ fields

    static List<IngestProductCommand.NutrientValue> nutrientsOf(JsonNode node) {
        NutrientBasis basis = "100ml".equalsIgnoreCase(node.path("nutrition_data_per").asText(""))
                ? NutrientBasis.PER_100ML : NutrientBasis.PER_100G;
        List<IngestProductCommand.NutrientValue> nutrients = new ArrayList<>();
        JsonNode nutrimentsNode = node.path("nutriments");
        for (NutrientMapping m : NUTRIENT_MAPPINGS) {
            Double value = asDouble(nutrimentsNode.get(m.offKey()));
            if (value == null || value < 0) {
                continue;
            }
            BigDecimal amount = BigDecimal.valueOf(value * m.multiplier());
            nutrients.add(new IngestProductCommand.NutrientValue(m.code(), amount, m.unit(), basis));
        }
        return nutrients;
    }

    /** 1–4 only; OFF's top-level {@code nova_group} or the legacy {@code nutriments["nova-group"]}. */
    static Integer novaGroupOf(JsonNode node) {
        JsonNode raw = node.path("nova_group");
        if (raw.isMissingNode() || raw.isNull()) {
            raw = node.path("nutriments").path("nova-group");
        }
        Double value = asDouble(raw);
        if (value == null || value != Math.rint(value) || value < 1 || value > 4) {
            return null;
        }
        return value.intValue();
    }

    /**
     * From {@code ingredients_analysis_tags}: {@code en:non-vegetarian} → NON_VEG,
     * {@code en:vegan} → VEGAN, {@code en:vegetarian} → VEG; the "maybe-" and
     * "-status-unknown" tags and anything else → UNKNOWN. Always an estimate — OFF derives
     * these from the ingredient list, not from the pack's green/brown mark.
     *
     * <p>{@code en:non-vegan} is deliberately <em>not</em> read as non-vegetarian: OFF sets it
     * for anything with milk, honey or egg, so a milk biscuit carries {@code en:non-vegan}
     * alongside {@code en:vegetarian} (or {@code en:vegetarian-status-unknown}). It only rules
     * out VEGAN.
     */
    static DietFlag dietFlagOf(JsonNode node) {
        boolean vegan = false;
        boolean nonVegan = false;
        boolean vegetarian = false;
        boolean nonVegetarian = false;
        for (JsonNode tagNode : node.path("ingredients_analysis_tags")) {
            switch (tagNode.asText("")) {
                case "en:vegan" -> vegan = true;
                case "en:non-vegan" -> nonVegan = true;
                case "en:vegetarian" -> vegetarian = true;
                case "en:non-vegetarian" -> nonVegetarian = true;
                default -> { }
            }
        }
        if (nonVegan) {
            vegan = false;
        }
        if (nonVegetarian) {
            return DietFlag.NON_VEG;
        }
        if (vegan) {
            return DietFlag.VEGAN;
        }
        if (vegetarian) {
            return DietFlag.VEG;
        }
        return DietFlag.UNKNOWN;
    }

    static BigDecimal fvnlEstimateOf(JsonNode node) {
        Double value = asDouble(node.path("nutriments").get(FVNL_ESTIMATE_KEY));
        if (value == null || value < 0 || value > 100) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }

    /** {@code serving_quantity} (grams/mL) with {@code serving_size} as the declared text; both optional. */
    static ParsedQuantity parseServing(JsonNode node) {
        String declared = trimToNull(node.path("serving_size").asText(null));
        Double quantity = asDouble(node.path("serving_quantity"));
        BigDecimal value = null;
        if (quantity != null && quantity > 0) {
            value = BigDecimal.valueOf(quantity);
        } else if (declared != null) {
            Matcher matcher = SERVING_PATTERN.matcher(declared);
            if (matcher.find()) {
                try {
                    value = new BigDecimal(matcher.group(1).replace(",", "."));
                } catch (NumberFormatException e) {
                    value = null;
                }
            }
        }
        return new ParsedQuantity(value, null, declared);
    }

    static String firstOf(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return null;
        }
        String first = commaSeparated.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * Open Food Facts lists {@code categories_tags} from most general to most specific
     * (e.g. a bread's own top-level ancestor tag is literally
     * {@code en:plant-based-foods-and-beverages} — it contains "beverages" as a substring
     * despite the product not being one). Scanning from the END of the list checks the most
     * specific tag first, so a real leaf category (e.g. {@code en:breads}) wins before a
     * broad umbrella ancestor can produce a false match.
     */
    static String mapCategory(JsonNode categoriesTags) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : categoriesTags) {
            tags.add(tagNode.asText("").toLowerCase());
        }
        for (int i = tags.size() - 1; i >= 0; i--) {
            String tag = tags.get(i);
            for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
                if (tag.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    static GtinType inferGtinType(String gtin) {
        return switch (gtin.length()) {
            case 8 -> GtinType.EAN8;
            case 12 -> GtinType.UPCA;
            case 13 -> GtinType.EAN13;
            case 14 -> GtinType.ITF14;
            default -> null;
        };
    }

    record ParsedQuantity(BigDecimal value, String unit, String declared) {
        static final ParsedQuantity EMPTY = new ParsedQuantity(null, null, null);
    }

    static ParsedQuantity parseQuantity(String quantity) {
        if (quantity == null) {
            return ParsedQuantity.EMPTY;
        }
        Matcher matcher = QUANTITY_PATTERN.matcher(quantity);
        if (!matcher.find()) {
            return ParsedQuantity.EMPTY;
        }
        try {
            BigDecimal value = new BigDecimal(matcher.group(1).replace(",", "."));
            String unit = matcher.group(2).toLowerCase();
            return new ParsedQuantity(value, unit, null);
        } catch (NumberFormatException e) {
            return ParsedQuantity.EMPTY;
        }
    }

    static List<String> splitIngredients(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c == '(' || c == '[') {
                depth++;
                current.append(c);
            } else if (c == ')' || c == ']') {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if (c == ',' && depth == 0) {
                addToken(tokens, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addToken(tokens, current);
        return tokens.size() > 60 ? tokens.subList(0, 60) : tokens;
    }

    private static void addToken(List<String> tokens, StringBuilder current) {
        String token = current.toString().trim();
        if (!token.isEmpty()) {
            tokens.add(token);
        }
    }

    static Double asDouble(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
