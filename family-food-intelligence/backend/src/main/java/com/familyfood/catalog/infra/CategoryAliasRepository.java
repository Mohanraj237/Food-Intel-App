package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.CategoryAlias;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryAliasRepository extends JpaRepository<CategoryAlias, String> {
}
