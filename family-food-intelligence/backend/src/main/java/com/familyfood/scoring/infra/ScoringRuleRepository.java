package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.ScoringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoringRuleRepository extends JpaRepository<ScoringRule, UUID> {

    List<ScoringRule> findByRuleSetId(UUID ruleSetId);
}
