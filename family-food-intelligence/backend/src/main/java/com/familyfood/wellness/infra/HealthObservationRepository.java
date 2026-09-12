package com.familyfood.wellness.infra;

import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HealthObservationRepository extends JpaRepository<HealthObservation, UUID> {

    /** One metric's history for one subject, newest first — the tracker-screen query. */
    List<HealthObservation> findByHouseholdMemberIdAndMetricCodeAndEffectiveAtBetweenOrderByEffectiveAtDesc(
            UUID householdMemberId, MetricCode metricCode, Instant from, Instant to);

    /**
     * Everything in a window for one subject, restricted to categories the caller may see.
     *
     * <p>The category filter is a parameter rather than something applied after the fetch:
     * filtering in memory would mean rows the caller is not allowed to see are loaded,
     * logged by the SQL logger, and one refactor away from being returned.
     */
    List<HealthObservation> findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(
            UUID householdMemberId, List<MetricCategory> categories, Instant from, Instant to);

    void deleteByHouseholdMemberId(UUID householdMemberId);

    /** Retract exactly the rows one producer derived for one subject. */
    void deleteByHouseholdMemberIdAndSourceRef(UUID householdMemberId, UUID sourceRef);

    /** Retract everything a producer derived, across every subject it apportioned to. */
    void deleteBySourceRef(UUID sourceRef);

    List<HealthObservation> findBySourceRef(UUID sourceRef);
}
