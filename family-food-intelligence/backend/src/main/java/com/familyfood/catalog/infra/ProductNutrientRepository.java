package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductNutrient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductNutrientRepository extends JpaRepository<ProductNutrient, UUID> {

    List<ProductNutrient> findByProductVersionId(UUID productVersionId);

    List<ProductNutrient> findByProductVersionIdIn(Collection<UUID> productVersionIds);
}
