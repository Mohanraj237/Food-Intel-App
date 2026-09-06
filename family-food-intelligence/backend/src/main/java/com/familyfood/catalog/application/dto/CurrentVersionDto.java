package com.familyfood.catalog.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors the inline {@code currentVersion} shape on {@code ProductDetail} in frontend/src/lib/catalog.ts. */
public record CurrentVersionDto(UUID id,
                                int versionNo,
                                Instant effectiveFrom,
                                BigDecimal servingSizeG,
                                String servingDeclaredText,
                                Boolean servingPlausible,
                                String dataQuality,
                                Instant capturedAt,
                                Instant verifiedAt,
                                String captureMethod) {
}
