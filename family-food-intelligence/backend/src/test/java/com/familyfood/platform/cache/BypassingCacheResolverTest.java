package com.familyfood.platform.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.BasicOperation;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.cache.support.NoOpCache;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** While the breaker is tripped, cached methods run against stand-ins that hold nothing (D-056). */
class BypassingCacheResolverTest {

    private static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final TestClock clock = new TestClock(Instant.parse("2026-09-13T10:00:00Z"));
    private final ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("catalog.product");
    private final CacheCircuitBreaker breaker = new CacheCircuitBreaker(RETRY_AFTER, clock, new SimpleMeterRegistry());
    private final BypassingCacheResolver resolver = new BypassingCacheResolver(caches, breaker);

    private static CacheOperationInvocationContext<BasicOperation> invocationOf(String cacheName) {
        CacheableOperation.Builder builder = new CacheableOperation.Builder();
        builder.setName("getProduct");
        builder.setCacheName(cacheName);
        CacheableOperation operation = builder.build();
        return new CacheOperationInvocationContext<>() {
            @Override public BasicOperation getOperation() { return operation; }
            @Override public Object getTarget() { return new Object(); }
            @Override public Method getMethod() { return null; }
            @Override public Object[] getArgs() { return new Object[0]; }
        };
    }

    private List<Cache> resolve(String cacheName) {
        return List.copyOf(resolver.resolveCaches(invocationOf(cacheName)));
    }

    private void trip() {
        breaker.handleCacheGetError(new RedisConnectionFailureException("refused"),
                caches.getCache("catalog.product"), "key");
    }

    @Test
    @DisplayName("normally the real cache is resolved")
    void resolvesTheRealCache() {
        assertThat(resolve("catalog.product")).containsExactly(caches.getCache("catalog.product"));
    }

    @Test
    @DisplayName("while bypassing, a stand-in is resolved: reads miss and writes reach nothing")
    void resolvesAStandInWhileBypassing() {
        trip();

        Cache resolved = resolve("catalog.product").get(0);
        resolved.put("key", "value");

        assertThat(resolved).isInstanceOf(NoOpCache.class);
        assertThat(resolved.getName()).isEqualTo("catalog.product");
        assertThat(resolved.get("key")).isNull();
        assertThat(caches.getCache("catalog.product").get("key")).isNull();
    }

    @Test
    @DisplayName("once retry-after has passed, the real cache is resolved again")
    void resumesAfterRetryAfter() {
        trip();
        clock.advance(RETRY_AFTER);

        assertThat(resolve("catalog.product")).containsExactly(caches.getCache("catalog.product"));
    }

    @Test
    @DisplayName("an undeclared cache name fails loudly, bypassing or not")
    void undeclaredCacheFails() {
        assertThatThrownBy(() -> resolve("catalog.misspelt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalog.misspelt");

        trip();
        assertThatThrownBy(() -> resolve("catalog.misspelt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
