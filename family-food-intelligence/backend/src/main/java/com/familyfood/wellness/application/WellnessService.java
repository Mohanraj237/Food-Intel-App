package com.familyfood.wellness.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import com.familyfood.wellness.infra.HealthObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recording and reading health observations, with the safety properties everything built on
 * top of this inherits.
 *
 * <h2>1. Access (D-044)</h2>
 * Who may read or record what is decided by {@link HealthAccessPolicy} and nowhere else —
 * self, a guardian of a child, or someone the subject granted VIEW or MANAGE on the metric's
 * category. Household role is never consulted. Before W1 this class held its own copy of the
 * rule; that copy is gone, not kept alongside.
 *
 * <h2>2. The age gate (D-036)</h2>
 * A body or clinical metric is refused for a child subject <em>in the domain</em>, not hidden
 * in the UI. The check is on the <em>subject's</em> age, never the actor's, and it fails closed:
 * no profile means an unknown age, which is treated as possibly a child. A subject whose member
 * type is CHILD is a child whatever band their profile holds (D-046, defence in depth).
 *
 * <h2>3. Consent (D-048)</h2>
 * Collection needs the subject's tracking purpose to be active — {@code HEALTH_TRACKING} for an
 * adult, {@code CHILD_WELLNESS_TRACKING} for a child. Disclosure to anyone else needs it too, and
 * the policy applies that. A subject's own reads of what is already stored stay available.
 *
 * <p>Checked in that order — access, age, consent — so that "we do not store this for a child"
 * is the answer a guardian gets for a child's weight whether or not consent has been given.
 *
 * <p>Deliberately absent: any interpretation of a value. No category, no reference range, no
 * "elevated". Naming a number is a clinical claim and needs a reviewed standard (D-037).
 */
@Service
public class WellnessService implements WellnessAccess {

    private static final String RESOURCE_TYPE_OBSERVATION = "HEALTH_OBSERVATION";
    private static final String CHILD_MEMBER_TYPE = "CHILD";

    private final HealthObservationRepository observations;
    private final HealthAccessPolicy access;
    private final TrackingConsent trackingConsent;
    private final HouseholdAccess households;
    private final ProfileService profiles;
    private final AuditService audit;
    private final Clock clock;

