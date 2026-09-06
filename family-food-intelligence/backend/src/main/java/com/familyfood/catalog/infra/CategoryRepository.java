package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByCode(String code);

    List<Category> findAllByOrderBySortOrderAsc();
}
