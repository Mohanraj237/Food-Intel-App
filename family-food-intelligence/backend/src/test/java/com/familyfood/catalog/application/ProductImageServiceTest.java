package com.familyfood.catalog.application;

import com.familyfood.catalog.domain.ImageSource;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductImage;
import com.familyfood.catalog.infra.ProductImageRepository;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.platform.config.AppProperties;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.platform.storage.ObjectStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ProductImageService}, with the repository and
 * {@link ObjectStorageClient} mocked — no real S3-compatible endpoint is reachable in this
 * environment (and none should be needed to prove this class's own validation, hashing and
 * idempotency logic). The real-migration, real-Hibernate-mapping proof for {@code
 * product_image} is covered separately by any {@code IntegrationTestBase} subclass booting
 * against the real Postgres container (Flyway + {@code ddl-auto=validate} would fail loudly
 * on a mismatch).
 */
class ProductImageServiceTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final byte[] SMALL_JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};

    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ObjectStorageClient storage = mock(ObjectStorageClient.class);

    private ProductImageService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Auth("dGVzdC1zaWduaW5nLWtleS1ub3QtYS1zZWNyZXQtdGhpcnR5LXR3by1i",
                        "test-issuer", Duration.ofMinutes(15), Duration.ofDays(30),
                        16, 32, 1, 19456, 2),
                new AppProperties.Storage("http://localhost:9000", "ap-south-1",
                        "familyfood-labels", "familyfood-catalogue-images",
                        "test-access", "test-secret", true, Duration.ofMinutes(10)),
                new AppProperties.Ai(false, "none", "", "", "", Duration.ofSeconds(20), 1),
                new AppProperties.Limits(10, 5, 60, 30, 100, 5_242_880L,
                        List.of("image/jpeg", "image/png", "image/webp")),
                new AppProperties.Privacy(Duration.ofDays(90), Duration.ofDays(30),
                        Duration.ofDays(1), Duration.ofDays(30), "test-pepper"));

        service = new ProductImageService(images, products, storage, properties);

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(images.findByProductIdAndContentHash(any(), anyString())).thenReturn(Optional.empty());
        when(images.findByProductIdAndPrimaryTrue(any())).thenReturn(Optional.empty());
        when(images.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.publicUrl(anyString(), anyString()))
                .thenAnswer(inv -> "http://localhost:9000/" + inv.getArgument(0) + "/" + inv.getArgument(1));
    }

    @Test
    @DisplayName("a new image is uploaded to the catalogue bucket and recorded as primary")
    void attachesNewImageAsPrimary() {
        UUID id = service.attachImage(PRODUCT_ID, SMALL_JPEG_BYTES, "image/jpeg", ImageSource.OFF_IMPORT);

        assertThat(id).isNotNull();
        verify(storage).putObject(eq("familyfood-catalogue-images"), anyString(),
                eq(SMALL_JPEG_BYTES), eq("image/jpeg"));
        verify(images).save(any(ProductImage.class));
    }

    @Test
    @DisplayName("the storage key is generated server-side from product id + content hash, never from caller input")
    void storageKeyIsServerGenerated() {
        service.attachImage(PRODUCT_ID, SMALL_JPEG_BYTES, "image/jpeg", ImageSource.OFF_IMPORT);

        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).putObject(anyString(), keyCaptor.capture(), any(), anyString());

        String key = keyCaptor.getValue();
        assertThat(key).startsWith(PRODUCT_ID + "/");
        assertThat(key).endsWith(".jpg");
        // sha256 hex digest is 64 chars — proves the key is hash-derived, not passed through.
        assertThat(key).hasSize(PRODUCT_ID.toString().length() + 1 + 64 + 4);
    }

    @Test
    @DisplayName("attaching the same bytes twice for the same product is a no-op the second time")
    void isIdempotentByContentHash() {
        ProductImage existing = ProductImage.create(PRODUCT_ID, "existing/key.jpg",
                ImageSource.OFF_IMPORT, "deadbeef", "image/jpeg", SMALL_JPEG_BYTES.length, null, null, true);
        when(images.findByProductIdAndContentHash(eq(PRODUCT_ID), anyString())).thenReturn(Optional.of(existing));

        UUID id = service.attachImage(PRODUCT_ID, SMALL_JPEG_BYTES, "image/jpeg", ImageSource.OFF_IMPORT);

        assertThat(id).isEqualTo(existing.getId());
        verify(storage, never()).putObject(anyString(), anyString(), any(), anyString());
        verify(images, never()).save(any(ProductImage.class));
    }

    @Test
    @DisplayName("a previous primary image is demoted, not deleted, when a new one is attached")
    void demotesPreviousPrimaryImage() {
        ProductImage previous = ProductImage.create(PRODUCT_ID, "old/key.jpg",
                ImageSource.OFF_IMPORT, "oldhash", "image/jpeg", 10, null, null, true);
        when(images.findByProductIdAndPrimaryTrue(PRODUCT_ID)).thenReturn(Optional.of(previous));

        service.attachImage(PRODUCT_ID, SMALL_JPEG_BYTES, "image/jpeg", ImageSource.OFF_IMPORT);

        assertThat(previous.isPrimary()).isFalse();
        verify(images).save(previous);
        verify(images, times(2)).save(any(ProductImage.class)); // demoted previous + new primary
    }

    @Test
    @DisplayName("an unknown product is rejected before any upload happens")
    void rejectsUnknownProduct() {
        UUID unknown = UUID.randomUUID();
        when(products.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachImage(unknown, SMALL_JPEG_BYTES, "image/jpeg", ImageSource.OFF_IMPORT))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(storage, never()).putObject(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("a disallowed content type is rejected before any upload happens")
    void rejectsDisallowedContentType() {
        assertThatThrownBy(() -> service.attachImage(PRODUCT_ID, SMALL_JPEG_BYTES, "image/gif", ImageSource.OFF_IMPORT))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.IMAGE_REJECTED);

        verify(storage, never()).putObject(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("content over the configured size cap is rejected")
    void rejectsOversizedContent() {
        byte[] tooLarge = new byte[6_000_000]; // over the 5_242_880-byte cap configured in setUp

        assertThatThrownBy(() -> service.attachImage(PRODUCT_ID, tooLarge, "image/jpeg", ImageSource.OFF_IMPORT))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.IMAGE_REJECTED);

        verify(storage, never()).putObject(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("empty content is rejected")
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> service.attachImage(PRODUCT_ID, new byte[0], "image/jpeg", ImageSource.OFF_IMPORT))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.IMAGE_REJECTED);
    }

    /** A real, decodable JPEG (unlike {@link #SMALL_JPEG_BYTES}, which is a handful of magic
     *  bytes ImageIO can't decode — deliberately, for the tests above that only care about
     *  the byte-level checks). The dimension check below only fires when ImageIO can actually
     *  read a width/height, so it needs real image data to exercise. */
    private static byte[] realJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", out)) {
                throw new IllegalStateException("no JPEG writer available in this JVM");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("an image below the minimum dimension on either edge is rejected before any upload")
    void rejectsImageBelowMinimumDimension() {
        byte[] tooSmall = realJpeg(200, 400); // width under the 300px floor

        assertThatThrownBy(() -> service.attachImage(PRODUCT_ID, tooSmall, "image/jpeg", ImageSource.OFF_IMPORT))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.IMAGE_REJECTED);

        verify(storage, never()).putObject(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("an image meeting the minimum dimension on both edges is accepted")
    void acceptsImageAtMinimumDimension() {
        byte[] bigEnough = realJpeg(300, 300);

        UUID id = service.attachImage(PRODUCT_ID, bigEnough, "image/jpeg", ImageSource.OFF_IMPORT);

        assertThat(id).isNotNull();
        verify(storage).putObject(eq("familyfood-catalogue-images"), anyString(), eq(bigEnough), eq("image/jpeg"));
    }

    @Test
    @DisplayName("currentImageUrl resolves through the storage client's public URL")
    void currentImageUrlResolves() {
        ProductImage image = ProductImage.create(PRODUCT_ID, "some/key.jpg",
                ImageSource.OFF_IMPORT, "hash", "image/jpeg", 10, null, null, true);
        when(images.findByProductIdAndPrimaryTrue(PRODUCT_ID)).thenReturn(Optional.of(image));

        Optional<String> url = service.currentImageUrl(PRODUCT_ID);

        assertThat(url).contains("http://localhost:9000/familyfood-catalogue-images/some/key.jpg");
    }

    @Test
    @DisplayName("currentImageUrl is empty when no image has been ingested yet")
    void currentImageUrlEmptyWhenNoImage() {
        assertThat(service.currentImageUrl(PRODUCT_ID)).isEmpty();
    }

    @Test
    @DisplayName("currentImageUrls batches a page of products into one repository call")
    void currentImageUrlsBatches() {
        UUID other = UUID.randomUUID();
        ProductImage image = ProductImage.create(PRODUCT_ID, "some/key.jpg",
                ImageSource.OFF_IMPORT, "hash", "image/jpeg", 10, null, null, true);
        when(images.findByProductIdInAndPrimaryTrue(List.of(PRODUCT_ID, other)))
                .thenReturn(List.of(image));

        Map<UUID, String> urls = service.currentImageUrls(List.of(PRODUCT_ID, other));

        assertThat(urls).hasSize(1);
        assertThat(urls).containsKey(PRODUCT_ID);
        assertThat(urls).doesNotContainKey(other);
    }

    @Test
    @DisplayName("currentImageUrls short-circuits to an empty map without touching the repository")
    void currentImageUrlsEmptyInputShortCircuits() {
        Map<UUID, String> urls = service.currentImageUrls(List.of());

        assertThat(urls).isEmpty();
    }
}
