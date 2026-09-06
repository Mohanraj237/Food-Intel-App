package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductConflict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductConflictRepository extends JpaRepository<ProductConflict, UUID> {

    boolean existsByProductVersionIdAndStatus(UUID productVersionId, ProductConflict.Status status);
}
