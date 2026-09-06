package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, UUID> {

    List<ProductIngredient> findByProductVersionIdOrderByPositionAsc(UUID productVersionId);
}
