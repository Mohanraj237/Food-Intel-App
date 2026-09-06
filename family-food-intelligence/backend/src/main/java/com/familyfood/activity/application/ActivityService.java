package com.familyfood.activity.application;

import com.familyfood.activity.domain.ActivityEvent;
import com.familyfood.activity.infra.ActivityEventRepository;
import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.dto.ProductSummaryDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records and reads back one signed-in account's own activity (FR-012, D-005). Deliberately
 * small (system-architecture.md §3: "activity... lookups, verdict records, outcome capture,
 * recent activity") — no analytics, no dashboards, no aggregation beyond "what did I
 * recently do."
 */
@Service
public class ActivityService {

    private static final int MAX_RECENT = 20;

    private final ActivityEventRepository events;
    private final ProductQueryService catalog;
    private final Clock clock;

    public ActivityService(ActivityEventRepository events, ProductQueryService catalog, Clock clock) {
        this.events = events;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * Validates the product (and alternative, if given) actually resolve in the catalogue
     * before recording — an activity event about a product id that does not exist would be
     * useless and, worse, would render as a broken link in "recent activity" forever.
     */
    @Transactional
    public UUID record(UUID accountId, RecordActivityCommand command) {
        catalog.getProduct(command.productId());
        if (command.alternativeProductId() != null) {
            catalog.getProduct(command.alternativeProductId());
        }

        Instant now = clock.instant();
        ActivityEvent saved = events.save(ActivityEvent.record(
                accountId, command.productId(), command.alternativeProductId(), command.eventType(), now));
        return saved.getId();
    }

    /** The caller's own recent activity only — never another account's. */
    @Transactional(readOnly = true)
    public List<RecentActivityEntry> recentForAccount(UUID accountId) {
        List<ActivityEvent> recent = events.findByAccountIdOrderByOccurredAtDesc(
                accountId, PageRequest.of(0, MAX_RECENT));

        List<UUID> productIds = recent.stream()
                .flatMap(e -> e.getAlternativeProductId() == null
                        ? java.util.stream.Stream.of(e.getProductId())
                        : java.util.stream.Stream.of(e.getProductId(), e.getAlternativeProductId()))
                .distinct()
                .toList();

        Map<UUID, ProductSummaryDto> summariesById = catalog.getSummaries(productIds).stream()
                .collect(Collectors.toMap(ProductSummaryDto::id, Function.identity()));

        return recent.stream()
                .map(e -> new RecentActivityEntry(
                        e.getId(),
                        e.getEventType(),
                        summariesById.get(e.getProductId()),
                        e.getAlternativeProductId() == null ? null : summariesById.get(e.getAlternativeProductId()),
                        e.getOccurredAt()))
                .toList();
    }
}
