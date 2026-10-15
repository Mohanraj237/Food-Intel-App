package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductChangeLogRepository extends JpaRepository<ProductChangeLog, UUID> {

    List<ProductChangeLog> findByProductIdOrderByDetectedAtDesc(UUID productId);
}
