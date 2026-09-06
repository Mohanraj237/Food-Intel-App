package com.familyfood.activity.application;

import com.familyfood.activity.domain.ActivityEventType;
import com.familyfood.catalog.application.dto.ProductSummaryDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One recent-activity row, enriched with the real product summary the frontend needs to
 * render it (Home's "recent activity" section) — {@code product}/{@code alternativeProduct}
 * are {@code null} only if the underlying product was subsequently removed from the
 * catalogue (merged/rejected), never fabricated as a placeholder.
 */
public record RecentActivityEntry(
        UUID id,
        ActivityEventType eventType,
        ProductSummaryDto product,
        ProductSummaryDto alternativeProduct,
        Instant occurredAt) {
}
