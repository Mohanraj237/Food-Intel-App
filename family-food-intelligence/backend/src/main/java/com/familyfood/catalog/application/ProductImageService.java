package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.ImageSource;
import com.familyfood.catalog.domain.ProductImage;
import com.familyfood.catalog.infra.ProductImageRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.platform.config.AppProperties;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.storage.ObjectStorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single write path for catalogue images, mirroring how
 * {@link ProductIngestionService} is the single write path for catalogue facts. Validates,
 * hashes, uploads to the catalogue-image bucket (never {@code familyfood.storage.bucket},
 * which stays scoped to private label photos — see V11__product_image.sql), and records one
 * {@code product_image} row.
 *
 * <p><b>Idempotent by content.</b> The same bytes ingested twice for the same product (a
 * resumed import, a retried request) resolve to the same {@code content_hash} and are
 * detected before any upload or insert happens — the second call is a cheap no-op that
 * returns the existing row's id.
 *
 * <p><b>Never trusts a caller-supplied storage path.</b> The storage key is always generated
 * here from the product id and the content hash — never from a filename, URL path segment,
 * or anything else an import source or API caller sent verbatim (path-traversal risk; same
 * reasoning as {@code label_image}'s own storage-key comment).
 */
@Service
public class ProductImageService {

    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final ObjectStorageClient storage;
    private final AppProperties properties;

    public ProductImageService(ProductImageRepository images,
                                ProductRepository products,
                                ObjectStorageClient storage,
                                AppProperties properties) {
        this.images = images;
        this.products = products;
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * Validates, deduplicates, uploads and records one image as the product's current hero
     * image. Any previously-primary image for the product is demoted (never deleted — a
     * future review/audit trail can still find it), matching the "one current fact, prior
     * ones superseded, nothing overwritten" spirit of {@code product_version} (D-014), scaled
     * down to the "no gallery yet" scope this table intentionally has.
     *
     * @return the id of the (possibly pre-existing) {@code product_image} row.
     * @throws AppException {@link ErrorCode#RESOURCE_NOT_FOUND} if the product does not
     *                       exist, {@link ErrorCode#IMAGE_REJECTED} if the content type,
     *                       byte size or pixel dimensions are not acceptable (below
     *                       {@link #MIN_DIMENSION_PX} on either edge), {@link ErrorCode#STORAGE_UNAVAILABLE}
     *                       if the upload itself fails.
     */
    @Transactional
    public UUID attachImage(UUID productId, byte[] content, String mimeType, ImageSource source) {
        if (products.findById(productId).isEmpty()) {
            throw AppException.notFound("product");
        }
        String extension = validateAndExtensionFor(mimeType);
        validateSize(content);

        // Decoded and checked before anything is uploaded — a rejected image should never
        // reach storage in the first place (an orphaned object nobody references).
        Dimensions dimensions = readDimensions(content);
        validateDimensions(dimensions);

        String contentHash = sha256Hex(content);

        Optional<ProductImage> existing = images.findByProductIdAndContentHash(productId, contentHash);
        if (existing.isPresent()) {
            // Same bytes already stored for this product — a resumed/retried ingest is a
            // no-op, not a duplicate upload or a second row.
            return existing.get().getId();
        }

        String storageKey = productId + "/" + contentHash + "." + extension;
        storage.putObject(properties.storage().catalogueBucket(), storageKey, content, mimeType);

        images.findByProductIdAndPrimaryTrue(productId).ifPresent(previous -> {
            previous.clearPrimary();
            images.save(previous);
        });

        ProductImage image = ProductImage.create(productId, storageKey, source, contentHash, mimeType,
                content.length, dimensions.width(), dimensions.height(), true);
        images.save(image);
        return image.getId();
    }

    /** The current hero image URL for a product, or empty if none has been ingested. */
    @Transactional(readOnly = true)
    public Optional<String> currentImageUrl(UUID productId) {
        return images.findByProductIdAndPrimaryTrue(productId).map(this::urlFor);
    }

    /**
     * Batch form of {@link #currentImageUrl(UUID)} for a page of search/summary results — one
     * query rather than N, matching how {@code ProductQueryService.buildSummaries} already
     * batches brand/category/nutrient lookups.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> currentImageUrls(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return images.findByProductIdInAndPrimaryTrue(productIds).stream()
                .collect(Collectors.toMap(ProductImage::getProductId, this::urlFor));
    }

    private String urlFor(ProductImage image) {
        return storage.publicUrl(properties.storage().catalogueBucket(), image.getStorageKey());
    }

    private String validateAndExtensionFor(String mimeType) {
        if (mimeType == null || !properties.limits().allowedImageContentTypes().contains(mimeType)) {
            throw new AppException(ErrorCode.IMAGE_REJECTED, Map.of("mimeType", String.valueOf(mimeType)));
        }
        String extension = EXTENSION_BY_MIME.get(mimeType);
        if (extension == null) {
            // Reachable only if allowedImageContentTypes ever lists a type this class does
            // not yet map to an extension — fail honestly rather than guess one.
            throw new AppException(ErrorCode.IMAGE_REJECTED, Map.of("mimeType", mimeType));
        }
        return extension;
    }

    private void validateSize(byte[] content) {
        if (content.length == 0 || content.length > properties.limits().maxImageBytes()) {
            throw new AppException(ErrorCode.IMAGE_REJECTED, Map.of("byteSize", content.length));
        }
    }

    /** Below this on either edge, a card-sized (150-360px) render has to upscale the image
     *  and the result looks blurry/untrustworthy rather than like a real product photo —
     *  exactly the complaint a tiny thumbnail earns. Only enforced when the dimensions were
     *  actually decoded; a format {@code ImageIO} can't read is left to {@link #readDimensions}
     *  own "never reject on that basis" rule. */
    private static final int MIN_DIMENSION_PX = 300;

    private void validateDimensions(Dimensions dimensions) {
        Integer width = dimensions.width();
        Integer height = dimensions.height();
        if (width != null && height != null && (width < MIN_DIMENSION_PX || height < MIN_DIMENSION_PX)) {
            throw new AppException(ErrorCode.IMAGE_REJECTED, Map.of("width", width, "height", height));
        }
    }

    /** Best-effort only. A format {@code ImageIO} cannot decode (e.g. some WebP variants
     *  without a plugin) simply leaves width/height null — never a reason to reject the
     *  image itself. */
    private static Dimensions readDimensions(byte[] content) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
            return decoded == null
                    ? new Dimensions(null, null)
                    : new Dimensions(decoded.getWidth(), decoded.getHeight());
        } catch (IOException e) {
            return new Dimensions(null, null);
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JCA standard names) — this cannot happen
            // on any conforming JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Dimensions(Integer width, Integer height) {}
}
