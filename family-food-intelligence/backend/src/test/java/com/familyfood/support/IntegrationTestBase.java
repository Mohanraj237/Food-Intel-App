package com.familyfood.support;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need a real database.
 *
 * <p>A real PostgreSQL rather than H2, for three reasons that all bite this schema
 * specifically: we rely on {@code pg_trgm} and {@code tsvector} for search, on partial
 * unique indexes for invariants such as "one current version per product", and on triggers
 * for audit-log immutability. None of those survive an in-memory substitute, so a green
 * H2 suite would prove nothing about production.
 *
 * <p>The container is static and reused across every subclass — starting one per class
 * would dominate the suite runtime.
 *
 * <p>Requires a running Docker daemon. Tests named {@code *IT} are bound to the failsafe
 * plugin so a developer without Docker can still run {@code mvn test} and get the full
 * unit suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("familyfood_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Flyway runs against the container; Hibernate only validates the result, so a
        // drift between the entity model and the migrations fails the suite loudly.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
