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
 * <p>Mirrors {@code HouseholdAccess} (D-023): nutrition, goals, insights and the dashboards call
 * this interface and never {@code HealthObservationRepository} directly. That matters more here
 * than it does for household data, because every write through this interface passes the
 * access policy (D-044), the D-036 age gate and the D-048 consent gate, and every read passes
 * the access policy — a module that reached the repository directly would bypass all of them,
 * and the bypass would be invisible in review.
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
     * <p>Subject to every gate a manual write passes: MANAGE on each metric's category, the age
     * gate, and the subject's tracking consent. A caller cannot smuggle a body metric onto a
     * child, or a value into another adult's record, by routing it through a derived source.
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

    /** Raw observations the caller may see, newest first. One audited access per call. */
    List<ObservationView> window(UUID callerAccountId, UUID subjectMemberId, Instant from, Instant to);

    /** Which categories the caller may read about this subject (D-044). */
    Set<MetricCategory> visibleCategories(UUID callerAccountId, UUID subjectMemberId);

    /**
     * Whether a metric may be recorded for this subject at all (D-036).
     *
     * <p>Published so goals can refuse a weight target for a child using exactly the check
     * that refuses a weight observation, rather than reimplementing the age rules.
     */
    boolean isMetricPermittedForSubject(UUID callerAccountId, UUID subjectMemberId, MetricCode metricCode);

    /**
     * Whether the caller may, right now, have values in this category derived into the
     * subject's record: they hold MANAGE on it (self, a guardian of a child, or a caregiver the
     * subject chose — D-044/D-045) <em>and</em> the subject's tracking consent is active (D-048).
     *
     * <p>Published so a producer can decide in advance which subjects to derive for — a family
     * meal derives for those, and leaves everyone else's share for that person to confirm
     * (D-041) — instead of discovering the boundary through an exception that rolls back the
     * whole meal.
     */
    boolean mayDeriveFor(UUID callerAccountId, UUID subjectMemberId, MetricCategory category);

    /**
     * Whether the subject is a child for every age-based rule: a CHILD member, or a profile
     * holding a child band (D-046). Goals use it for the child goal rule (D-047).
     */
    boolean isChildSubject(UUID subjectMemberId);
}
