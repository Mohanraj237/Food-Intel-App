package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.Nutrient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutrientRepository extends JpaRepository<Nutrient, String> {

    java.util.List<Nutrient> findByCoreTrue();
}
