package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.Product;
import com.familyfood.catalog.domain.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Candidate alternatives for `recommendation` (D-013): same category, excluding self. */
    List<Product> findByCategoryIdAndStatusAndIdNot(UUID categoryId, ProductStatus status,
                                                      UUID excludeProductId, Pageable pageable);

    /** Stable id paging for bulk jobs (rescore): by id, so a page never shifts under the job. */
    @Query("select p.id from Product p where p.status = :status order by p.id")
    List<UUID> findIdsByStatus(ProductStatus status, Pageable pageable);

    long countByStatus(ProductStatus status);
}
