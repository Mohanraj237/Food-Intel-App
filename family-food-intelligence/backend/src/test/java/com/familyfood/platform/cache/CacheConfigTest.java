package com.familyfood.platform.cache;

import com.familyfood.catalog.application.dto.AllergenRowDto;
import com.familyfood.catalog.application.dto.BarcodeRowDto;
import com.familyfood.catalog.application.dto.CategoryDto;
import com.familyfood.catalog.application.dto.CurrentVersionDto;
import com.familyfood.catalog.application.dto.IngredientRowDto;
import com.familyfood.catalog.application.dto.KeyFactDto;
import com.familyfood.catalog.application.dto.NutrientRowDto;
import com.familyfood.catalog.application.dto.PackSizeDto;
import com.familyfood.catalog.application.dto.ProductDetailDto;
import com.familyfood.catalog.application.dto.SourceRefDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How each provider builds the declared caches (D-056). */
class CacheConfigTest {

    private static final CacheSpec PRODUCT = new CacheSpec("catalog.product", Duration.ofMinutes(30), 2_000,
            new TypeReference<ProductDetailDto>() {});
    private static final CacheSpec CATEGORIES = new CacheSpec("catalog.categories", Duration.ofHours(1), 1,
            new TypeReference<List<CategoryDto>>() {});

    /** Configured like the application's mapper: see spring.jackson in application.yml. */
    private final ObjectMapper apiMapper = Jackson2ObjectMapperBuilder.json()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    // ------------------------------------------------------------------ caffeine

