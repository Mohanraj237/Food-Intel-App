package com.familyfood.platform.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.cache.support.NoOpCache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the real caches — or, while {@link CacheCircuitBreaker} is tripped, stand-ins that
 * hold nothing. A stand-in's read is always a miss, so the cached method runs against the
 * database, and its writes and evictions go nowhere. No cache call is made, so a request
 * waits on no cache timeout.
 *
 * <p>An undeclared cache name still fails here, loudly, exactly as the plain resolver does.
 */
final class BypassingCacheResolver extends SimpleCacheResolver {

    private final CacheCircuitBreaker breaker;
    private final Map<String, Cache> standIns = new ConcurrentHashMap<>();

    BypassingCacheResolver(CacheManager cacheManager, CacheCircuitBreaker breaker) {
        super(cacheManager);
        this.breaker = breaker;
    }

    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        Collection<? extends Cache> caches = super.resolveCaches(context);
        if (!breaker.isBypassing()) {
            return caches;
        }
        return caches.stream()
                .map(cache -> standIns.computeIfAbsent(cache.getName(), NoOpCache::new))
                .toList();
    }
}
