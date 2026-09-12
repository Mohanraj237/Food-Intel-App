package com.familyfood.goals.infra;

import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.wellness.domain.MetricCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WellnessGoalRepository extends JpaRepository<WellnessGoal, UUID> {

    /** Everything a member has ever targeted, newest first — the history view. */
    List<WellnessGoal> findByHouseholdMemberIdOrderByStartedOnDesc(UUID householdMemberId);

    /** What is in force now. The only set progress is computed for. */
    List<WellnessGoal> findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(UUID householdMemberId);

    /**
     * The live goal for one metric, if any. Mirrors the {@code uq_goal_live_per_metric} partial
     * index so a duplicate is reported as a conflict rather than surfacing as a 500.
     */
    Optional<WellnessGoal> findByHouseholdMemberIdAndMetricCodeAndActiveTrue(
            UUID householdMemberId, MetricCode metricCode);

    /** Called by erasure: a goal is the subject's data, not a record of consent. */
    void deleteByHouseholdMemberId(UUID householdMemberId);
}
