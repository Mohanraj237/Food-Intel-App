package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductSourceRepository extends JpaRepository<ProductSource, UUID> {

    List<ProductSource> findByProductVersionId(UUID productVersionId);
}
