package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.ProductScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductScoreRepository extends JpaRepository<ProductScore, UUID> {

    Optional<ProductScore> findByProductVersionIdAndRuleSetId(UUID productVersionId, UUID ruleSetId);

    long countByRuleSetId(UUID ruleSetId);

    long countByRuleSetIdAndStatus(UUID ruleSetId, String status);
}
