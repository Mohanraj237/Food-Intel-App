package com.familyfood.catalog.seed;

import com.familyfood.catalog.application.ProductImageService;
import com.familyfood.catalog.domain.ImageSource;
import com.familyfood.catalog.infra.ProductBarcodeRepository;
import com.familyfood.platform.error.AppException;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backfills {@code product_image} rows for products already imported by
 * {@link OpenFoodFactsSeedRunner}, using the {@code image_url} field Open Food Facts'
 * product API returns. Deliberately a separate step from the product-facts import, not a
 * parameter added to it:
 *
 * <ul>
 *   <li>the two operations have entirely different failure modes and costs — importing a
 *       product is one JSON row already on disk; backfilling its image is a live HTTP
 *       fetch per product against a third-party host that (as of this project's own
 *       README) has had sustained availability problems;</li>
 *   <li>it must be safely re-runnable on its own against a catalogue that already exists,
 *       without re-running (or risking corrupting) the product import itself.</li>
 * </ul>
 *
 * <p><b>Idempotent and resumable.</b> A product that already has a primary image is
 * skipped without a network call. A row whose image bytes are already stored for that
 * product (same content hash) is a no-op inside {@link ProductImageService} even if this
 * runner re-fetches them. Re-running after a partial run (or after Open Food Facts
 * recovers from an outage) only ever adds images for products that still lack one.
 *
 * <p><b>Store-and-attribute, never hotlink.</b> The image bytes are downloaded once and
 * stored in this application's own catalogue-image bucket
 * (see {@code familyfood.storage.catalogue-bucket}); nothing in the running application
 * ever points a user's browser directly at {@code images.openfoodfacts.org}. Provenance
 * travels with the row via {@link ImageSource#OFF_IMPORT} — the same attribution
 * obligation already recorded per-product on {@code product_source.license_note}
 * (D-031) covers the image, since it is the same source and the same licence.
 *
 * <p><b>Never runs by accident.</b> Gated behind the {@code seed-catalog-images} profile,
 * distinct from {@code seed-catalog} — running the product import never implies running
 * this. If Open Food Facts is unreachable, every row fails its own download individually
 * and is counted as skipped; the run still completes and reports an honest summary rather
 * than aborting or fabricating a placeholder image.
 */
@Component
@Profile("seed-catalog-images")
public class OpenFoodFactsImageBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsImageBackfillRunner.class);

    /** Mirrors familyfood.limits.allowed-image-content-types; kept local because a
     *  malformed/unexpected Content-Type header must never crash the whole run. */
    private static final Set<String> ACCEPTABLE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final String USER_AGENT =
            "FamilyFoodIntelligence-DevCatalogueImageBackfill/1.0 (+https://github.com/family-food-intelligence)";

    private final ProductImageService productImages;
    private final ProductBarcodeRepository barcodes;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${familyfood.seed.catalog.source:classpath:db/seed/openfoodfacts-india.jsonl}")
    private String sourcePath;

    public OpenFoodFactsImageBackfillRunner(ProductImageService productImages,
                                            ProductBarcodeRepository barcodes,
                                            ResourceLoader resourceLoader) {
        this.productImages = productImages;
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
        int noImageUrl = 0;
        int noMatchingProduct = 0;
        int alreadyHasImage = 0;
        int downloaded = 0;
        int failed = 0;

        log.info("Starting Open Food Facts image backfill from {}", sourcePath);

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
                    String imageUrl = firstNonBlank(
                            node.path("image_url").asText(null),
                            node.path("image_front_url").asText(null));

                    if (gtin == null) {
                        continue;
                    }
                    if (imageUrl == null) {
                        noImageUrl++;
                        continue;
                    }

                    UUID productId = resolveProductId(gtin);
                    if (productId == null) {
                        noMatchingProduct++;
                        continue;
                    }

                    if (productImages.currentImageUrl(productId).isPresent()) {
                        alreadyHasImage++;
                        continue;
                    }

                    if (fetchAndAttach(productId, imageUrl)) {
                        downloaded++;
                    } else {
                        failed++;
                    }

                    if ((downloaded + failed) % 200 == 0 && (downloaded + failed) > 0) {
                        log.info("...processed {} downloads ({} ok, {} failed) of {} rows read",
                                downloaded + failed, downloaded, failed, read);
                    }
                } catch (Exception rowError) {
                    failed++;
                    log.warn("Skipping row {} (image backfill): {}", read, rowError.getMessage());
                }
            }
        }

        log.info("=== Open Food Facts image backfill complete ===");
        log.info("Rows read:                {}", read);
        log.info("No image_url in source:   {}", noImageUrl);
        log.info("No matching product:      {}", noMatchingProduct);
        log.info("Already had an image:     {}", alreadyHasImage);
        log.info("Downloaded and stored:    {}", downloaded);
        log.info("Failed (network/decode):  {}", failed);
        if (downloaded == 0 && (noMatchingProduct + noImageUrl) < read) {
            log.warn("Zero images stored this run — if this is unexpected, Open Food Facts "
                    + "may be unreachable (a documented, sustained outage for this project). "
                    + "Re-run this profile later; the run is safely resumable.");
        }
    }

    /** Mirrors OpenFoodFactsSeedRunner's own "an active barcode identifies the product"
     *  lookup — never guesses between two products claiming the same GTIN. */
    private UUID resolveProductId(String gtin) {
        List<com.familyfood.catalog.domain.ProductBarcode> matches = barcodes.findByGtinAndActiveTrue(gtin);
        if (matches.size() != 1) {
            return null;
        }
        return matches.get(0).getProductId();
    }

    private boolean fetchAndAttach(UUID productId, String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.warn("Image fetch for product {} returned HTTP {}: {}",
                        productId, response.statusCode(), imageUrl);
                return false;
            }

            String contentType = response.headers().firstValue("Content-Type")
                    .map(ct -> ct.split(";")[0].trim().toLowerCase())
                    .orElse(guessContentTypeFromUrl(imageUrl));

            if (!ACCEPTABLE_CONTENT_TYPES.contains(contentType)) {
                log.warn("Image fetch for product {} had unacceptable content type '{}': {}",
                        productId, contentType, imageUrl);
                return false;
            }

            productImages.attachImage(productId, response.body(), contentType, ImageSource.OFF_IMPORT);
            return true;
        } catch (AppException appError) {
            // ProductImageService rejected the content (bad size/type) or storage was
            // unreachable — either way, this one product's image is skipped, not the run.
            log.warn("Rejected image for product {} ({}): {}", productId, appError.code(), imageUrl);
            return false;
        } catch (IOException | InterruptedException networkError) {
            if (networkError instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Network error fetching image for product {}: {}", productId, networkError.getMessage());
            return false;
        }
    }

    private static String guessContentTypeFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
