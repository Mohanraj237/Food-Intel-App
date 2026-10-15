package com.familyfood.platform.cache;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * The shared cache's health, reported as {@code cache} in {@code /actuator/health} (D-056).
 *
 * <p>This replaces Spring Boot's own Redis indicator, which reports DOWN when Redis is
 * unreachable and so turns the whole of {@code /actuator/health} into a 503. Here Redis is
 * optional: the application keeps serving from the database. An unreachable Redis is
 * therefore {@link #DEGRADED}, which application.yml orders between OUT_OF_SERVICE and UP and
 * which maps to HTTP 200. Liveness and readiness never include this indicator.
 */
final class RedisCacheHealthIndicator implements HealthIndicator {

    static final Status DEGRADED = new Status("DEGRADED");

    private final RedisConnectionFactory connections;
    private final CacheCircuitBreaker breaker;

    RedisCacheHealthIndicator(RedisConnectionFactory connections, CacheCircuitBreaker breaker) {
        this.connections = connections;
        this.breaker = breaker;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connections.getConnection()) {
            connection.ping();
            return Health.up().withDetail("bypassed", breaker.isBypassing()).build();
        } catch (RuntimeException e) {
            return Health.status(DEGRADED)
                    .withDetail("bypassed", breaker.isBypassing())
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
