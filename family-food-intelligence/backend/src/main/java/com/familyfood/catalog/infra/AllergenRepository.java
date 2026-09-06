package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.Allergen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergenRepository extends JpaRepository<Allergen, String> {
}
