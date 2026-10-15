package com.familyfood.catalog.seed;

import com.familyfood.catalog.application.EnrichmentFacts;
import com.familyfood.catalog.application.ProductEnrichmentService;
import com.familyfood.catalog.domain.ProductBarcode;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fills facts a newer Open Food Facts snapshot carries (NOVA group, diet flag, serving size,
 * FVNL estimate, nutrients the first import lacked, additive marks) into products that
 * already exist — the companion to {@link OpenFoodFactsSeedRunner}, which only ever adds new
 * products. Goes through {@link ProductEnrichmentService}, so every fill is null → value
 * only, carries its own {@code product_source} row and a {@code product_change_log} entry,
 * and triggers a score recompute (D-059).
 *
 * <p><b>Never runs by accident.</b> Gated behind the {@code seed-catalog-enrich} profile:
 *
 * <pre>
 *   java -jar backend/target/family-food-intelligence-0.1.0-SNAPSHOT.jar --spring.profiles.active=local,seed-catalog-enrich
 * </pre>
 *
 * <p><b>Idempotent.</b> A product whose version already holds every fact the row offers is
 * a no-op (nothing written, no event). A GTIN with no active barcode, or one that is
 * ambiguous (AC-4.4), is skipped — this runner never creates a product.
 */
@Component
@Profile("seed-catalog-enrich")
public class OpenFoodFactsEnrichmentRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsEnrichmentRunner.class);

    private final ProductEnrichmentService enrichmentService;
    private final ProductBarcodeRepository barcodes;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${familyfood.seed.catalog.source:classpath:db/seed/openfoodfacts-india.jsonl}")
    private String sourcePath;

    public OpenFoodFactsEnrichmentRunner(ProductEnrichmentService enrichmentService,
                                         ProductBarcodeRepository barcodes,
                                         ResourceLoader resourceLoader) {
        this.enrichmentService = enrichmentService;
        this.barcodes = barcodes;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) throws Exception {
        Resource resource = resourceLoader.getResource(sourcePath);
        if (!resource.exists()) {
            log.error("Catalogue seed source not found at {} — run "
                    + "tools/data-import/refetch-openfoodfacts.mjs --merge first.", sourcePath);
            return;
        }

        int read = 0;
        int enriched = 0;
        int unchanged = 0;
        int notInCatalogue = 0;
        int skippedInvalid = 0;
        Map<String, Integer> byField = new LinkedHashMap<>();

        log.info("Starting Open Food Facts enrichment from {}", sourcePath);

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
                    if (gtin == null) {
                        skippedInvalid++;
                        continue;
                    }
                    List<ProductBarcode> matches = barcodes.findByGtinAndActiveTrue(gtin);
                    Set<UUID> productIds = new java.util.HashSet<>();
                    for (ProductBarcode barcode : matches) {
                        productIds.add(barcode.getProductId());
                    }
                    if (productIds.size() != 1) {
                        notInCatalogue++;
                        continue;
                    }

                    EnrichmentFacts facts = OpenFoodFactsRowParser.toEnrichmentFacts(node, gtin);
                    List<String> filled = enrichmentService.enrichCurrentVersion(productIds.iterator().next(), facts);
                    if (filled.isEmpty()) {
                        unchanged++;
                    } else {
                        enriched++;
                        for (String field : filled) {
                            byField.merge(field.startsWith("nutrient:") || field.startsWith("additives:")
                                    ? field.substring(0, field.indexOf(':')) : field, 1, Integer::sum);
                        }
                    }

                    if ((enriched + unchanged) % 500 == 0 && enriched + unchanged > 0) {
                        log.info("...processed {} products so far ({} read)", enriched + unchanged, read);
                    }
                } catch (Exception rowError) {
                    skippedInvalid++;
                    log.warn("Skipping row {}: {}", read, rowError.getMessage());
                }
            }
        }

        log.info("=== Open Food Facts enrichment complete ===");
        log.info("Rows read:              {}", read);
        log.info("Products enriched:      {}", enriched);
        log.info("Already complete:       {}", unchanged);
        log.info("Not in catalogue:       {}", notInCatalogue);
        log.info("Skipped (invalid):      {}", skippedInvalid);
        log.info("Fills by field: {}", byField);
    }
}
