package com.familyfood.platform.cache;

import com.familyfood.catalog.application.CatalogCaches;
import com.familyfood.catalog.application.ProductImageService;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.ImageSource;
import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductStatus;
import com.familyfood.catalog.domain.ProductVersion;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.catalog.infra.ProductRepository;
import com.familyfood.catalog.infra.ProductVersionRepository;
import com.familyfood.platform.storage.ObjectStorageClient;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalogue caches against a real Redis (D-056): the key convention, TTLs, hits,
 * eviction on write, concurrent first reads, and a Redis that stops answering mid-flight.
 */
@TestPropertySource(properties = {
        "familyfood.cache.type=redis",
        "familyfood.cache.retry-after=PT1S"
})
class RedisCachingIT extends IntegrationTestBase {

    // BSD-licensed Redis line; any Redis-protocol server behaves the same for this test.
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    /** attachImage uploads to object storage, and none is reachable in tests. */
    @MockitoBean
    private ObjectStorageClient storage;

    @Autowired private CacheManager cacheManager;
    @Autowired private CacheCircuitBreaker breaker;
    @Autowired private StringRedisTemplate redis;
    @Autowired private MeterRegistry meters;
    @Autowired private Flyway flyway;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductQueryService products;
    @Autowired private ProductImageService productImages;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVersionRepository versionRepository;

    @BeforeEach
    void startFromAnEmptyCache() {
        // A previous test may have tripped the breaker; retry-after is one second here.
        await().atMost(Duration.ofSeconds(5)).until(() -> !breaker.isBypassing());
        // The first command opens the context's first connection, which a cold JVM may not
        // finish within the command timeout.
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() ->
                redis.execute((RedisCallback<Void>) connection -> {
                    connection.serverCommands().flushAll();
                    return null;
                }));
        productCache().clearStatistics();
        ((RedisCache) cacheManager.getCache(CatalogCaches.CATEGORIES)).clearStatistics();
    }

    private RedisCache productCache() {
        return (RedisCache) cacheManager.getCache(CatalogCaches.PRODUCT);
    }

    private Set<String> keys(String cacheName) {
        return redis.keys("ffi:" + cacheName + ":*");
    }

    private UUID saveProduct(String name) {
        Product product = productRepository.save(Product.create(
                name, name.toLowerCase(), null, null, null, null, ProductStatus.ACTIVE));
        versionRepository.save(ProductVersion.create(product.getId(), 1, VersionStatus.VERIFIED,
                true, null, null, null, null, null, null, null,
                CaptureMethod.MANUAL_ENTRY, null, DataQuality.UNVERIFIED));
        return product.getId();
    }

    private String productJson(UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a product read is stored under ffi:catalog.product:<version>::<id> with its TTL, and the next read is a hit")
    void productDetailIsCachedUnderTheConventionalKey() throws Exception {
        UUID id = saveProduct("Cached Crackers");

        String first = productJson(id);
        String second = productJson(id);

        assertThat(second).isEqualTo(first);
        // The version segment is the detail type's shape at the schema version actually applied
        // — read after the migrations ran, not while the cache manager was being built.
        String schemaVersion = flyway.info().current().getVersion().getVersion();
        String version = CacheKeys.versionOf(
                objectMapper.getTypeFactory().constructType(ProductDetailDto.class), schemaVersion);
        Set<String> keys = keys(CatalogCaches.PRODUCT);
        assertThat(keys).containsExactly("ffi:catalog.product:" + version + "::" + id);
        Long ttlSeconds = redis.getExpire(keys.iterator().next(), TimeUnit.SECONDS);
        assertThat(ttlSeconds).isBetween(1L, Duration.ofMinutes(30).toSeconds());
        assertThat(productCache().getStatistics().getMisses()).isEqualTo(1);
        assertThat(productCache().getStatistics().getHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("the category list is one entry, under the key 'all', for at most an hour")
    void categoriesAreOneEntry() throws Exception {
        mockMvc.perform(get("/api/v1/reference/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reference/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists());

        Set<String> keys = keys(CatalogCaches.CATEGORIES);
        assertThat(keys).singleElement().asString().matches("ffi:catalog\\.categories:[0-9a-f]{8}::all");
        assertThat(redis.getExpire(keys.iterator().next(), TimeUnit.SECONDS))
                .isBetween(1L, Duration.ofHours(1).toSeconds());
    }

    @Test
    @DisplayName("an unknown product is a 404, and nothing is cached for it")
    void notFoundIsNotCached() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        assertThat(keys(CatalogCaches.PRODUCT)).isEmpty();
    }

    @Test
    @DisplayName("attaching an image evicts that product, so the next read shows the image")
    void attachingAnImageEvictsTheProduct() throws Exception {
        UUID id = saveProduct("Pictured Puffs");
        when(storage.publicUrl(anyString(), anyString()))
                .thenAnswer(call -> "https://images.test/" + call.getArgument(1));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(keys(CatalogCaches.PRODUCT)).hasSize(1);

        productImages.attachImage(id, png(320, 320), "image/png", ImageSource.OFF_IMPORT);

        assertThat(keys(CatalogCaches.PRODUCT)).isEmpty();
        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(jsonPath("$.imageUrl").value(startsWith("https://images.test/" + id + "/")));
    }

    @Test
    @DisplayName("32 concurrent first reads of one product all get the same detail and leave one entry")
    void concurrentColdReadsAgree() throws Exception {
        UUID id = saveProduct("Contended Chips");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ProductDetailDto>> reads = new ArrayList<>();

        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 32; i++) {
                reads.add(callers.submit(() -> {
                    start.await();
                    return products.getProduct(id);
                }));
            }
            start.countDown();
            Set<ProductDetailDto> distinct = new HashSet<>();
            for (Future<ProductDetailDto> read : reads) {
                distinct.add(read.get(30, TimeUnit.SECONDS));
            }
            assertThat(distinct).singleElement()
                    .satisfies(detail -> assertThat(detail.name()).isEqualTo("Contended Chips"));
        }

        assertThat(keys(CatalogCaches.PRODUCT)).hasSize(1);
    }

    @Test
    @DisplayName("a Redis that stops answering costs one request the timeout; the rest skip it until it answers again")
    void hungRedisIsBypassedThenRetried() throws Exception {
        UUID id = saveProduct("Paused Pretzels");
        DockerClient docker = DockerClientFactory.instance().client();

        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            mockMvc.perform(get("/api/v1/products/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Paused Pretzels"));
            // One read and one write-back, each bounded by spring.data.redis.timeout (500ms).
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
            assertThat(breaker.isBypassing()).isTrue();

            double errorsAfterFirst = cacheErrors();
            mockMvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isOk());
            // While bypassing, no cache call is made, so nothing more can fail.
            assertThat(cacheErrors()).isEqualTo(errorsAfterFirst);
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            productJson(id);
            assertThat(keys(CatalogCaches.PRODUCT)).hasSize(1);
        });
    }

    private double cacheErrors() {
        return meters.find("familyfood.cache.errors").counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
