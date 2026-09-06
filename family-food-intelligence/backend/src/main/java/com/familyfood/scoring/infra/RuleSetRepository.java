package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.RuleSet;
import com.familyfood.scoring.domain.RuleSetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RuleSetRepository extends JpaRepository<RuleSet, UUID> {

    /**
     * Only meaningful for {@link RuleSetStatus#ACTIVE}: {@code uq_rule_set_active} (a partial
     * unique index in V5__scoring.sql) guarantees at most one such row exists, so this is
     * safe to call for ACTIVE. DRAFT and RETIRED carry no such guarantee and may hold more
     * than one row — calling this with either would throw
     * {@code IncorrectResultSizeDataAccessException} the moment a second such row exists.
     */
    Optional<RuleSet> findByStatus(RuleSetStatus status);

    Optional<RuleSet> findByVersion(String version);
}
