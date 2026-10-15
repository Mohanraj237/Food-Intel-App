package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.BrandAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrandAliasRepository extends JpaRepository<BrandAlias, UUID> {

    List<BrandAlias> findByBrandId(UUID brandId);

    boolean existsByBrandIdAndNormalizedAlias(UUID brandId, String normalizedAlias);
}
