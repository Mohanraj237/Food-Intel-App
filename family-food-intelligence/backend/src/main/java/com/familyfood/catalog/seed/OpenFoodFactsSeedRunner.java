package com.familyfood.catalog.seed;

import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.domain.DataQuality;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Development-catalogue seed: imports the Open Food Facts India-market snapshot fetched by
 * {@code tools/data-import/fetch-openfoodfacts.mjs} into the real catalogue schema through
 * {@link ProductIngestionService} — the same write path ops-review contribution approval
 * uses, so every invariant (D-014 versioning, D-015 per-field provenance) is honoured.
 * Row parsing lives in {@link OpenFoodFactsRowParser}, shared with the enrichment runner.
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
 * fetching a larger snapshot) only adds genuinely new products, never duplicates. To fill
 * newly-fetched facts into products that already exist, run
 * {@link OpenFoodFactsEnrichmentRunner} ({@code seed-catalog-enrich}) instead.
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
        int withNova = 0;
        int withDietFlag = 0;
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

                    IngestProductCommand cmd = OpenFoodFactsRowParser.toCommand(node, gtin, name);
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
                    if (cmd.novaGroup() != null) {
                        withNova++;
                    }
                    if (cmd.dietFlag() != null && cmd.dietFlag() != com.familyfood.catalog.domain.DietFlag.UNKNOWN) {
                        withDietFlag++;
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
        log.info("With NOVA group:      {}", withNova);
        log.info("With diet flag:       {}", withDietFlag);
        log.info("Uncategorised:        {}", uncategorized);
        log.info("By category: {}", byCategory);
        log.info("By data quality: {}", byQuality);
    }
}
