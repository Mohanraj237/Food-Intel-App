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
import java.util.regex.Pattern;

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

    /**
     * Open Food Facts' {@code image_url}/{@code image_front_url} fields point at a
     * pre-resized "display" derivative — {@code front_en.11.400.jpg} is at most 400px on the
     * long edge. Measured directly against this project's own fetched snapshot: 872 of 882
     * downloaded images were under 400px, some as small as 101×61 — nowhere near large enough
     * for the card sizes this app actually renders (150-360px), so they get upscaled and come
     * out blurry. OFF also serves the original upload at the same path with the size segment
     * replaced by the literal {@code full} — e.g. {@code front_en.11.full.jpg}, confirmed
     * directly (2535×2735 for the same image the {@code .400} variant above came from). This
     * pattern rewrites a sized URL to that original; {@link #fetchAndAttach} tries it first
     * and falls back to the URL Open Food Facts actually gave us if the original isn't there
     * for that particular image (not every upload has one).
     */
    private static final Pattern SIZED_IMAGE_URL = Pattern.compile("\\.(\\d{2,4})\\.(jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    private static String fullResolutionUrl(String imageUrl) {
        return SIZED_IMAGE_URL.matcher(imageUrl).replaceFirst(".full.$2");
    }

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
        int rejected = 0;
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

                    switch (fetchAndAttach(productId, imageUrl)) {
                        case DOWNLOADED -> downloaded++;
                        case REJECTED -> rejected++;
                        case FAILED -> failed++;
                    }

                    int attempted = downloaded + rejected + failed;
                    if (attempted % 200 == 0 && attempted > 0) {
                        log.info("...processed {} of {} rows read ({} ok, {} rejected, {} failed)",
                                attempted, read, downloaded, rejected, failed);
                    }
                } catch (Exception rowError) {
                    failed++;
                    log.warn("Skipping row {} (image backfill): {}", read, rowError.getMessage());
                }
            }
        }

        log.info("=== Open Food Facts image backfill complete ===");
        log.info("Rows read:                    {}", read);
        log.info("No image_url in source:       {}", noImageUrl);
        log.info("No matching product:          {}", noMatchingProduct);
        log.info("Already had an image:         {}", alreadyHasImage);
        log.info("Downloaded and stored:        {}", downloaded);
        log.info("Rejected (too small/wrong type): {}", rejected);
        log.info("Failed (network/HTTP error):  {}", failed);
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

    private enum FetchOutcome { DOWNLOADED, REJECTED, FAILED }

    /** Tries the full-resolution rewrite first (see {@link #fullResolutionUrl}); if that
     *  isn't available for this particular image (a 404 is common — not every OFF upload
     *  has one) or gets rejected, falls back to the URL Open Food Facts actually gave us
     *  rather than leaving the product with no image at all over a resolution preference. */
    private FetchOutcome fetchAndAttach(UUID productId, String sourceUrl) {
        String fullUrl = fullResolutionUrl(sourceUrl);
        FetchOutcome outcome = attemptDownload(productId, fullUrl);
        if (outcome == FetchOutcome.DOWNLOADED || fullUrl.equals(sourceUrl)) {
            return outcome;
        }
        log.debug("Full-resolution image unavailable for product {} ({}), trying {}",
                productId, fullUrl, sourceUrl);
        return attemptDownload(productId, sourceUrl);
    }

    private FetchOutcome attemptDownload(UUID productId, String imageUrl) {
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
                return FetchOutcome.FAILED;
            }

            String contentType = response.headers().firstValue("Content-Type")
                    .map(ct -> ct.split(";")[0].trim().toLowerCase())
                    .orElse(guessContentTypeFromUrl(imageUrl));

            if (!ACCEPTABLE_CONTENT_TYPES.contains(contentType)) {
                log.warn("Image fetch for product {} had unacceptable content type '{}': {}",
                        productId, contentType, imageUrl);
                return FetchOutcome.FAILED;
            }

            productImages.attachImage(productId, response.body(), contentType, ImageSource.OFF_IMPORT);
            return FetchOutcome.DOWNLOADED;
        } catch (AppException appError) {
            // ProductImageService rejected the content — too small to render well, wrong
            // type, or over the byte-size cap. Distinct from a network/HTTP failure: this is
            // a real image that simply isn't good enough, not an unreachable one.
            log.warn("Rejected image for product {} ({}): {}", productId, appError.code(), imageUrl);
            return FetchOutcome.REJECTED;
        } catch (IOException | InterruptedException networkError) {
            if (networkError instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Network error fetching image for product {}: {}", productId, networkError.getMessage());
            return FetchOutcome.FAILED;
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
