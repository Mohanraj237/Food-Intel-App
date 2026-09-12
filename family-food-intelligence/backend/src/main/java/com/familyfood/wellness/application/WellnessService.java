package com.familyfood.wellness.application;

import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.MetricVisibilityGrant;
import com.familyfood.wellness.domain.ObservationSource;
import com.familyfood.wellness.infra.HealthObservationRepository;
import com.familyfood.wellness.infra.MetricVisibilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 1 of the wellness scope: recording and reading health observations, with the two
 * safety properties that everything built on top of this inherits.
 *
 * <h2>1. The age gate (D-036)</h2>
 * A body or clinical metric is refused for a child subject <em>in the domain</em>, not hidden
 * in the UI. The check is on the <em>subject's</em> age band, never the actor's: a guardian
 * recording their own weight is ordinary, and the same guardian recording a child's weight is
 * the thing this refuses. It fails closed — a subject with no profile has an unknown age, and
 * an unknown age is treated as possibly a child.
 *
 * <h2>2. Default-closed visibility (D-038)</h2>
 * Three independent questions, in order, and a "no" at any step is final:
 * <ol>
 *   <li>Is the metric permitted for this subject's age band?</li>
 *   <li>Does the actor have a relationship to the subject — self, or guardian?</li>
 *   <li>Has the subject granted visibility of this category to this actor?</li>
 * </ol>
 * Household role is never consulted. {@code OWNER} is not a health-data role, because the
 * failure mode worth designing against is not a stranger but a family member.
 *
 * <p>Deliberately absent: any interpretation of a value. No category, no reference range, no
 * "elevated". Naming a number is a clinical claim and needs a reviewed standard (D-037).
 */
@Service
public class WellnessService implements WellnessAccess {

    private static final String RESOURCE_TYPE_OBSERVATION = "HEALTH_OBSERVATION";
    private static final String CHILD_MEMBER_TYPE = "CHILD";

    /**
     * Categories a reader may always see about themselves, and the set a guardian may see
     * about a child they guard. Not "everything": a guardian's access is broad by necessity
     * but the categories a child can hold at all are already narrowed by {@link MetricCode}.
     */
    private static final Set<MetricCategory> ALL_CATEGORIES = EnumSet.allOf(MetricCategory.class);

    private final HealthObservationRepository observations;
    private final MetricVisibilityRepository visibility;
    private final HouseholdAccess households;
    private final ProfileService profiles;
    private final AuditService audit;
    private final Clock clock;

