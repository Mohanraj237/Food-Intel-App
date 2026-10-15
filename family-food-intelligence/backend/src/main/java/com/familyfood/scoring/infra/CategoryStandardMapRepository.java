package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.CategoryStandardMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryStandardMapRepository extends JpaRepository<CategoryStandardMap, CategoryStandardMap.Key> {

    List<CategoryStandardMap> findByCategoryCode(String categoryCode);
}
