package com.familyfood.dashboard.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.domain.Relationship;
import com.familyfood.goals.application.GoalProgressView;
import com.familyfood.goals.application.GoalService;
import com.familyfood.habits.application.HabitService;
import com.familyfood.habits.application.HabitService.DaySummary;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.nutrition.application.MealService;
import com.familyfood.wellness.application.WellnessAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * "How is this person's day going?" — the Today screen (architecture §8, D-053).
 *
 * <p>A read-only composition that owns no data and decides no access. The one policy (D-044)
 * decides what the caller may see; wellness filters observations to those categories and audits
 * the read; goals, habits and meals answer for their own data. What this class adds is the
 * day — the household's day, midnight to midnight where the family lives — and the reduction of
 * that day to one honest number per metric (see {@link MetricDay}).
 *
 * <p>A caller who may see nothing about the member is refused with an audited 403, never shown
 * an empty day: "not allowed" must not masquerade as "nothing logged".
 */
@Service
public class TodayService {

    static final String RESOURCE_TYPE_TODAY = "TODAY_SUMMARY";
    private static final String RESOURCE_TYPE_HABIT = "HABIT";

    /** How far back an unconfirmed share still counts as waiting — two weeks of meals. */
    static final Duration AWAITING_WINDOW = Duration.ofDays(14);

    private final HealthAccessPolicy access;
    private final HouseholdAccess households;
    private final WellnessAccess wellness;
    private final GoalService goals;
    private final HabitService habits;
    private final MealService meals;
    private final TrackingConsent trackingConsent;
    private final Clock clock;

    public TodayService(HealthAccessPolicy access,
                        HouseholdAccess households,
                        WellnessAccess wellness,
                        GoalService goals,
                        HabitService habits,
                        MealService meals,
                        TrackingConsent trackingConsent,
                        Clock clock) {
        this.access = access;
        this.households = households;
        this.wellness = wellness;
        this.goals = goals;
        this.habits = habits;
        this.meals = meals;
        this.trackingConsent = trackingConsent;
        this.clock = clock;
    }

    /**
     * @param dateOrNull a household-local date; {@code null} for today there. A future date is a
     *                   400: a day that has not happened has nothing in it.
     */
    @Transactional(readOnly = true)
    public TodayView today(UUID callerAccountId, UUID memberId, LocalDate dateOrNull) {
        AccessDecision decision = access.decide(callerAccountId, memberId);
        if (decision.levels().isEmpty()) {
            // Asked again through the policy only for its audited refusal; it throws.
            access.requireAnyView(callerAccountId, memberId, RESOURCE_TYPE_TODAY);
        }

        ZoneId zone = DashboardDays.zoneOf(households.findHousehold(decision.householdId()));
        LocalDate date = DashboardDays.resolveDate(dateOrNull, zone, clock);

        // One read of the day: wellness restricts it to the visible categories and audits it once.
        List<MetricDay> metrics = DashboardDays.reduce(wellness.window(callerAccountId, memberId,
                DashboardDays.startOf(date, zone), DashboardDays.endOf(date, zone)));

        List<GoalProgressView> goalProgress = DashboardDays.seesAnyMetric(decision)
                ? goals.goalProgress(callerAccountId, memberId, null, date)
                : List.of();

        DaySummary habitDay = null;
        if (decision.canView(AccessScope.HABITS)) {
            habitDay = habits.daySummary(decision, date);
            access.auditRead(callerAccountId, decision, List.of(AccessScope.HABITS), RESOURCE_TYPE_HABIT);
        }

        int awaiting = meals.countAwaitingConfirmation(callerAccountId, memberId,
                clock.instant().minus(AWAITING_WINDOW));

        return new TodayView(
                memberId,
                households.findMemberSummary(memberId).map(MemberSummary::displayLabel).orElse(null),
                date,
                zone.getId(),
                decision.relationship().name(),
                DashboardDays.scopeNames(decision),
                metrics,
                goalProgress,
                habitDay,
                awaiting,
                consentStateFor(decision));
    }

    /**
     * Whether tracking is on is the member's own consent record: shown to the member and, for a
     * child, to their guardian — the two people who can change it — and to nobody else.
     */
    private Boolean consentStateFor(AccessDecision decision) {
        Relationship r = decision.relationship();
        if (r == Relationship.SELF || r == Relationship.GUARDIAN) {
            return trackingConsent.isActive(decision.subject());
        }
        return null;
    }
}
