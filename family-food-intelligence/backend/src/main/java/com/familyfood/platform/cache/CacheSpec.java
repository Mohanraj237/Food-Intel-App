package com.familyfood.platform.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One cache, declared by the module that owns the data in it (D-056).
 *
 * <p>The owning module publishes a {@code CacheSpec} bean and {@link CacheConfig} builds the
 * cache from it, whichever provider is active. Name, lifetime and value type live together
 * in the owning module so that platform never names a domain type (ModuleBoundaryTest), and
 * so that the value type is explicit — which is what lets Redis hold plain typed JSON rather
 * than JSON that names its own class for the reader to instantiate.
 *
 * @param name       {@code <module>.<what>} in lower case, e.g. {@code catalog.product}. Also
 *                   the middle of every Redis key (see {@link CacheKeys}).
 * @param ttl        how long an entry may be served after it was written. This is the bound on
 *                   staleness for any change the owning module cannot evict itself: a separate
 *                   seed process, a SQL correction, an eviction lost while Redis was down.
 * @param maxEntries size bound for the in-process provider. Redis is bounded by its own
 *                   {@code maxmemory} policy.
 * @param valueType  exactly what the cached method returns.
 */
public record CacheSpec(String name, Duration ttl, long maxEntries, TypeReference<?> valueType) {

    private static final Pattern NAME = Pattern.compile("[a-z]+(\\.[a-z]+(-[a-z]+)*)+");

    public CacheSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(valueType, "valueType");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("cache name must be <module>.<what> in lower case: " + name);
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("every cache entry must expire: " + name);
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive: " + name);
        }
    }
}
