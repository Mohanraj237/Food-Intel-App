package com.familyfood.catalog.seed;

import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.GtinType;
import com.familyfood.catalog.domain.NutrientBasis;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.infra.ProductBarcodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Development-catalogue seed: imports the Open Food Facts India-market snapshot fetched by
 * {@code tools/data-import/fetch-openfoodfacts.mjs} into the real catalogue schema through
 * {@link ProductIngestionService} — the same write path ops-review contribution approval
 * uses, so every invariant (D-014 versioning, D-015 per-field provenance) is honoured.
 *
 * <p><b>Never runs by accident.</b> Gated behind the {@code seed-catalog} Spring profile,
 * which is not part of any normal {@code local}/{@code test}/{@code prod} activation. Run
 * it explicitly:
 *
 * <pre>
 *   cd backend
 *   mvn spring-boot:run -Dspring-boot.run.profiles=local,seed-catalog
 * </pre>
 *
 * <p><b>Idempotent.</b> Before ingesting a row, it checks whether an active barcode for
 * that GTIN already exists and skips the row if so — re-running the importer (e.g. after
 * fetching a larger snapshot) only adds genuinely new products, never duplicates.
 *
 * <p><b>Honest, not verified.</b> Every imported version is created with
 * {@code VersionStatus.NEEDS_VERIFICATION} and a {@code DataQuality} reflecting only how
 * complete the source row was — never {@code VERIFIED}. Nothing here invents a nutrient
 * value, an allergen, or a category: a field absent from the source stays absent.
 *
 * <p>Data licensing: Open Food Facts is licensed under the Open Database License (ODbL)
 * 1.0 (https://opendatacommons.org/licenses/odbl/1.0/). Attribution is recorded per
 * imported row on {@code product_source.license_note} and must also be visible in the
 * product UI (see {@code docs/decisions/decision-log.md} for the decision recording this
 * import and the attribution requirement).
 */
@Component
@Profile("seed-catalog")
public class OpenFoodFactsSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsSeedRunner.class);

    private static final String LICENSE_NOTE =
            "Data from Open Food Facts (https://world.openfoodfacts.org), licensed under the "
          + "Open Database License (ODbL) v1.0 (https://opendatacommons.org/licenses/odbl/1.0/). "
          + "Contents (nutrition/ingredients facts) additionally under the Database Contents "
          + "License (DbCL).";

    /** OFF categories_tags substring -> our closed category code (V1__extensions_and_reference_data.sql). */
    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();
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
    private static final Map<String, String> ALLERGEN_TAGS = Map.ofEntries(
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
    private record NutrientMapping(String offKey, String code, String unit, double multiplier) {
    }

    private static final List<NutrientMapping> NUTRIENT_MAPPINGS = List.of(
            new NutrientMapping("energy-kcal_100g", "ENERGY_KCAL", "kcal", 1.0),
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

    private static final Pattern QUANTITY_PATTERN =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(kg|g|l|ml|L|G|Kg|KG|ML|Ltr|ltr)");

    private final ProductIngestionService ingestionService;
    private final ProductBarcodeRepository barcodes;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${familyfood.seed.catalog.source:classpath:db/seed/openfoodfacts-india.jsonl}")
    private String sourcePath;

    public OpenFoodFactsSeedRunner(ProductIngestionService ingestionService,
                                    ProductBarcodeRepository barcodes,
                                    ResourceLoader resourceLoader) {
        this.ingestionService = ingestionService;
        this.barcodes = barcodes;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) throws Exception {
        Resource resource = resourceLoader.getResource(sourcePath);
        if (!resource.exists()) {
            log.error("Catalogue seed source not found at {} — run "
                    + "tools/data-import/fetch-openfoodfacts.mjs first.", sourcePath);
            return;
        }

        int read = 0;
        int imported = 0;
        int skippedExisting = 0;
        int skippedInvalid = 0;
        int withNutrition = 0;
        int withIngredients = 0;
        int withAllergens = 0;
        int uncategorized = 0;
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        Map<DataQuality, Integer> byQuality = new LinkedHashMap<>();

        log.info("Starting Open Food Facts catalogue seed from {}", sourcePath);

        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                read++;
                try {
                    JsonNode node = mapper.readTree(line);
                    String gtin = node.path("code").asText(null);
                    String name = node.path("product_name").asText(null);
                    if (gtin == null || name == null || name.isBlank()) {
                        skippedInvalid++;
                        continue;
                    }
                    if (!barcodes.findByGtinAndActiveTrue(gtin).isEmpty()) {
                        skippedExisting++;
                        continue;
                    }

                    IngestProductCommand cmd = toCommand(node, gtin, name);
                    ingestionService.ingestNewProduct(cmd);
                    imported++;

                    if (!cmd.nutrients().isEmpty()) {
                        withNutrition++;
                    }
                    if (cmd.ingredientsRawText() != null && !cmd.ingredientsRawText().isBlank()) {
                        withIngredients++;
                    }
                    if (!cmd.allergens().isEmpty()) {
                        withAllergens++;
                    }
                    if (cmd.categoryCode() == null) {
                        uncategorized++;
                    } else {
                        byCategory.merge(cmd.categoryCode(), 1, Integer::sum);
                    }
                    byQuality.merge(cmd.dataQuality(), 1, Integer::sum);

                    if (imported % 500 == 0) {
                        log.info("...imported {} products so far ({} read)", imported, read);
                    }
                } catch (Exception rowError) {
                    skippedInvalid++;
                    log.warn("Skipping malformed row {}: {}", read, rowError.getMessage());
                }
            }
        }

        log.info("=== Open Food Facts catalogue seed complete ===");
        log.info("Rows read:            {}", read);
        log.info("Products imported:    {}", imported);
        log.info("Skipped (duplicate):  {}", skippedExisting);
        log.info("Skipped (invalid):    {}", skippedInvalid);
        log.info("With nutrition data:  {}", withNutrition);
        log.info("With ingredients:     {}", withIngredients);
        log.info("With allergen flags:  {}", withAllergens);
        log.info("Uncategorised:        {}", uncategorized);
        log.info("By category: {}", byCategory);
        log.info("By data quality: {}", byQuality);
    }

    private IngestProductCommand toCommand(JsonNode node, String gtin, String name) {
        String brandName = firstOf(node.path("brands").asText(null));
        String categoryCode = mapCategory(node.path("categories_tags"));
        GtinType gtinType = inferGtinType(gtin);

        ParsedQuantity packSize = parseQuantity(node.path("quantity").asText(null));

        String ingredientsRaw = trimToNull(node.path("ingredients_text").asText(null));
        List<String> ingredientTokens = splitIngredients(ingredientsRaw);

        List<IngestProductCommand.NutrientValue> nutrients = new ArrayList<>();
        JsonNode nutrimentsNode = node.path("nutriments");
        for (NutrientMapping m : NUTRIENT_MAPPINGS) {
            Double value = asDouble(nutrimentsNode.get(m.offKey()));
            if (value == null || value < 0) {
                continue;
            }
            BigDecimal amount = BigDecimal.valueOf(value * m.multiplier());
            nutrients.add(new IngestProductCommand.NutrientValue(
                    m.code(), amount, m.unit(), NutrientBasis.PER_100G));
        }

        List<IngestProductCommand.AllergenValue> allergenValues = new ArrayList<>();
        for (JsonNode tagNode : node.path("allergens_tags")) {
            String tag = tagNode.asText("");
            String code = ALLERGEN_TAGS.get(tag);
            if (code != null) {
                allergenValues.add(new IngestProductCommand.AllergenValue(
                        code, AllergenDeclaration.CONTAINS));
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
                null,
                null,
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
                null);
    }

    private static String firstOf(String commaSeparated) {
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
    private static String mapCategory(JsonNode categoriesTags) {
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

    private static GtinType inferGtinType(String gtin) {
        return switch (gtin.length()) {
            case 8 -> GtinType.EAN8;
            case 12 -> GtinType.UPCA;
            case 13 -> GtinType.EAN13;
            case 14 -> GtinType.ITF14;
            default -> null;
        };
    }

    private record ParsedQuantity(BigDecimal value, String unit) {
        static final ParsedQuantity EMPTY = new ParsedQuantity(null, null);
    }

    private static ParsedQuantity parseQuantity(String quantity) {
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
            return new ParsedQuantity(value, unit);
        } catch (NumberFormatException e) {
            return ParsedQuantity.EMPTY;
        }
    }

    private static List<String> splitIngredients(String raw) {
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

    private static Double asDouble(JsonNode node) {
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
