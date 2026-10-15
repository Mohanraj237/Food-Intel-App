package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors the inline {@code currentVersion} shape on {@code ProductDetail} in
 * frontend/src/lib/catalog.ts. {@code novaGroup}/{@code novaSource} and
 * {@code dietFlag}/{@code dietFlagEstimated} are the V15 enrichment facts (D-059);
 * {@code novaSource} is the {@code product_source.source_type} the group came from, null
 * when unclassified.
 */
public record CurrentVersionDto(UUID id,
                                int versionNo,
                                Instant effectiveFrom,
                                BigDecimal servingSizeG,
                                String servingDeclaredText,
                                Boolean servingPlausible,
                                String dataQuality,
                                Instant capturedAt,
                                Instant verifiedAt,
                                String captureMethod,
                                Integer novaGroup,
                                String novaSource,
                                String dietFlag,
                                boolean dietFlagEstimated) {
}
