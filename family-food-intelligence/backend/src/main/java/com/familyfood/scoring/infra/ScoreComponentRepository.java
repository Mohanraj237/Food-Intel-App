package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.ScoreComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoreComponentRepository extends JpaRepository<ScoreComponent, UUID> {

    List<ScoreComponent> findByRuleSetId(UUID ruleSetId);
}
