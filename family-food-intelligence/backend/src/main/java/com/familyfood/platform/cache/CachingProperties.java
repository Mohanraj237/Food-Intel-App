package com.familyfood.platform.cache;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code familyfood.cache.*} (D-056). The Redis connection itself is Spring Boot's
 * {@code spring.data.redis.*}.
 *
 * @param type       where cached entries live — see {@link Type}.
 * @param keyPrefix  namespace in front of every Redis key, so one Redis can serve more than
 *                   this application.
 * @param retryAfter after a cache failure, how long every cache operation is skipped before
 *                   the cache is tried again (see {@link CacheCircuitBreaker}).
 */
@Validated
@ConfigurationProperties(prefix = "familyfood.cache")
public record CachingProperties(
        @NotNull Type type,
        @NotBlank String keyPrefix,
        @NotNull Duration retryAfter
) {

    public enum Type {
        /** In-process, per instance. The default, and correct while there is one instance (D-007). */
        CAFFEINE,
        /** Shared by every instance. Optional: the application keeps working without it. */
        REDIS,
        /** No caching — every read goes to the database. For ruling a cache in or out of a bug. */
        NONE
    }
}