    @Test
    @DisplayName("caffeine: exactly the declared caches, each with its own TTL, size bound and statistics")
    void caffeineBuildsTheDeclaredCaches() {
        CacheManager manager = CacheConfig.caffeineCacheManager(List.of(PRODUCT, CATEGORIES));

        assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("catalog.product", "catalog.categories");
        assertThat(manager.getCache("catalog.misspelt")).isNull();

        var nativeCache = ((CaffeineCache) manager.getCache("catalog.product")).getNativeCache();
        Policy<Object, Object> policy = nativeCache.policy();
        assertThat(policy.expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(2_000);
        assertThat(policy.isRecordingStats()).isTrue();
    }

    @Test
    @DisplayName("caffeine: null is refused rather than cached")
    void caffeineRefusesNull() {
        CacheManager manager = CacheConfig.caffeineCacheManager(List.of(PRODUCT));

        assertThatThrownBy(() -> manager.getCache("catalog.product").put(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --------------------------------------------------------------------- redis

    @Test
    @DisplayName("redis: keys follow ffi:<cache>:<version>::<key>, entries carry the spec's TTL, nulls are not cached")
    void redisConfigurationFollowsTheConvention() {
        RedisCacheConfiguration config = redisConfig(PRODUCT, "14");

        assertThat(config.getKeyPrefixFor("catalog.product")).matches("ffi:catalog\\.product:[0-9a-f]{8}::");
        assertThat(config.getTtlFunction().getTimeToLive("key", "value")).isEqualTo(Duration.ofMinutes(30));
        assertThat(config.getAllowCacheNullValues()).isFalse();
    }

    @Test
    @DisplayName("redis: two caches never share a keyspace")
    void redisCachesHaveDistinctPrefixes() {
        String product = redisConfig(PRODUCT, "14").getKeyPrefixFor("x");
        String categories = redisConfig(CATEGORIES, "14").getKeyPrefixFor("x");

        assertThat(product).isNotEqualTo(categories);
    }

    @Test
    @DisplayName("redis: after a migration, entries read before it are in another keyspace and never served")
    void aMigrationMovesTheKeyspace() {
        String before = redisConfig(PRODUCT, "14").getKeyPrefixFor("x");
        String after = redisConfig(PRODUCT, "15").getKeyPrefixFor("x");

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("redis: the schema version is read once, on first use, not while the bean is built")
    void schemaVersionIsReadLazilyAndOnce() {
        AtomicInteger reads = new AtomicInteger();
        RedisCacheConfiguration config = CacheConfig.redisCacheConfiguration(PRODUCT, "ffi:", apiMapper, () -> {
            reads.incrementAndGet();
            return "14";
        });
        assertThat(reads.get()).isZero();

        config.getKeyPrefixFor("catalog.product");
        config.getKeyPrefixFor("catalog.product");

        assertThat(reads.get()).isEqualTo(1);
    }

    private RedisCacheConfiguration redisConfig(CacheSpec spec, String schemaVersion) {
        return CacheConfig.redisCacheConfiguration(spec, "ffi:", apiMapper, () -> schemaVersion);
    }

    @Test
    @DisplayName("redis: a fully populated product detail comes back exactly as it went in")
    void productDetailRoundTrips() {
        ProductDetailDto detail = fullProductDetail();

        Object read = roundTrip(PRODUCT, detail);

        assertThat(read).isEqualTo(detail);
    }

    @Test
    @DisplayName("redis: a category list with the nulls the API omits comes back exactly as it went in")
    void categoriesRoundTrip() {
        List<CategoryDto> categories = List.of(
                new CategoryDto("BISCUITS", "Biscuits and cookies", "SNACKS/BISCUITS", 10,
                        new BigDecimal("20.0"), new BigDecimal("40.0")),
                new CategoryDto("OTHER", "Other", "OTHER", 999, null, null));

        assertThat(roundTrip(CATEGORIES, categories)).isEqualTo(categories);
    }

    @Test
    @DisplayName("redis: entries are plain typed JSON that names no class")
    void entriesNameNoClass() {
        SerializationPair<Object> pair = redisConfig(PRODUCT, "14").getValueSerializationPair();

        String json = StandardCharsets.UTF_8.decode(pair.write(fullProductDetail())).toString();

        assertThat(json).startsWith("{").doesNotContain("@class").doesNotContain("com.familyfood");
    }

    private Object roundTrip(CacheSpec spec, Object value) {
        SerializationPair<Object> pair = redisConfig(spec, "14").getValueSerializationPair();
        ByteBuffer written = pair.write(value);
        return pair.read(written);
    }

    private static ProductDetailDto fullProductDetail() {
        UUID productId = UUID.fromString("0b6e5f1e-8a2c-4d3b-9f10-1c2d3e4f5a6b");
        Instant captured = Instant.parse("2026-09-01T08:15:30.123456Z");
        List<KeyFactDto> keyFacts = List.of(
                new KeyFactDto("ENERGY_KCAL", "Energy", new BigDecimal("150"), new BigDecimal("500"), "kcal"),
                new KeyFactDto("SODIUM_MG", "Sodium", null, new BigDecimal("300.50"), "mg"));
        CurrentVersionDto version = new CurrentVersionDto(UUID.randomUUID(), 3, captured,
                new BigDecimal("30.000"), "1 pack (30g)", true, "VERIFIED", captured, null, "MANUAL_ENTRY",
                4, "OPEN_FOOD_FACTS", "VEG", true);
        List<NutrientRowDto> nutrients = List.of(
                new NutrientRowDto("ENERGY_KCAL", "Energy", new BigDecimal("500"), new BigDecimal("150"), "kcal",
                        "PER_100G", false, new BigDecimal("0.95"),
                        new SourceRefDto("MANUAL_ENTRY", "label photo", captured)),
                new NutrientRowDto("SODIUM_MG", "Sodium", new BigDecimal("300.50"), null, "mg",
                        null, true, null, null));
        List<IngredientRowDto> ingredients = List.of(
                new IngredientRowDto("Refined wheat flour", 1, "Refined wheat flour (maida)", false, null, null, null, null),
                new IngredientRowDto("Emulsifier (322)", 2, null, true, "322", "322", "Lecithins", "Emulsifier / antioxidant"));
        List<AllergenRowDto> allergens = List.of(new AllergenRowDto("WHEAT", "Wheat", "CONTAINS"));
        List<BarcodeRowDto> barcodes = List.of(new BarcodeRowDto("8901234500017", "EAN13", true));

        return new ProductDetailDto(productId, "Britannia", "Good Day Cashew",
                new PackSizeDto(new BigDecimal("75.00"), "g"), "BISCUITS", "Biscuits and cookies",
                "http://localhost:9000/familyfood-catalogue-images/x.jpg", "VERIFIED",
                new BigDecimal("30.000"), "1 pack (30g)", keyFacts, version, nutrients, ingredients,
                allergens, barcodes, true);
    }
}
