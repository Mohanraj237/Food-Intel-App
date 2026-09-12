package com.familyfood.goals.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.goals.domain.GoalMetricAggregation;
import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.goals.infra.WellnessGoalRepository;
import com.familyfood.household.application.HouseholdAccess;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Targets a member sets for themselves, and what their own logged data says about them.
 *
 * <h2>The safety rule this module exists to inherit</h2>
 * A goal names a {@link MetricCode}, so a goal is a standing intention to track that metric.
 * Every goal therefore passes
 * {@link WellnessAccess#isMetricPermittedForSubject(UUID, UUID, MetricCode)} before it is
 * created — the same call that refuses a weight <em>observation</em> for a child (D-036). That
 * is what makes a weight-loss goal for a child structurally impossible rather than merely
 * absent from the UI, and it is why there is no age logic of any kind in this file. A second
 * implementation of the age rules would be a second thing to keep in step, and the one that
 * drifted would be the one nobody was watching.
 *
 * <h2>Progress is arithmetic, not assessment</h2>
 * Progress reports the measured amount, the target, the direction and the window. It does not
 * report a score, a percentage, a streak or a verdict. Two disciplines follow:
 *
 * <ul>
 *   <li><b>Absent data is reported as absent.</b> An unlogged day is not a zero (see
 *       {@link GoalProgressView}).
 *   <li><b>The window is reduced the way the metric allows.</b> Water accumulates; weight does
 *       not (see {@link GoalMetricAggregation}).
 * </ul>
 *
 * <p>Authorization is the same model as observations and grants nothing to a household role:
 * self, or guardian of a child member (D-038). {@code OWNER} is not a health-data role.
 */
@Service
public class GoalService {

    private static final String RESOURCE_TYPE_GOAL = "WELLNESS_GOAL";
    private static final String CHILD_MEMBER_TYPE = "CHILD";

    private final WellnessGoalRepository goals;
    private final WellnessAccess wellness;
    private final HouseholdAccess households;
    private final AuditService audit;
    private final Clock clock;

    public GoalService(WellnessGoalRepository goals,
                       WellnessAccess wellness,
                       HouseholdAccess households,
                       AuditService audit,
                       Clock clock) {
        this.goals = goals;
        this.wellness = wellness;
        this.households = households;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------- lifecycle */

    @Transactional
    public GoalView createGoal(UUID callerAccountId, CreateGoalCommand command) {
        MemberInfo subject = requireMember(command.memberId());
        requireSelfOrGuardian(callerAccountId, subject);
        requireValidTarget(command);

        // The one line that matters in this class. Not "is the caller allowed" — that was
        // settled above — but "may this metric be tracked for this subject at all", answered
        // by the same code that answers it for a manual reading.
        if (!wellness.isMetricPermittedForSubject(callerAccountId, subject.id(), command.metricCode())) {
            // Filed under the same audit action as a refused observation on purpose: finding
            // every attempt to track a child's weight must be one query, not a union over
            // however many features happen to touch a metric.
            audit.denied(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE, RESOURCE_TYPE_GOAL,
                    subject.id(), callerAccountId, subject.householdId(),
                    "goal on metric " + command.metricCode() + " not permitted for this subject's age band");
            throw new AppException(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        }

        // Checked before the insert so the caller gets a 409 with a message rather than the
        // partial unique index surfacing as a 500. The index remains the authority.
        if (goals.findByHouseholdMemberIdAndMetricCodeAndActiveTrue(subject.id(), command.metricCode())
                .isPresent()) {
            throw new AppException(ErrorCode.GOAL_ALREADY_ACTIVE);
        }

        Instant now = clock.instant();
        WellnessGoal saved;
        try {
            saved = goals.save(WellnessGoal.start(
                    subject.id(), command.metricCode(), command.direction(), command.targetValue(),
                    command.period(), LocalDate.now(clock), callerAccountId, now));
        } catch (DataIntegrityViolationException e) {
            // Two creates racing past the check above. The loser sees the same conflict it
            // would have seen a millisecond earlier.
            throw new AppException(ErrorCode.GOAL_ALREADY_ACTIVE, Map.of(), e);
        }

        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_GOAL,
                    saved.getId(), callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.GOAL_CREATED, RESOURCE_TYPE_GOAL,
                saved.getId(), callerAccountId, subject.householdId());

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
        MemberInfo subject = requireMember(goal.getHouseholdMemberId());
        requireSelfOrGuardian(callerAccountId, subject);

        goal.end(LocalDate.now(clock));
        WellnessGoal saved = goals.save(goal);

        audit.success(AuditAction.GOAL_ENDED, RESOURCE_TYPE_GOAL,
                saved.getId(), callerAccountId, subject.householdId());

        return GoalView.from(saved);
    }

    /* ---------------------------------------------------------------------- reading */

    @Transactional(readOnly = true)
    public List<GoalView> listGoals(UUID callerAccountId, UUID memberId, boolean includeEnded) {
        MemberInfo subject = requireMember(memberId);
        requireSelfOrGuardian(callerAccountId, subject);
        auditCrossMemberRead(callerAccountId, subject);

        List<WellnessGoal> found = includeEnded
                ? goals.findByHouseholdMemberIdOrderByStartedOnDesc(memberId)
                : goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(memberId);

        return found.stream().map(GoalView::from).toList();
    }

    /**
     * Actual against target for every live goal.
     *
     * <p>{@code from} and {@code to} are optional. When omitted, each goal supplies its own
     * window from its period — a daily goal means today, a weekly goal the last seven days —
     * which is the reading a member expects without having to ask for one. When supplied, that
     * range is used for every goal regardless of period, and the window is echoed back in each
     * result either way, because a total with an unstated window is not checkable.
     *
     * <p>Reads go through {@link WellnessAccess}, so the D-038 visibility rule and the
     * cross-member read audit apply to the observations themselves without this module
     * restating either.
     */
    @Transactional(readOnly = true)
    public List<GoalProgressView> goalProgress(UUID callerAccountId,
                                               UUID memberId,
                                               LocalDate from,
                                               LocalDate to) {
        MemberInfo subject = requireMember(memberId);
        requireSelfOrGuardian(callerAccountId, subject);

        List<WellnessGoal> active = goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(memberId);
        LocalDate lastDay = to == null ? LocalDate.now(clock) : to;

        // Goals commonly share a window, and every distinct window is a query. Memoised per
        // call so a member with six daily goals reads the window once rather than six times.
        Map<Window, Map<MetricCode, BigDecimal>> sums = new HashMap<>();
        Map<Window, Map<MetricCode, ObservationView>> latest = new HashMap<>();

        List<GoalProgressView> progress = new ArrayList<>(active.size());
        for (WellnessGoal goal : active) {
            LocalDate firstDay = from == null
                    ? lastDay.minusDays(goal.getPeriod().days() - 1L)
                    : from;
            Window window = Window.of(firstDay, lastDay, clock);

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

            // An absent key means "nothing recorded" (see WellnessAccess.sumByMetric) and must
            // never become a zero on the way out of here.
            progress.add(actual == null
                    ? GoalProgressView.absent(goal, window.from(), window.to())
                    : GoalProgressView.measured(goal, actual, window.from(), window.to()));
        }
        return progress;
    }

    /* -------------------------------------------------------------------- erasure */

    /** Called by account erasure (D-028). A target is the subject's own data. */
    @Transactional
    public void eraseAllForMember(UUID subjectMemberId) {
        goals.deleteByHouseholdMemberId(subjectMemberId);
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

        static Window of(LocalDate firstDay, LocalDate lastDay, Clock clock) {
            return new Window(
                    firstDay.atStartOfDay(clock.getZone()).toInstant(),
                    lastDay.plusDays(1).atStartOfDay(clock.getZone()).toInstant().minusNanos(1));
        }
    }

    private MemberInfo requireMember(UUID memberId) {
        return households.findMember(memberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private boolean isSelf(UUID callerAccountId, MemberInfo subject) {
        return callerAccountId.equals(subject.userAccountId());
    }

    /**
     * Who may set or read a goal: the subject, or a guardian of a child subject. Not another
     * adult in the household, however senior — an adult is their own data principal (D-002),
     * and household role grants nothing here (D-038).
     */
    private void requireSelfOrGuardian(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return;
        }
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())
                && households.isGuardianOf(callerAccountId, subject.id())) {
            return;
        }
        audit.denied(AuditAction.HEALTH_DATA_ACCESS_DENIED, RESOURCE_TYPE_GOAL,
                subject.id(), callerAccountId, subject.householdId(),
                "caller is neither the subject nor a guardian of this subject");
        throw new AppException(ErrorCode.ACCESS_DENIED);
    }

    /** Reading someone else's goals leaves a trace, as reading their observations does. */
    private void auditCrossMemberRead(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return;
        }
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_GOAL,
                    subject.id(), callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.HEALTH_DATA_ACCESSED, RESOURCE_TYPE_GOAL,
                subject.id(), callerAccountId, subject.householdId());
    }

    /**
     * Structural validation only. A target of 600 g of protein is not refused here: calling a
     * self-set number implausible is an interpretation, and interpretation needs a citable
     * standard (D-037). The column CHECK and this method agree deliberately.
     */
    private void requireValidTarget(CreateGoalCommand command) {
        if (command.metricCode() == null || command.direction() == null || command.period() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.targetValue() == null || command.targetValue().signum() < 0) {
            throw AppException.validation("targetValue", "must be present and not negative");
        }
    }
}
