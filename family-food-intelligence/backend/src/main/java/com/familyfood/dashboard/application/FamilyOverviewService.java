package com.familyfood.dashboard.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.domain.Relationship;
import com.familyfood.dashboard.application.FamilyOverviewView.MemberCard;
import com.familyfood.experience.application.ExperienceService;
import com.familyfood.goals.application.GoalProgressView;
import com.familyfood.goals.application.GoalService;
import com.familyfood.habits.application.HabitService;
import com.familyfood.habits.application.HabitService.DaySummary;
import com.familyfood.habits.application.HabitService.HabitDay;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.HouseholdInfo;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.nutrition.application.MealService;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Family lens (D-043): every live member of a household, each seen through the one access
 * policy (D-044), for one household-local day and the week ending on it (D-053).
 *
 * <p>For each member the caller's relationship is decided once. NONE yields an identity card
 * and nothing else — no metrics, no habits, no consent state — because another household member
 * sees nothing of an adult who has not shared (D-002, D-038). Anything else yields that member's
 * week restricted to the scopes the caller holds: <b>one</b> observation read per member, filtered
 * and audited by wellness, from which the day, the week and goal progress are all derived; habits
 * only if HABITS is visible. Showing a week costs the person's access log nothing more than
 * showing a day did.
 *
 * <p>The household summary (D-054) is computed from the finished cards alone — see
 * {@link FamilySummaries} — so it can never contain a number the caller could not already read on
 * someone's card. What this deliberately never computes: a comparison, a rank or a "best" (D-051).
 */
@Service
public class FamilyOverviewService {

    private static final String RESOURCE_TYPE_HABIT = "HABIT";

    /** The overview's week: the day asked for and the six before it. */
    static final int WEEK_DAYS = 7;

    private final HealthAccessPolicy access;
    private final HouseholdAccess households;
    private final WellnessAccess wellness;
    private final GoalService goals;
    private final HabitService habits;
    private final MealService meals;
    private final ExperienceService experiences;
    private final TrackingConsent trackingConsent;
    private final Clock clock;

    public FamilyOverviewService(HealthAccessPolicy access,
                                 HouseholdAccess households,
                                 WellnessAccess wellness,
                                 GoalService goals,
                                 HabitService habits,
                                 MealService meals,
                                 ExperienceService experiences,
                                 TrackingConsent trackingConsent,
                                 Clock clock) {
        this.access = access;
        this.households = households;
        this.wellness = wellness;
        this.goals = goals;
        this.habits = habits;
        this.meals = meals;
        this.experiences = experiences;
        this.trackingConsent = trackingConsent;
        this.clock = clock;
    }

    /**
     * @param dateOrNull a household-local date; {@code null} for today there. A future date is a
     *                   400.
     */
    @Transactional(readOnly = true)
    public FamilyOverviewView overview(UUID callerAccountId, UUID householdId, LocalDate dateOrNull) {
        // Membership first, and the same answer whether the household exists or not: a household
        // id is not a public fact.
        MemberInfo caller = households.findActiveMembership(householdId, callerAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER));
        HouseholdInfo household = households.findHousehold(householdId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER));

        ZoneId zone = household.zone() == null ? DashboardDays.DEFAULT_ZONE : household.zone();
        LocalDate date = DashboardDays.resolveDate(dateOrNull, zone, clock);
        Week week = new Week(date, zone, DashboardDays.daysEndingOn(date, WEEK_DAYS));

        List<Ranked> cards = new ArrayList<>();
        for (MemberSummary member : households.listActiveMemberSummaries(householdId)) {
            AccessDecision decision = access.decide(callerAccountId, member.id());
            cards.add(new Ranked(member, decision, cardFor(callerAccountId, member, decision, week)));
        }
        cards.sort(ORDER);
        List<MemberCard> ordered = cards.stream().map(Ranked::card).toList();

        int awaiting = meals.countAwaitingConfirmation(callerAccountId, caller.id(),
                clock.instant().minus(TodayService.AWAITING_WINDOW));

        return new FamilyOverviewView(household.id(), household.name(), household.individual(),
                zone.getId(), date, week.days(), awaiting, FamilySummaries.summarise(ordered, week.days()),
                ordered);
    }

    /* ------------------------------------------------------------------ internals */

    private MemberCard cardFor(UUID callerAccountId, MemberSummary member, AccessDecision decision, Week week) {
        boolean canLogNutrition = wellness.mayDeriveFor(callerAccountId, member.id(), MetricCategory.NUTRITION);

        if (decision.levels().isEmpty()) {
            // Identity only: what the household member list already shows, and nothing more.
            return new MemberCard(member.id(), member.displayLabel(), member.memberType(), false,
                    decision.relationship().name(), experiences.visibleTier(decision), List.of(),
                    List.of(), null, null, canLogNutrition, List.of(), List.of(), List.of(), null);
        }

        // One read of the whole week: wellness restricts it to the visible categories and audits it
        // once, exactly as a single day's read did. The day, the week and every goal come out of it.
        List<ObservationView> observations = wellness.window(callerAccountId, member.id(),
                DashboardDays.startOf(week.first(), week.zone()), DashboardDays.endOf(week.date(), week.zone()));
        List<MetricDay> today = DashboardDays.reduce(DashboardDays.onDay(observations, week.date(), week.zone()));
        List<MetricWeek> metricWeek = DashboardDays.weekSeries(observations, week.days(), week.zone());
        List<GoalProgressView> goalProgress = DashboardDays.seesAnyMetric(decision)
                ? goals.progressFrom(decision, week.date(), week.zone(), observations)
                : List.of();

        DaySummary habitDay = null;
        List<HabitDay> habitWeek = null;
        if (decision.canView(AccessScope.HABITS)) {
            habitWeek = habits.daySummaries(decision, week.first(), week.date(), week.zone());
            HabitDay last = habitWeek.get(habitWeek.size() - 1);
            habitDay = new DaySummary(last.active(), last.due(), last.done());
            access.auditRead(callerAccountId, decision, List.of(AccessScope.HABITS), RESOURCE_TYPE_HABIT);
        }

        Relationship r = decision.relationship();
        Boolean consent = (r == Relationship.SELF || r == Relationship.GUARDIAN)
                ? trackingConsent.isActive(decision.subject())
                : null;

        return new MemberCard(member.id(), member.displayLabel(), member.memberType(), decision.isSelf(),
                r.name(), experiences.visibleTier(decision), DashboardDays.scopeNames(decision),
                today, habitDay, consent, canLogNutrition, DashboardDays.manageScopeNames(decision),
                metricWeek, goalProgress, habitWeek);
    }

    /** The day the overview is for, where the household lives, and the week ending on it. */
    private record Week(LocalDate date, ZoneId zone, List<LocalDate> days) {

        LocalDate first() {
            return days.get(0);
        }
    }

    /** A card with what it is sorted by — who the member is to the caller, never what they did. */
    private record Ranked(MemberSummary member, AccessDecision decision, MemberCard card) {

        /** 0: you. 1: a child you guard. 2: everyone else. */
        int group() {
            if (decision.isSelf()) {
                return 0;
            }
            return decision.relationship() == Relationship.GUARDIAN ? 1 : 2;
        }
    }

    /** You, then the children you guard, then everyone else — each group in the order they joined. */
    private static final Comparator<Ranked> ORDER = Comparator
            .comparingInt(Ranked::group)
            .thenComparing((Ranked r) -> r.member().joinedAt(),
                    Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing((Ranked r) -> r.member().id());
}
