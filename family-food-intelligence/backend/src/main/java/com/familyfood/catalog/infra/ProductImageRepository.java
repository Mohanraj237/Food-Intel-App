package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    /**
     * The current hero image for a product, if one has been ingested — used by
     * {@code ProductQueryService} to populate {@code ProductSummaryDto.imageUrl} /
     * {@code ProductDetailDto.imageUrl}.
     */
    Optional<ProductImage> findByProductIdAndPrimaryTrue(UUID productId);

    /** Batch form of {@link #findByProductIdAndPrimaryTrue(UUID)} for search-result pages. */
    List<ProductImage> findByProductIdInAndPrimaryTrue(Collection<UUID> productIds);

    /**
     * Idempotency check: has this exact image content already been stored for this product?
     * Used by {@code ProductImageService} to skip a redundant upload+insert when an import
     * (e.g. the Open Food Facts image backfill) is resumed or re-run.
     */
    Optional<ProductImage> findByProductIdAndContentHash(UUID productId, String contentHash);

    /** Every image row for a product, in gallery order — originals and derivatives alike. */
    List<ProductImage> findByProductIdOrderBySortOrderAscCreatedAtAsc(UUID productId);
}
