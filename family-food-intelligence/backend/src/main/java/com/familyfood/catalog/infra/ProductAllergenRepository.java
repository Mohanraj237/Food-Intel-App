package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductAllergen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAllergenRepository extends JpaRepository<ProductAllergen, UUID> {

    List<ProductAllergen> findByProductVersionId(UUID productVersionId);

    List<ProductAllergen> findByProductVersionIdIn(java.util.Collection<UUID> productVersionIds);
}
