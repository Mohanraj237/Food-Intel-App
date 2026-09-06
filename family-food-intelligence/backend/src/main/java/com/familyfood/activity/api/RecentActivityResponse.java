package com.familyfood.activity.api;

import com.familyfood.activity.application.RecentActivityEntry;
import com.familyfood.catalog.application.dto.ProductSummaryDto;

import java.time.Instant;

/** Mirrors {@link RecentActivityEntry}. */
public record RecentActivityResponse(
        String id,
        String eventType,
        ProductSummaryDto product,
        ProductSummaryDto alternativeProduct,
        Instant occurredAt) {

    static RecentActivityResponse from(RecentActivityEntry entry) {
        return new RecentActivityResponse(
                entry.id().toString(),
                entry.eventType().name(),
                entry.product(),
                entry.alternativeProduct(),
                entry.occurredAt());
    }
}