    public WellnessService(HealthObservationRepository observations,
                           HealthAccessPolicy access,
                           TrackingConsent trackingConsent,
                           HouseholdAccess households,
                           ProfileService profiles,
                           AuditService audit,
                           Clock clock) {
        this.observations = observations;
        this.access = access;
        this.trackingConsent = trackingConsent;
        this.households = households;
        this.profiles = profiles;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ recording */

    @Transactional
    public ObservationView record(UUID callerAccountId, RecordObservationCommand command) {
        MetricCode metric = command.metricCode();
        AccessDecision decision = access.requireManage(
                callerAccountId, command.subjectMemberId(), metric.category().scope(), RESOURCE_TYPE_OBSERVATION);
        MemberInfo subject = decision.subject();

        requirePermittedForAge(callerAccountId, subject, metric);
        trackingConsent.require(subject);

        Instant now = clock.instant();
        Instant effectiveAt = command.effectiveAt() == null ? now : command.effectiveAt();
        if (effectiveAt.isAfter(now)) {
            // A reading cannot apply to the future. Caught here as well as by the CHECK
            // constraint so the caller gets a 400 with a message rather than a 500.
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        HealthObservation saved = observations.save(HealthObservation.record(
                subject.id(),
                metric,
                command.value(),
                effectiveAt,
                callerAccountId,
                command.source() == null ? ObservationSource.MANUAL : command.source(),
                now));

        auditWrite(callerAccountId, decision, saved.getId(), metric.category().scope());
        return ObservationView.from(saved);
    }

    /* --------------------------------------------------------------------- reading */

    /**
     * One metric's history for one subject.
     *
     * <p>Requires visibility of that metric's category. An unauthorized read returns 403
     * rather than an empty list: silently returning nothing would let a caller distinguish
     * "no data" from "not allowed" only by guessing, and would make an access failure look
     * like an empty tracker.
     */
    @Transactional(readOnly = true)
    public List<ObservationView> history(UUID callerAccountId,
                                         UUID subjectMemberId,
                                         MetricCode metricCode,
                                         Instant from,
                                         Instant to) {
        access.requireView(callerAccountId, subjectMemberId, metricCode.category().scope(),
                RESOURCE_TYPE_OBSERVATION);

        return observations
                .findByHouseholdMemberIdAndMetricCodeAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                        subjectMemberId, metricCode, from, to)
                .stream()
                .map(ObservationView::from)
                .toList();
    }

    /**
     * Everything the caller may see about one subject in a window.
     *
     * <p>The category restriction is pushed into the query rather than applied afterwards:
     * fetching rows the caller may not see and filtering them in memory means those rows are
     * loaded, appear in SQL logs, and are one careless refactor away from being returned.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObservationView> window(UUID callerAccountId,
                                        UUID subjectMemberId,
                                        Instant from,
                                        Instant to) {
        AccessDecision decision = access.decide(callerAccountId, subjectMemberId);
        Set<MetricCategory> readable = readableCategories(decision);

        if (readable.isEmpty()) {
            // Not an error: a household member who shares nothing is a valid, expected
            // state, and the caller is entitled to know there is nothing for them.
            return List.of();
        }

        access.auditRead(callerAccountId, decision,
                readable.stream().map(MetricCategory::scope).toList(), RESOURCE_TYPE_OBSERVATION);

        return observations
                .findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                        subjectMemberId, List.copyOf(readable), from, to)
                .stream()
                .map(ObservationView::from)
                .toList();
    }

    /** What the caller may currently see about a subject — drives the UI's own honesty. */
    @Override
    @Transactional(readOnly = true)
    public Set<MetricCategory> visibleCategories(UUID callerAccountId, UUID subjectMemberId) {
        return readableCategories(access.decide(callerAccountId, subjectMemberId));
    }

    /* ------------------------------------------------- WellnessAccess (other modules) */

    /**
     * Retract-and-restate, never mutate. See {@link WellnessAccess#replaceDerived}.
     *
     * <p>Every gate a manual write passes applies here too — the caller must hold MANAGE on each
     * metric's category, each metric must be permitted for the subject's age, and the subject's
     * tracking consent must be active. Routing a value through a derived source must never be a
     * way round any of them.
     */
    @Override
    @Transactional
    public void replaceDerived(UUID callerAccountId,
                               UUID subjectMemberId,
                               UUID sourceRef,
                               Map<MetricCode, BigDecimal> values,
                               Instant effectiveAt) {
        AccessDecision decision = access.decide(callerAccountId, subjectMemberId);
        Set<AccessScope> scopes = values.keySet().stream()
                .map(metric -> metric.category().scope())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccessScope.class)));
        for (AccessScope scope : scopes) {
            if (!decision.canManage(scope)) {
                // Re-asked through the policy only to get its audited refusal; it throws.
                access.requireManage(callerAccountId, subjectMemberId, scope, RESOURCE_TYPE_OBSERVATION);
            }
        }
        MemberInfo subject = decision.subject();

        observations.deleteByHouseholdMemberIdAndSourceRef(subject.id(), sourceRef);
        if (values.isEmpty()) {
            return;
        }

        for (MetricCode metric : values.keySet()) {
            requirePermittedForAge(callerAccountId, subject, metric);
        }
        trackingConsent.require(subject);

        Instant now = clock.instant();
        for (Map.Entry<MetricCode, BigDecimal> entry : values.entrySet()) {
            observations.save(HealthObservation.record(
                    subject.id(), entry.getKey(), entry.getValue(), effectiveAt,
                    callerAccountId, ObservationSource.DERIVED, now, sourceRef));
        }

        // A write into someone else's record — a caregiver logging an elder's meal (D-045),
        // a guardian a child's — belongs in that person's access log. The subject's own writes
        // are not "access" and would only be noise there.
        if (decision.isCrossMember()) {
            for (AccessScope scope : scopes) {
                auditWrite(callerAccountId, decision, sourceRef, scope);
            }
        }
    }

    @Override
    @Transactional
    public void retractDerived(UUID callerAccountId, UUID sourceRef) {
        // Authorization is the producing module's responsibility: it only reaches here
        // having already established the caller may edit the thing being retracted, and it
        // owns the meaning of sourceRef. Retracting rows this module derived on that
        // module's behalf is not an independent grant of access to anything.
        observations.deleteBySourceRef(sourceRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<MetricCode, BigDecimal> sumByMetric(UUID callerAccountId,
                                                   UUID subjectMemberId,
                                                   Instant from,
                                                   Instant to) {
        Map<MetricCode, BigDecimal> totals = new EnumMap<>(MetricCode.class);
        for (ObservationView o : window(callerAccountId, subjectMemberId, from, to)) {
            totals.merge(o.metricCode(), o.value(), BigDecimal::add);
        }
        return totals;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<MetricCode, ObservationView> latestByMetric(UUID callerAccountId,
                                                           UUID subjectMemberId,
                                                           Instant from,
                                                           Instant to) {
        Map<MetricCode, ObservationView> latest = new EnumMap<>(MetricCode.class);
        // The window is already newest-first, so the first sighting of a metric wins.
        for (ObservationView o : window(callerAccountId, subjectMemberId, from, to)) {
            latest.putIfAbsent(o.metricCode(), o);
        }
        return latest;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mayDeriveFor(UUID callerAccountId, UUID subjectMemberId, MetricCategory category) {
        AccessDecision decision = access.decide(callerAccountId, subjectMemberId);
        return decision.canManage(category.scope()) && trackingConsent.isActive(decision.subject());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMetricPermittedForSubject(UUID callerAccountId,
                                               UUID subjectMemberId,
                                               MetricCode metricCode) {
        MemberInfo subject = requireMember(subjectMemberId);
        return ageVerdict(subject, metricCode) == AgeVerdict.PERMITTED;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isChildSubject(UUID subjectMemberId) {
        MemberInfo subject = requireMember(subjectMemberId);
        return CHILD_MEMBER_TYPE.equals(subject.memberType())
                || profiles.currentAgeBand(subject.id()).map(AgeBand::isChild).orElse(false);
    }

    /* -------------------------------------------------------------- data rights */

    /** Called by account and child erasure (D-028). Observations are the subject's own data. */
    @Transactional
    public void eraseAllForMember(UUID subjectMemberId) {
        observations.deleteByHouseholdMemberId(subjectMemberId);
    }

    /** Every observation a member has, for their data export (AC-9.2). No authorization here:
     *  the export orchestrator has already established the caller is the subject or guardian. */
    @Transactional(readOnly = true)
    public List<ObservationView> exportForMember(UUID subjectMemberId) {
        return observations.findByHouseholdMemberIdOrderByEffectiveAtDesc(subjectMemberId).stream()
                .map(ObservationView::from)
                .toList();
    }

    /* --------------------------------------------------------------------- internals */

    private enum AgeVerdict { PERMITTED, REFUSED, UNKNOWN_AGE }

    /**
     * The D-036 gate, for one subject and one metric. Reads the band through the trusted
     * {@link ProfileService#currentAgeBand}, because the gate must hold for the subject no
     * matter who is acting — a caregiver may record an elder's blood pressure without being
     * allowed to read the elder's profile (D-045).
     */
    private AgeVerdict ageVerdict(MemberInfo subject, MetricCode metric) {
        Optional<AgeBand> band = profiles.currentAgeBand(subject.id());
        if (band.isEmpty()) {
            return AgeVerdict.UNKNOWN_AGE;
        }
        boolean child = CHILD_MEMBER_TYPE.equals(subject.memberType()) || band.get().isChild();
        if (child && metric.permittedFor() == MetricCode.AgeClass.ADULT_ONLY) {
            return AgeVerdict.REFUSED;
        }
        return AgeVerdict.PERMITTED;
    }

    private void requirePermittedForAge(UUID callerAccountId, MemberInfo subject, MetricCode metric) {
        switch (ageVerdict(subject, metric)) {
            case PERMITTED -> { }
            case UNKNOWN_AGE -> throw new AppException(ErrorCode.PROFILE_REQUIRED_FOR_METRIC);
            case REFUSED -> {
                // Audited as a denial, because an attempt to record a child's weight is exactly
                // the event a reviewer of this product would want to be able to find.
                audit.deniedForSubject(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE, RESOURCE_TYPE_OBSERVATION,
                        subject.id(), callerAccountId, subject.householdId(), subject.id(),
                        "metric " + metric + " not permitted for this subject's age");
                throw new AppException(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
            }
        }
    }

    /** The metric categories among the scopes a decision lets the caller see. */
    private static Set<MetricCategory> readableCategories(AccessDecision decision) {
        Set<MetricCategory> categories = new LinkedHashSet<>();
        for (AccessScope scope : decision.viewableScopes()) {
            MetricCategory.of(scope).ifPresent(categories::add);
        }
        return categories.isEmpty() ? EnumSet.noneOf(MetricCategory.class) : EnumSet.copyOf(categories);
    }

    /**
     * Records a write with the subject attached, so a caregiver's or guardian's write appears in
     * the subject's access log. A child's data is additionally audited in the strict category.
     */
    private void auditWrite(UUID callerAccountId, AccessDecision decision, UUID resourceId, AccessScope scope) {
        Map<String, Object> metadata = Map.of(
                "scope", scope.name(), "relationship", decision.relationship().name());
        if (decision.isChildSubject()) {
            audit.successForSubject(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_OBSERVATION, resourceId,
                    callerAccountId, decision.householdId(), decision.subjectId(), metadata);
        }
        audit.successForSubject(AuditAction.HEALTH_METRIC_RECORDED, RESOURCE_TYPE_OBSERVATION, resourceId,
                callerAccountId, decision.householdId(), decision.subjectId(), metadata);
    }

    private MemberInfo requireMember(UUID memberId) {
        return households.findMember(memberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
