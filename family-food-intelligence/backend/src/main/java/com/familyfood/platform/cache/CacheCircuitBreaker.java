package com.familyfood.platform.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps a failing cache from slowing the application down (D-056).
 *
 * <p>Every cache here is optional. As the {@link CacheErrorHandler}, this class turns a failed
 * read into a miss and a failed write into "not cached", so a request never fails because of
 * the cache. That keeps requests correct when Redis is down, but not fast: a Redis that has
 * stopped answering costs each read the command timeout, twice (the read, then the
 * write-back), and a request that reads twenty products — alternatives do — pays that twenty
 * times. So a failure also trips this breaker. For {@code familyfood.cache.retry-after}, every
 * cache operation is skipped ({@link BypassingCacheResolver}) and reads go straight to the
 * database. After that, the next request tries the cache again; if it fails, the breaker trips
 * for another period.
 *
 * <p>Evictions skipped or failed while tripped are not replayed. An entry one of them should
 * have removed is served until its TTL runs out — the same bound that already covers changes
 * made outside this process.
 *
 * <p>An entry that is present but unreadable is not an outage. It is discarded, and the
 * breaker stays closed, so one bad entry cannot switch caching off indefinitely.
 */
final class CacheCircuitBreaker implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CacheCircuitBreaker.class);

    private final Duration retryAfter;
    private final Clock clock;
    private final MeterRegistry meters;
    private final AtomicReference<Instant> bypassUntil = new AtomicReference<>(Instant.MIN);

    CacheCircuitBreaker(Duration retryAfter, Clock clock, MeterRegistry meters) {
        this.retryAfter = retryAfter;
        this.clock = clock;
        this.meters = meters;
        Gauge.builder("familyfood.cache.bypassed", this, breaker -> breaker.isBypassing() ? 1 : 0)
                .description("1 while cache operations are skipped after a cache failure")
                .register(meters);
    }

    boolean isBypassing() {
        return clock.instant().isBefore(bypassUntil.get());
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        if (exception instanceof SerializationException) {
            discardUnreadable(cache, key, exception);
            return;
        }
        trip(cache, "get", exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        trip(cache, "put", exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        trip(cache, "evict", exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        trip(cache, "clear", exception);
    }

    private void trip(Cache cache, String operation, RuntimeException exception) {
        count(cache, operation);
        Instant now = clock.instant();
        Instant previous = bypassUntil.getAndSet(now.plus(retryAfter));
        if (!now.isBefore(previous)) {
            // Logged once per trip, not once per request: while tripped nothing reaches the
            // cache, so the next line appears at most once per retry-after.
            log.warn("cache_unavailable cache={} operation={} bypassFor={} error={}",
                    cache.getName(), operation, retryAfter, describe(exception));
            log.debug("cache_unavailable_detail cache={}", cache.getName(), exception);
        }
    }

    private void discardUnreadable(Cache cache, Object key, RuntimeException exception) {
        count(cache, "get");
        log.warn("cache_entry_unreadable cache={} error={}", cache.getName(), describe(exception));
        try {
            cache.evict(key);
        } catch (RuntimeException evictFailure) {
            trip(cache, "evict", evictFailure);
        }
    }

    private void count(Cache cache, String operation) {
        Counter.builder("familyfood.cache.errors")
                .description("Cache operations that failed and fell back to the database")
                .tag("cache", cache.getName())
                .tag("operation", operation)
                .register(meters)
                .increment();
    }

    /** Exception type and message only — never the key or value, which stay out of logs. */
    private static String describe(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == exception
                ? exception.getClass().getSimpleName() + ": " + exception.getMessage()
                : exception.getClass().getSimpleName() + " <- " + root.getClass().getSimpleName()
                        + ": " + root.getMessage();
    }
}
