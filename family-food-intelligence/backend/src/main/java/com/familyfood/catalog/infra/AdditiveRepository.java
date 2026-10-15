package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.Additive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdditiveRepository extends JpaRepository<Additive, String> {
}
