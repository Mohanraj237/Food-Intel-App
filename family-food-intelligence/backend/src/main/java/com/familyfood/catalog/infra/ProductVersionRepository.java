package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {

    Optional<ProductVersion> findByProductIdAndCurrentTrue(UUID productId);

    List<ProductVersion> findByProductIdInAndCurrentTrue(Collection<UUID> productIds);
}
