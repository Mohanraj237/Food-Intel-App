package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Candidate alternatives for `recommendation` (D-013): same category, excluding self. */
    List<Product> findByCategoryIdAndStatusAndIdNot(UUID categoryId, ProductStatus status,
                                                      UUID excludeProductId, Pageable pageable);
}
