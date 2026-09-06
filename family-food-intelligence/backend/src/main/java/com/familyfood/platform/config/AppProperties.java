package com.familyfood.platform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * All application configuration, bound from the environment. No secret has a default
 * (Constitution §60, D-018) — a missing secret must fail startup loudly rather than
 * silently falling back to something guessable.
 */
@Validated
@ConfigurationProperties(prefix = "familyfood")
public record AppProperties(
        @NotNull Auth auth,
        @NotNull Storage storage,
        @NotNull Ai ai,
        @NotNull Limits limits,
        @NotNull Privacy privacy
) {

    public record Auth(
            /* Base64-encoded HMAC key, >= 32 bytes decoded. Supplied per environment. */
            @NotBlank String jwtSecret,
            @NotBlank String jwtIssuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            /* Argon2id parameters — see docs/security/security-architecture.md */
            @Min(1) int argon2SaltLength,
            @Min(16) int argon2HashLength,
            @Min(1) int argon2Parallelism,
            @Min(19456) int argon2MemoryKb,
            @Min(2) int argon2Iterations
    ) {}

    public record Storage(
            @NotBlank String endpoint,
            @NotBlank String region,
            @NotBlank String bucket,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess,
            @NotNull Duration signedUrlTtl
    ) {}

    /**
     * AI provider settings. {@code enabled=false} is a fully supported production state:
     * OCR degrades to manual entry and explanations degrade to deterministic templates
     * (D-011, Constitution §75). It is never an error surfaced to a user.
     */
    public record Ai(
            boolean enabled,
            String provider,
            String apiKey,
            String ocrModel,
            String explanationModel,
            @NotNull Duration timeout,
            @Min(0) int maxRetries
    ) {}

    public record Limits(
            @Min(1) int loginAttemptsPerHour,
            @Min(1) int signupsPerHourPerIp,
            @Min(1) int searchesPerMinute,
            @Min(1) int uploadsPerHour,
            @Min(1) int aiCallsPerHour,
            @Min(1024) long maxImageBytes,
            @NotNull List<String> allowedImageContentTypes
    ) {}

    public record Privacy(
            @NotNull Duration labelImageRetention,
            @NotNull Duration ocrRawTextRetention,
            @NotNull Duration loginAttemptRetention,
            @NotNull Duration erasureGracePeriod,
            /* Salt for one-way hashing of search text and emails in throttling tables. */
            @NotBlank String hashPepper
    ) {}
}
