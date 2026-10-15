package com.familyfood;

import com.familyfood.platform.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Family Food Intelligence — household packaged-food decision intelligence for India.
 *
 * <p>A modular monolith. Module boundaries are the packages directly under
 * {@code com.familyfood}; they are enforced by the ArchUnit suite rather than by a
 * POM graph (D-021). Two of those rules carry product weight and must never be
 * relaxed:
 *
 * <ul>
 *   <li>{@code scoring} must not depend on {@code ai} — this is what makes
 *       "AI never computes a verdict" a structural property rather than a promise (D-011).
 *   <li>{@code recommendation} must not depend on any commercial concept — no code path
 *       may exist by which a commercial relationship alters a ranking (D-012).
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class FamilyFoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyFoodApplication.class, args);
    }
}
