package com.familyfood.platform.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.ClientOptions;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.util.function.SingletonSupplier;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Caching (D-056, superseding D-007's in-process-only rule).
 *
 * <p><b>Cache-aside, declared.</b> A module marks a read with {@code @Cacheable} and a write
 * that changes it with {@code @CacheEvict}. A read checks the cache, falls back to the
 * database on a miss and stores what it read. Nothing is written to the cache ahead of the
 * database, and nothing reads the cache except through those annotations
 * (CachingBoundaryTest).
 *
 * <p><b>One provider at a time</b>, chosen by {@code familyfood.cache.type}. Caffeine is the
 * default and keeps each instance's cache in its own heap. Redis shares one cache between
 * instances, which is what D-007 named as its reversal condition. With Redis there is
 * deliberately no in-process tier in front: an entry evicted on one instance would live on in
 * another's heap.
 *
 * <p><b>Redis is optional at runtime.</b> Startup never connects to it. A failed cache
 * operation falls back to the database, and after one failure the cache is skipped for a
 * while ({@link CacheCircuitBreaker}). Lettuce rejects commands while it is disconnected
 * rather than queueing them behind a reconnect. Health reports DEGRADED, never DOWN
 * ({@link RedisCacheHealthIndicator}).
 *
 * <p><b>What may be cached:</b> public, non-personal data only — today, catalogue product
 * facts and categories. Never a person's profile, verdict, consent state or health data:
 * those are read fresh, so a consent withdrawal takes effect immediately (D-018), and they
 * never leave the database for a second store.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = CacheConfig.CACHE_ADVICE_ORDER)
@EnableConfigurationProperties(CachingProperties.class)
public class CacheConfig implements CachingConfigurer {

    /**
     * Cache advice runs outside transaction advice, which sits at {@code LOWEST_PRECEDENCE}.
     * A cache hit therefore returns before a transaction starts or a pooled connection is
     * borrowed, and an eviction runs after its write has committed, so a concurrent reader
     * cannot put the pre-commit row back. Method security has much higher precedence, so it
     * still decides before any cached value is returned.
     */
    static final int CACHE_ADVICE_ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    /** Redis clears a cache with SCAN in pages of this size. KEYS would block the whole server. */
    private static final int CLEAR_BATCH_SIZE = 500;

    private final ObjectProvider<CacheManager> cacheManager;
    private final ObjectProvider<CacheCircuitBreaker> breaker;

    public CacheConfig(ObjectProvider<CacheManager> cacheManager,
                       ObjectProvider<CacheCircuitBreaker> breaker) {
        // Providers, not instances: the caching infrastructure asks for these lazily, and
        // resolving them in the constructor would initialise their dependencies too early.
        this.cacheManager = cacheManager;
        this.breaker = breaker;
    }

    @Override
    public CacheResolver cacheResolver() {
        return new BypassingCacheResolver(cacheManager.getObject(), breaker.getObject());
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return breaker.getObject();
    }

    @Bean
    CacheCircuitBreaker cacheCircuitBreaker(CachingProperties properties, Clock clock,
                                            ObjectProvider<MeterRegistry> meters) {
        return new CacheCircuitBreaker(properties.retryAfter(), clock,
                meters.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    public CacheManager cacheManager(CachingProperties properties,
                                     ObjectProvider<CacheSpec> specs,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<RedisConnectionFactory> redis,
                                     ObjectProvider<Flyway> flyway) {
        List<CacheSpec> declared = specs.orderedStream().toList();
        return switch (properties.type()) {
            case CAFFEINE -> caffeineCacheManager(declared);
            case REDIS -> redisCacheManager(declared, properties.keyPrefix(), objectMapper,
                    redis.getObject(), appliedSchemaVersion(flyway));
            case NONE -> new NoOpCacheManager();
        };
    }

    /**
     * The latest applied migration, part of every Redis key's version segment (see
     * {@link CacheKeys}). It is read once, at the first cache operation, by which time startup
     * has run the migrations. Reading it while building this bean could come before them.
     */
    static Supplier<String> appliedSchemaVersion(ObjectProvider<Flyway> flyway) {
        return SingletonSupplier.of(() -> {
            Flyway migrations = flyway.getIfAvailable();
            MigrationInfo current = migrations == null ? null : migrations.info().current();
            return current == null || current.getVersion() == null
                    ? "none"
                    : current.getVersion().getVersion();
        });
    }

    static CacheManager caffeineCacheManager(List<CacheSpec> specs) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        // Static: only declared caches exist, so a misspelt cache name fails at the first call
        // instead of quietly creating an unbounded cache nobody configured.
        manager.setCacheNames(List.of());
        for (CacheSpec spec : specs) {
            manager.registerCustomCache(spec.name(), Caffeine.newBuilder()
                    .maximumSize(spec.maxEntries())
                    .expireAfterWrite(spec.ttl())
                    .recordStats()
                    .build());
        }
        return manager;
    }

    static RedisCacheManager redisCacheManager(List<CacheSpec> specs, String keyPrefix,
                                               ObjectMapper objectMapper,
                                               RedisConnectionFactory connections,
                                               Supplier<String> schemaVersion) {
        Map<String, RedisCacheConfiguration> configurations = new LinkedHashMap<>();
        for (CacheSpec spec : specs) {
            configurations.put(spec.name(),
                    redisCacheConfiguration(spec, keyPrefix, objectMapper, schemaVersion));
        }
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(
                connections, BatchStrategies.scan(CLEAR_BATCH_SIZE));
        return RedisCacheManager.builder(writer)
                .withInitialCacheConfigurations(configurations)
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
    }

    /**
     * Typed JSON for exactly the spec's value type. No default typing: the stored JSON never
     * names a class for the reader to instantiate, so an entry written into Redis by anyone
     * else can at worst fail to parse.
     */
    static RedisCacheConfiguration redisCacheConfiguration(CacheSpec spec, String keyPrefix,
                                                           ObjectMapper objectMapper,
                                                           Supplier<String> schemaVersion) {
        // A copy of the application's mapper, so dates and numbers serialise as the API does.
        // It is a tolerant reader: an unexpected property in a cache entry is not worth failing
        // a request over.
        ObjectMapper mapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JavaType type = mapper.getTypeFactory().constructType(spec.valueType());
        Supplier<String> prefix = SingletonSupplier.of(() -> CacheKeys.prefix(
                keyPrefix, spec.name(), CacheKeys.versionOf(type, schemaVersion.get())));
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(spec.ttl())
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> prefix.get())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    /** Beans that exist only when Redis is the provider. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "familyfood.cache.type", havingValue = "redis")
    static class RedisProvider {

        /**
         * Lettuce's default queues commands while it reconnects, so each one waits out the
         * full command timeout. Rejected commands fail at once, and the fallback to the
         * database starts immediately.
         */
        @Bean
        LettuceClientOptionsBuilderCustomizer rejectCommandsWhileDisconnected() {
            return options -> options.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
        }

        @Bean
        HealthIndicator cacheHealthIndicator(RedisConnectionFactory connections,
                                             CacheCircuitBreaker breaker) {
            return new RedisCacheHealthIndicator(connections, breaker);
        }
    }
}
