package com.familyfood.platform.config;

import com.familyfood.platform.logging.SensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Cross-cutting platform beans.
 */
@Configuration
public class PlatformConfig {

    /**
     * An injected {@link Clock} rather than {@code Instant.now()} calls scattered through
     * the domain. Scoring reproducibility (AC-6.1) and consent-expiry logic both need time
     * to be controllable in tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SensitiveDataMasker sensitiveDataMasker(AppProperties properties) {
        return new SensitiveDataMasker(properties.privacy().hashPepper());
    }

    /**
     * In-process caching only (D-007). Redis is not justified at MVP scale and would add
     * a component to secure, back up and monitor for no measured benefit.
     *
     * <p>Caches hold catalogue and rule data — never a person's profile, verdict or
     * consent state. Those must be read fresh so that a withdrawal takes effect
     * immediately (D-018: consent is checked at processing time).
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "products", "productVersions", "categories", "referenceStandards",
                "activeRuleSet", "brandAliases");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats());
        return manager;
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Family Food Intelligence API")
                .version("v1")
                .description("""
                        Household packaged-food decision intelligence for India.

                        Product facts are versioned and carry per-field provenance. Every
                        verdict is produced by a deterministic rules engine and stores the
                        product version, profile version and rule-set version that produced
                        it, so it can be reconstructed. No model output can become a score,
                        a threshold or a verdict.
                        """)
                .license(new License().name("Proprietary")));
    }
}
