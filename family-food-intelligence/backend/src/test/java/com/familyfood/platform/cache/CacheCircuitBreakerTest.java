package com.familyfood.platform.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** A failing cache is skipped for a while and then retried (D-056). */
class CacheCircuitBreakerTest {

    private static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final TestClock clock = new TestClock(Instant.parse("2026-09-13T10:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CacheCircuitBreaker breaker = new CacheCircuitBreaker(RETRY_AFTER, clock, meters);
    private final Cache cache = new ConcurrentMapCache("catalog.product");

    private static RuntimeException outage() {
        return new RedisConnectionFailureException("Unable to connect to Redis");
    }

    private double errors(String operation) {
        var counter = meters.find("familyfood.cache.errors")
                .tags("cache", "catalog.product", "operation", operation).counter();
        return counter == null ? 0 : counter.count();
    }

    private double bypassedGauge() {
        return meters.get("familyfood.cache.bypassed").gauge().value();
    }

    @Test
    @DisplayName("the cache is used until something fails")
    void closedByDefault() {
        assertThat(breaker.isBypassing()).isFalse();
        assertThat(bypassedGauge()).isZero();
    }

    @Test
    @DisplayName("a failure skips the cache for retry-after, then lets the next call try it again")
    void failureBypassesThenRetries() {
        breaker.handleCacheGetError(outage(), cache, "key");

        assertThat(breaker.isBypassing()).isTrue();
        assertThat(bypassedGauge()).isEqualTo(1);

        clock.advance(RETRY_AFTER.minusSeconds(1));
        assertThat(breaker.isBypassing()).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.isBypassing()).isFalse();
        assertThat(bypassedGauge()).isZero();
    }

    @Test
    @DisplayName("a failed write, eviction or clear trips it just as a failed read does")
    void everyOperationTrips() {
        breaker.handleCachePutError(outage(), cache, "key", "value");
        assertThat(breaker.isBypassing()).isTrue();

        clock.advance(RETRY_AFTER);
        breaker.handleCacheEvictError(outage(), cache, "key");
        assertThat(breaker.isBypassing()).isTrue();

        clock.advance(RETRY_AFTER);
        breaker.handleCacheClearError(outage(), cache);
        assertThat(breaker.isBypassing()).isTrue();

        assertThat(errors("put")).isEqualTo(1);
        assertThat(errors("evict")).isEqualTo(1);
        assertThat(errors("clear")).isEqualTo(1);
    }

    @Test
    @DisplayName("a retry that fails again starts a fresh window from that failure")
    void failedRetryStartsANewWindow() {
        breaker.handleCacheGetError(outage(), cache, "key");
        clock.advance(RETRY_AFTER);
        breaker.handleCacheGetError(outage(), cache, "key");

        clock.advance(RETRY_AFTER.minusSeconds(1));
        assertThat(breaker.isBypassing()).isTrue();
    }

    @Test
    @DisplayName("an unreadable entry is discarded and counted, and the cache stays in use")
    void unreadableEntryIsDiscardedWithoutBypassing() {
        cache.put("key", "written in an old shape");

        breaker.handleCacheGetError(new SerializationException("Could not read JSON"), cache, "key");

        assertThat(breaker.isBypassing()).isFalse();
        assertThat(cache.get("key")).isNull();
        assertThat(errors("get")).isEqualTo(1);
    }

    @Test
    @DisplayName("64 threads failing at once leave one consistent window and 64 counted errors")
    void concurrentFailuresAreConsistent() throws Exception {
        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                done.add(pool.submit(() -> {
                    start.await();
                    breaker.handleCachePutError(outage(), cache, "key", "value");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : done) {
                f.get();
            }
        }

        assertThat(errors("put")).isEqualTo(threads);
        assertThat(breaker.isBypassing()).isTrue();
        clock.advance(RETRY_AFTER);
        assertThat(breaker.isBypassing()).isFalse();
    }
}