    public WellnessService(HealthObservationRepository observations,
                           MetricVisibilityRepository visibility,
                           HouseholdAccess households,
                           ProfileService profiles,
                           AuditService audit,
                           Clock clock) {
        this.observations = observations;
        this.visibility = visibility;
        this.households = households;
        this.profiles = profiles;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ recording */

    @Transactional
    public ObservationView record(UUID callerAccountId, RecordObservationCommand command) {
        MemberInfo subject = requireMember(command.subjectMemberId());
        requireWriteRelationship(callerAccountId, subject);

        AgeBand band = requireAgeBand(callerAccountId, subject);
        if (!command.metricCode().isPermittedFor(band)) {
            // Audited as a denial, because an attempt to record a child's weight is exactly
            // the event a reviewer of this product would want to be able to find.
            audit.denied(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE, RESOURCE_TYPE_OBSERVATION,
                    subject.id(), callerAccountId, subject.householdId(),
                    "metric " + command.metricCode() + " not permitted for age band " + band);
            throw new AppException(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        }

        Instant now = clock.instant();
        Instant effectiveAt = command.effectiveAt() == null ? now : command.effectiveAt();
        if (effectiveAt.isAfter(now)) {
            // A reading cannot apply to the future. Caught here as well as by the CHECK
            // constraint so the caller gets a 400 with a message rather than a 500.
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        HealthObservation saved = observations.save(HealthObservation.record(
                subject.id(),
                command.metricCode(),
                command.value(),
                effectiveAt,
                callerAccountId,
                command.source() == null ? ObservationSource.MANUAL : command.source(),
                now));

        // A child's observation is child data and is audited as such, in addition to the
        // ordinary record event — the strict category must not lose coverage.
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_OBSERVATION,
                    saved.getId(), callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.HEALTH_METRIC_RECORDED, RESOURCE_TYPE_OBSERVATION,
                saved.getId(), callerAccountId, subject.householdId());

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
        MemberInfo subject = requireMember(subjectMemberId);
        Set<MetricCategory> readable = readableCategories(callerAccountId, subject);

        if (!readable.contains(metricCode.category())) {
            audit.denied(AuditAction.HEALTH_DATA_ACCESS_DENIED, RESOURCE_TYPE_OBSERVATION,
                    subject.id(), callerAccountId, subject.householdId(),
                    "no visibility grant for category " + metricCode.category());
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        auditCrossMemberRead(callerAccountId, subject);

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
        MemberInfo subject = requireMember(subjectMemberId);
        Set<MetricCategory> readable = readableCategories(callerAccountId, subject);

        if (readable.isEmpty()) {
            // Not an error: a household member who shares nothing is a valid, expected
            // state, and the caller is entitled to know there is nothing for them.
            return List.of();
        }

        auditCrossMemberRead(callerAccountId, subject);

        return observations
                .findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                        subjectMemberId, new ArrayList<>(readable), from, to)
                .stream()
                .map(ObservationView::from)
                .toList();
    }

    /* ------------------------------------------------------------------ visibility */

    /** What the caller may currently see about a subject — drives the UI's own honesty. */
    @Override
    @Transactional(readOnly = true)
    public Set<MetricCategory> visibleCategories(UUID callerAccountId, UUID subjectMemberId) {
        return readableCategories(callerAccountId, requireMember(subjectMemberId));
    }

    /**
     * A subject shares one category with one audience member.
     *
     * <p>Only the subject may grant. A guardian cannot grant on an adult's behalf (D-002),
     * and an owner cannot grant on anyone's behalf. For a child subject there is nothing to
     * grant — a guardian's access already comes from the guardian link, and no other party
     * gains access to a child's data through this mechanism.
     */
    @Transactional
    public MetricVisibilityGrant share(UUID callerAccountId,
                                       UUID subjectMemberId,
                                       UUID audienceMemberId,
                                       MetricCategory category) {
        MemberInfo subject = requireMember(subjectMemberId);
        if (!isSelf(callerAccountId, subject)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        MemberInfo audience = requireMember(audienceMemberId);
        if (!audience.householdId().equals(subject.householdId())) {
            throw new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER);
        }
        if (audienceMemberId.equals(subjectMemberId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        // Idempotent: re-sharing an already-shared category returns the live grant rather
        // than tripping the partial unique index with a 500.
        Optional<MetricVisibilityGrant> existing = visibility
                .findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(subjectMemberId, audienceMemberId)
                .stream()
                .filter(g -> g.getMetricCategory() == category)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        MetricVisibilityGrant granted = visibility.save(MetricVisibilityGrant.grant(
                subjectMemberId, audienceMemberId, category, callerAccountId, clock.instant()));
        audit.success(AuditAction.HEALTH_VISIBILITY_GRANTED, RESOURCE_TYPE_OBSERVATION,
                granted.getId(), callerAccountId, subject.householdId());
        return granted;
    }

    /** Withdraws a grant. Only the subject may revoke their own sharing. */
    @Transactional
    public void unshare(UUID callerAccountId, UUID subjectMemberId, UUID grantId) {
        MemberInfo subject = requireMember(subjectMemberId);
        if (!isSelf(callerAccountId, subject)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        MetricVisibilityGrant grant = visibility.findByIdAndSubjectMemberId(grantId, subjectMemberId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        grant.revoke(clock.instant());
        visibility.save(grant);
        audit.success(AuditAction.HEALTH_VISIBILITY_REVOKED, RESOURCE_TYPE_OBSERVATION,
                grant.getId(), callerAccountId, subject.householdId());
    }

    /** Live grants a subject has made, for their own privacy screen. */
    @Transactional(readOnly = true)
    public List<MetricVisibilityGrant> sharesBySubject(UUID callerAccountId, UUID subjectMemberId) {
        MemberInfo subject = requireMember(subjectMemberId);
        if (!isSelf(callerAccountId, subject)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return visibility.findBySubjectMemberIdAndRevokedAtIsNull(subjectMemberId);
    }

    /* ------------------------------------------------- WellnessAccess (other modules) */

    /**
     * Retract-and-restate, never mutate. See {@link WellnessAccess#replaceDerived}.
     *
     * <p>The age gate still applies. Routing a body metric through a derived source must not
     * become a way to record one for a child, so each value is checked exactly as a manual
     * write would be — the check lives on the metric, so there is nothing to remember here.
     */
    @Override
    @Transactional
    public void replaceDerived(UUID callerAccountId,
                               UUID subjectMemberId,
                               UUID sourceRef,
                               Map<MetricCode, BigDecimal> values,
                               Instant effectiveAt) {
        MemberInfo subject = requireMember(subjectMemberId);
        requireWriteRelationship(callerAccountId, subject);

        observations.deleteByHouseholdMemberIdAndSourceRef(subjectMemberId, sourceRef);
        if (values.isEmpty()) {
            return;
        }

        AgeBand band = requireAgeBand(callerAccountId, subject);
        Instant now = clock.instant();

        for (Map.Entry<MetricCode, BigDecimal> entry : values.entrySet()) {
            if (!entry.getKey().isPermittedFor(band)) {
                audit.denied(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE, RESOURCE_TYPE_OBSERVATION,
                        subject.id(), callerAccountId, subject.householdId(),
                        "derived metric " + entry.getKey() + " not permitted for age band " + band);
                throw new AppException(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
            }
            observations.save(HealthObservation.record(
                    subjectMemberId, entry.getKey(), entry.getValue(), effectiveAt,
                    callerAccountId, ObservationSource.DERIVED, now, sourceRef));
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
        Map<MetricCode, BigDecimal> totals = new java.util.EnumMap<>(MetricCode.class);
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
        Map<MetricCode, ObservationView> latest = new java.util.EnumMap<>(MetricCode.class);
        // The window is already newest-first, so the first sighting of a metric wins.
        for (ObservationView o : window(callerAccountId, subjectMemberId, from, to)) {
            latest.putIfAbsent(o.metricCode(), o);
        }
        return latest;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMetricPermittedForSubject(UUID callerAccountId,
                                               UUID subjectMemberId,
                                               MetricCode metricCode) {
        MemberInfo subject = requireMember(subjectMemberId);
        try {
            return metricCode.isPermittedFor(requireAgeBand(callerAccountId, subject));
        } catch (AppException e) {
            // No profile means an unknown age, and an unknown age is treated as possibly a
            // child — the same fail-closed posture as recording.
            if (e.code() == ErrorCode.PROFILE_REQUIRED_FOR_METRIC) {
                return false;
            }
            throw e;
        }
    }

    /* -------------------------------------------------------------------- erasure */

    /** Called by account erasure (D-028). Observations are the subject's data, not a record
     *  of consent, so they are removed outright rather than pseudonymised. */
    @Transactional
    public void eraseAllForMember(UUID subjectMemberId) {
        observations.deleteByHouseholdMemberId(subjectMemberId);
        visibility.deleteBySubjectMemberId(subjectMemberId);
    }

    /* --------------------------------------------------------------------- internals */

    private MemberInfo requireMember(UUID memberId) {
        return households.findMember(memberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private boolean isSelf(UUID callerAccountId, MemberInfo subject) {
        return callerAccountId.equals(subject.userAccountId());
    }

    /**
     * Who may write an observation about a subject: the subject themselves, or a guardian of
     * a child subject. Notably <em>not</em> another adult in the household, however senior —
     * an adult is their own data principal (D-002), and there is no proxy.
     */
    private void requireWriteRelationship(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return;
        }
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())
                && households.isGuardianOf(callerAccountId, subject.id())) {
            return;
        }
        audit.denied(AuditAction.HEALTH_DATA_ACCESS_DENIED, RESOURCE_TYPE_OBSERVATION,
                subject.id(), callerAccountId, subject.householdId(),
                "caller is neither the subject nor a guardian of this subject");
        throw new AppException(ErrorCode.ACCESS_DENIED);
    }

    /**
     * The subject's current age band, which the D-036 gate needs.
     *
     * <p>Fails closed. A subject with no profile has an unknown age, and an unknown age must
     * not be treated as adult — that would make "skip the profile step" a way to record a
     * child's weight.
     */
    private AgeBand requireAgeBand(UUID callerAccountId, MemberInfo subject) {
        try {
            ProfileView profile = profiles.getCurrentProfile(callerAccountId, subject.id());
            return profile.ageBand();
        } catch (AppException e) {
            if (e.code() == ErrorCode.RESOURCE_NOT_FOUND) {
                throw new AppException(ErrorCode.PROFILE_REQUIRED_FOR_METRIC);
            }
            throw e;
        }
    }

    /**
     * The categories the caller may read about this subject.
     *
     * <p>Self sees everything. A guardian sees a child's categories, because someone has to
     * and the set a child may hold is already narrowed by {@link MetricCode}. Everyone else
     * sees exactly what the subject has explicitly shared — the empty set by default.
     */
    private Set<MetricCategory> readableCategories(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return ALL_CATEGORIES;
        }
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())
                && households.isGuardianOf(callerAccountId, subject.id())) {
            return ALL_CATEGORIES;
        }

        // Another adult: only what they were given, and the grant names a member rather than
        // an account, so we resolve the caller's own membership in the subject's household.
        Optional<MemberInfo> audience = households.listActiveMembers(subject.householdId()).stream()
                .filter(m -> callerAccountId.equals(m.userAccountId()))
                .findFirst();
        if (audience.isEmpty()) {
            return Set.of();
        }

        return visibility
                .findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(subject.id(), audience.get().id())
                .stream()
                .map(MetricVisibilityGrant::getMetricCategory)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(MetricCategory.class)));
    }

    /** Every read of someone else's health data leaves a trace (D-038). */
    private void auditCrossMemberRead(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return;
        }
        // Child reads additionally land in the strict child-data category.
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_OBSERVATION,
                    subject.id(), callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.HEALTH_DATA_ACCESSED, RESOURCE_TYPE_OBSERVATION,
                subject.id(), callerAccountId, subject.householdId());
    }
}
