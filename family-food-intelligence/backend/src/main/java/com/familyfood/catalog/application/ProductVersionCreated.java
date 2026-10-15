package com.familyfood.catalog.application;

import java.util.UUID;

/**
 * Published (via {@code ApplicationEventPublisher}) whenever the facts of a product's current
 * version change: a new product is ingested, a superseding version is created, or an
 * enrichment pass fills previously-null facts into the current version. Consumers
 * ({@code scoring}) recompute derived data after the publishing transaction commits.
 * {@code reason} is a short label for logs: {@code INGESTED}, {@code OFF_ENRICHMENT}, …
 */
public record ProductVersionCreated(UUID productId, UUID productVersionId, String reason) {
}
