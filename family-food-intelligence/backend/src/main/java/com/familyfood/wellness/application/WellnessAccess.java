package com.familyfood.wellness.application;

import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only way another module may write or read health observations.
 *
 * <p>Mirrors {@code HouseholdAccess} (D-023): nutrition, goals and habits call this
 * interface and never {@code HealthObservationRepository} directly. That matters more here
 * than it does for household data, because every write through this interface passes the
 * D-036 age gate and every read passes the D-038 visibility check — a module that reached
 * the repository directly would bypass both, and the bypass would be invisible in review.
 */
public interface WellnessAccess {

    /**
     * Replaces every observation previously derived from {@code sourceRef} for this subject
     * with the supplied set, in one transaction.
     *
     * <p>Delete-and-rewrite rather than update, because an observation is an append-only
     * historical fact (see {@code HealthObservation}). Editing a meal must not mutate the
     * rows an insight was already computed from; it retracts them and states new ones.
     * Passing an empty map retracts without replacing, which is what deleting a meal does.
     *
     * <p>Still subject to the age gate: a caller cannot smuggle a body metric onto a child
     * subject by routing it through a derived source.
     *
     * @param sourceRef the producing entity's id — opaque here, owned by the caller
     */
    void replaceDerived(UUID callerAccountId,
                        UUID subjectMemberId,
                        UUID sourceRef,
                        Map<MetricCode, BigDecimal> values,
                        Instant effectiveAt);

    /** Retracts everything derived from {@code sourceRef}, across every subject. */
    void retractDerived(UUID callerAccountId, UUID sourceRef);

    /**
     * Totals per metric for one subject over a window, restricted to what the caller may
     * see. Returns only metrics that have at least one observation — an absent key means
     * "nothing recorded", which callers must not render as zero.
     */
    Map<MetricCode, BigDecimal> sumByMetric(UUID callerAccountId,
                                            UUID subjectMemberId,
                                            Instant from,
                                            Instant to);

    /** The most recent value per metric for one subject, for "current weight"-style reads. */
    Map<MetricCode, ObservationView> latestByMetric(UUID callerAccountId,
                                                    UUID subjectMemberId,
                                                    Instant from,
                                                    Instant to);

    /** Raw observations the caller may see, newest first. */
    List<ObservationView> window(UUID callerAccountId, UUID subjectMemberId, Instant from, Instant to);

    /** Which categories the caller may read about this subject (D-038). */
    Set<MetricCategory> visibleCategories(UUID callerAccountId, UUID subjectMemberId);

    /**
     * Whether a metric may be recorded for this subject at all (D-036).
     *
     * <p>Published so goals can refuse a weight target for a child using exactly the check
     * that refuses a weight observation, rather than reimplementing the age rules.
     */
    boolean isMetricPermittedForSubject(UUID callerAccountId, UUID subjectMemberId, MetricCode metricCode);
}
