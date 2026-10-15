package com.familyfood.goals.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.goals.domain.ChildGoalRule;
import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalMetricAggregation;
import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.goals.infra.WellnessGoalRepository;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.HouseholdInfo;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Targets a member sets for themselves — or a guardian for a child, or a caregiver the member
 * chose (D-045) — and what the member's own logged data says about them.
 *
 * <h2>Safety rules this module applies</h2>
 * <ul>
 *   <li><b>The metric age gate (D-036)</b>, through
 *       {@link WellnessAccess#isMetricPermittedForSubject} — the same call that refuses a weight
 *       <em>observation</em> for a child. No second copy of the age rules lives here.</li>
 *   <li><b>The child goal rule (D-047).</b> A child's goal may only aim at more activity, sleep
 *       or water, or at keeping them steady: no nutrition target of any kind (no calorie, sugar
 *       or macro target for a minor), no wellbeing score to aim at, and no "at most" on
 *       anything. Children's routines belong in habits, which carry no numeric target.</li>
 *   <li><b>The adult guardrail (D-047).</b> A calorie cap needs a reviewed safety floor
 *       (D-025) before the product will hold one, so it is refused as "not yet".</li>
 * </ul>
 *
 * <h2>Progress is arithmetic, not assessment</h2>
 * Progress reports the measured amount, the target, the direction and the window. It does not
 * report a score, a percentage, a streak or a verdict. Absent data is reported as absent — an
 * unlogged day is not a zero (see {@link GoalProgressView}). Days are the household's days
 * (D-053), so a daily goal runs midnight to midnight where the person lives.
 *
 * <p>Access is the one policy (D-044): creating or ending a goal needs MANAGE on the goal
 * metric's category, reading needs VIEW on it. A steps goal is ACTIVITY data; someone who can
 * see a member's activity can see their steps goal, and no one else can.
 */
@Service
public class GoalService {

    private static final String RESOURCE_TYPE_GOAL = "WELLNESS_GOAL";

    private final WellnessGoalRepository goals;
    private final WellnessAccess wellness;
    private final HealthAccessPolicy access;
    private final TrackingConsent trackingConsent;
    private final HouseholdAccess households;
    private final AuditService audit;
    private final Clock clock;

    public GoalService(WellnessGoalRepository goals,
                       WellnessAccess wellness,
                       HealthAccessPolicy access,
                       TrackingConsent trackingConsent,
                       HouseholdAccess households,
                       AuditService audit,
                       Clock clock) {
        this.goals = goals;
        this.wellness = wellness;
        this.access = access;
        this.trackingConsent = trackingConsent;
        this.households = households;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------- lifecycle */

    @Transactional
    public GoalView createGoal(UUID callerAccountId, CreateGoalCommand command) {
        requireValidTarget(command);
        MetricCode metric = command.metricCode();
        AccessDecision decision = access.requireManage(
                callerAccountId, command.memberId(), scopeOf(metric), RESOURCE_TYPE_GOAL);
        MemberInfo subject = decision.subject();

        // 1. May this metric be tracked for this subject at all? The same call that answers it
        //    for a manual reading, so a weight goal for a child is refused by the weight rule.
        if (!wellness.isMetricPermittedForSubject(callerAccountId, subject.id(), metric)) {
            refuseForAge(callerAccountId, subject, "goal on metric " + metric
                    + " not permitted for this subject's age band");
            throw new AppException(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        }

        // 2. D-047, child: permitted metrics are not enough. A calorie or sugar target is a
        //    NUTRITION goal, and those are the restriction framing a minor must never be given.
        if (wellness.isChildSubject(subject.id())
                && !ChildGoalRule.allows(metric.category(), command.direction())) {
            refuseForAge(callerAccountId, subject, "goal " + command.direction() + " " + metric
                    + " not permitted for a child (D-047)");
            throw new AppException(ErrorCode.GOAL_NOT_PERMITTED_FOR_CHILD);
        }

        // 3. D-047, adult: a calorie cap waits for a reviewed safety floor.
        if (metric == MetricCode.ENERGY_KCAL && command.direction() == GoalDirection.AT_MOST) {
            throw new AppException(ErrorCode.GOAL_REQUIRES_REVIEWED_STANDARD);
        }

        // 4. A goal is a standing intention to track, so it is tracking data (D-048).
        trackingConsent.require(subject);

        // Checked before the insert so the caller gets a 409 with a message rather than the
        // partial unique index surfacing as a 500. The index remains the authority.
        if (goals.findByHouseholdMemberIdAndMetricCodeAndActiveTrue(subject.id(), metric).isPresent()) {
            throw new AppException(ErrorCode.GOAL_ALREADY_ACTIVE);
        }

        Instant now = clock.instant();
        WellnessGoal saved;
        try {
            saved = goals.save(WellnessGoal.start(
                    subject.id(), metric, command.direction(), command.targetValue(),
                    command.period(), today(subject), callerAccountId, now));
        } catch (DataIntegrityViolationException e) {
            // Two creates racing past the check above. The loser sees the same conflict it
            // would have seen a millisecond earlier.
            throw new AppException(ErrorCode.GOAL_ALREADY_ACTIVE, Map.of(), e);
        }

        auditWrite(callerAccountId, decision, AuditAction.GOAL_CREATED, saved.getId(), scopeOf(metric));
        return GoalView.from(saved);
    }

    /**
     * Stops a goal. The row survives with {@code ended_on} set rather than being deleted, so
     * that progress a member saw last month stays attributable to the target that was actually
     * in force then.
     */
    @Transactional
    public GoalView endGoal(UUID callerAccountId, UUID goalId) {
        WellnessGoal goal = goals.findById(goalId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        AccessDecision decision = access.requireManage(callerAccountId, goal.getHouseholdMemberId(),
                scopeOf(goal.getMetricCode()), RESOURCE_TYPE_GOAL);

        goal.end(today(decision.subject()));
        WellnessGoal saved = goals.save(goal);

        auditWrite(callerAccountId, decision, AuditAction.GOAL_ENDED, saved.getId(), scopeOf(goal.getMetricCode()));
        return GoalView.from(saved);
    }

    /* ---------------------------------------------------------------------- reading */

    /**
     * The goals the caller may see — those whose metric category they can view. Someone who can
     * see nothing of the member is refused, so that "not allowed" never reads as "no goals".
     */
    @Transactional(readOnly = true)
    public List<GoalView> listGoals(UUID callerAccountId, UUID memberId, boolean includeEnded) {
        AccessDecision decision = access.requireAnyView(callerAccountId, memberId, RESOURCE_TYPE_GOAL);

        List<WellnessGoal> found = includeEnded
                ? goals.findByHouseholdMemberIdOrderByStartedOnDesc(memberId)
                : goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(memberId);

        List<WellnessGoal> visible = found.stream()
                .filter(g -> decision.canView(scopeOf(g.getMetricCode())))
                .toList();
        access.auditRead(callerAccountId, decision, scopesOf(visible), RESOURCE_TYPE_GOAL);
        return visible.stream().map(GoalView::from).toList();
    }

    /**
     * Actual against target for every live goal the caller may see.
     *
     * <p>{@code from} and {@code to} are optional. When omitted, each goal supplies its own
     * window from its period — a daily goal means today, a weekly goal the last seven days —
     * in the household's days (D-053). When supplied, that range is used for every goal
     * regardless of period, and the window is echoed back in each result either way, because a
     * total with an unstated window is not checkable.
     *
     * <p>Reads go through {@link WellnessAccess}, so the observations are filtered and the read
     * is audited there; this method does not audit a second time.
     */
    @Transactional(readOnly = true)
    public List<GoalProgressView> goalProgress(UUID callerAccountId,
                                               UUID memberId,
                                               LocalDate from,
                                               LocalDate to) {
        AccessDecision decision = access.requireAnyView(callerAccountId, memberId, RESOURCE_TYPE_GOAL);
        ZoneId zone = zoneOf(decision.subject());

        List<WellnessGoal> active = goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(memberId).stream()
                .filter(g -> decision.canView(scopeOf(g.getMetricCode())))
                .toList();
        LocalDate lastDay = to == null ? LocalDate.now(clock.withZone(zone)) : to;

        // Goals commonly share a window, and every distinct window is a query. Memoised per
        // call so a member with six daily goals reads the window once rather than six times.
        Map<Window, Map<MetricCode, BigDecimal>> sums = new HashMap<>();
        Map<Window, Map<MetricCode, ObservationView>> latest = new HashMap<>();

        List<GoalProgressView> progress = new ArrayList<>(active.size());
        for (WellnessGoal goal : active) {
            LocalDate firstDay = from == null
                    ? lastDay.minusDays(goal.getPeriod().days() - 1L)
                    : from;
            Window window = Window.of(firstDay, lastDay, zone);

            BigDecimal actual = switch (GoalMetricAggregation.forMetric(goal.getMetricCode())) {
                case SUM -> sums
                        .computeIfAbsent(window,
                                w -> wellness.sumByMetric(callerAccountId, memberId, w.from(), w.to()))
                        .get(goal.getMetricCode());
                case LATEST -> {
                    ObservationView observation = latest
                            .computeIfAbsent(window,
                                    w -> wellness.latestByMetric(callerAccountId, memberId, w.from(), w.to()))
                            .get(goal.getMetricCode());
                    yield observation == null ? null : observation.value();
                }
            };

            progress.add(progressOf(goal, actual, window));
        }
        return progress;
    }

    /**
     * {@link #goalProgress} for a composition that has already read the member's observations —
     * the family overview reads each member's week once, and every goal window (a day, or at most
     * {@link com.familyfood.goals.domain.GoalPeriod#WEEKLY seven days}) falls inside it.
     *
     * <p>Reads no observations and audits nothing: {@code observations} came from
     * {@link WellnessAccess#window}, which filtered them to the caller's visible categories and
     * audited that read. Asking {@link #goalProgress} instead would read, and audit, each member's
     * window again for every card on the screen — noise in the very access log a person checks to
     * see who has looked at their records. Each window is reduced exactly as
     * {@link WellnessAccess#sumByMetric} and {@link WellnessAccess#latestByMetric} reduce it, so
     * the two methods agree about every goal.
     *
     * @param decision     the caller's decision about the subject; goals outside its visible
     *                     categories are left out, as {@link #goalProgress} leaves them out
     * @param lastDay      the household-local day each goal's window ends on
     * @param zone         the subject's household zone (D-053)
     * @param observations the subject's observations, newest first, covering at least the seven
     *                     days ending on {@code lastDay}
     */
    @Transactional(readOnly = true)
    public List<GoalProgressView> progressFrom(AccessDecision decision,
                                               LocalDate lastDay,
                                               ZoneId zone,
                                               List<ObservationView> observations) {
        List<GoalProgressView> progress = new ArrayList<>();
        for (WellnessGoal goal : goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(decision.subjectId())) {
            if (decision.canView(scopeOf(goal.getMetricCode()))) {
                Window window = Window.of(lastDay.minusDays(goal.getPeriod().days() - 1L), lastDay, zone);
                progress.add(progressOf(goal, actualIn(goal.getMetricCode(), window, observations), window));
            }
        }
        return progress;
    }

    /**
     * One metric's number for a window: the total for a summed metric, the newest reading
     * otherwise; {@code null} when the window holds nothing for it.
     */
    private static BigDecimal actualIn(MetricCode metric, Window window, List<ObservationView> observations) {
        List<ObservationView> inWindow = observations.stream()
                .filter(o -> o.metricCode() == metric)
                .filter(o -> !o.effectiveAt().isBefore(window.from()) && !o.effectiveAt().isAfter(window.to()))
                .toList();
        if (inWindow.isEmpty()) {
            return null;
        }
        return switch (GoalMetricAggregation.forMetric(metric)) {
            case SUM -> inWindow.stream().map(ObservationView::value).reduce(BigDecimal::add).orElseThrow();
            // Newest first, so the first is the latest — the same pick latestByMetric makes.
            case LATEST -> inWindow.get(0).value();
        };
    }

    /** An absent actual means "nothing recorded" and must never become a zero on the way out. */
    private static GoalProgressView progressOf(WellnessGoal goal, BigDecimal actual, Window window) {
        return actual == null
                ? GoalProgressView.absent(goal, window.from(), window.to())
                : GoalProgressView.measured(goal, actual, window.from(), window.to());
    }

    /* -------------------------------------------------------------------- data rights */

    /** Called by account and child erasure (D-028). A target is the subject's own data. */
    @Transactional
    public void eraseAllForMember(UUID subjectMemberId) {
        goals.deleteByHouseholdMemberId(subjectMemberId);
    }

    /** Every goal a member has had, for their export. Authorized by the export orchestrator. */
    @Transactional(readOnly = true)
    public List<GoalView> exportForMember(UUID subjectMemberId) {
        return goals.findByHouseholdMemberIdOrderByStartedOnDesc(subjectMemberId).stream()
                .map(GoalView::from)
                .toList();
    }

    /* ------------------------------------------------------------------ internals */

    /**
     * A whole number of local days as an instant range.
     *
     * @param to end of the last day, not the start of the next one: the observation query
     *           filters with BETWEEN, which is inclusive at both ends, so a midnight boundary
     *           would count one reading in both the day it belongs to and the day after.
     */
    private record Window(Instant from, Instant to) {

        static Window of(LocalDate firstDay, LocalDate lastDay, ZoneId zone) {
            return new Window(
                    firstDay.atStartOfDay(zone).toInstant(),
                    lastDay.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1));
        }
    }

    private static AccessScope scopeOf(MetricCode metric) {
        return metric.category().scope();
    }

    private static Set<AccessScope> scopesOf(List<WellnessGoal> goalList) {
        Set<AccessScope> scopes = EnumSet.noneOf(AccessScope.class);
        goalList.forEach(g -> scopes.add(scopeOf(g.getMetricCode())));
        return scopes;
    }

    /** D-053: the subject's household decides where a day starts. */
    private ZoneId zoneOf(MemberInfo subject) {
        return households.findHousehold(subject.householdId())
                .map(HouseholdInfo::zone)
                .orElse(ZoneId.of("Asia/Kolkata"));
    }

    private LocalDate today(MemberInfo subject) {
        return LocalDate.now(clock.withZone(zoneOf(subject)));
    }

    /**
     * Filed under the same action as a refused observation on purpose: finding every attempt to
     * track a child's weight — or give a child a calorie target — must be one query.
     */
    private void refuseForAge(UUID callerAccountId, MemberInfo subject, String reason) {
        audit.deniedForSubject(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE, RESOURCE_TYPE_GOAL,
                subject.id(), callerAccountId, subject.householdId(), subject.id(), reason);
    }

    private void auditWrite(UUID callerAccountId, AccessDecision decision, AuditAction action,
                            UUID goalId, AccessScope scope) {
        Map<String, Object> metadata = Map.of(
                "scope", scope.name(), "relationship", decision.relationship().name());
        if (decision.isChildSubject()) {
            audit.successForSubject(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_GOAL, goalId,
                    callerAccountId, decision.householdId(), decision.subjectId(), metadata);
        }
        audit.successForSubject(action, RESOURCE_TYPE_GOAL, goalId,
                callerAccountId, decision.householdId(), decision.subjectId(), metadata);
    }

    /**
     * Structural validation only. A target of 600 g of protein is not refused here: calling a
     * self-set number implausible is an interpretation, and interpretation needs a citable
     * standard (D-037). The column CHECK and this method agree deliberately.
     */
    private static void requireValidTarget(CreateGoalCommand command) {
        if (command.memberId() == null || command.metricCode() == null
                || command.direction() == null || command.period() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.targetValue() == null || command.targetValue().signum() < 0) {
            throw AppException.validation("targetValue", "must be present and not negative");
        }
    }
}
