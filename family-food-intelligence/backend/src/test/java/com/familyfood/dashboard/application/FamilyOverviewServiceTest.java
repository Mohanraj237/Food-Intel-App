package com.familyfood.dashboard.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.dashboard.application.FamilyOverviewView.MemberCard;
import com.familyfood.experience.application.ExperienceService;
import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.goals.application.GoalProgressView;
import com.familyfood.goals.application.GoalService;
import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalPeriod;
import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.goals.infra.WellnessGoalRepository;
import com.familyfood.habits.application.HabitService;
import com.familyfood.habits.application.HabitService.DaySummary;
import com.familyfood.habits.application.HabitService.HabitDay;
import com.familyfood.habits.domain.Habit;
import com.familyfood.habits.domain.HabitCadence;
import com.familyfood.habits.domain.HabitCompletion;
import com.familyfood.habits.infra.HabitCompletionRepository;
import com.familyfood.habits.infra.HabitRepository;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.nutrition.application.MealService;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The family overview (D-043, D-053, D-054): one card per live member, each only as far as the
 * caller may see them, from ONE observation read per member.
 *
 * <p>The REAL access policy runs (through {@link HealthAccessFixture}, spied so the tests can see
 * which audits were asked for), with a real {@link GoalService} and {@link HabitService} over
 * mocked repositories, spied so the tests can prove which of their methods the overview used.
 * Wellness is faked by {@link #visibleWindow}: it answers a window read the way wellness does —
 * only the subject's rows in categories the policy lets the caller see — so a card can hold
 * nothing the policy would not release.
 */
class FamilyOverviewServiceTest {

    /** 15:30 in India on Saturday 12 September 2026. */
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 12);
    private static final ZoneId IST = HealthAccessFixture.HOUSEHOLD_ZONE;
    /** Midnight in India starting Sunday 6 September, and the last instant of Saturday the 12th. */
    private static final Instant WEEK_FROM = Instant.parse("2026-09-05T18:30:00Z");
    private static final Instant WEEK_TO = Instant.parse("2026-09-12T18:30:00Z").minusNanos(1);

    private static final List<String> EVERY_SCOPE = Arrays.stream(AccessScope.values())
            .map(AccessScope::name).sorted().toList();

    private final UUID household = UUID.randomUUID();
    private final UUID meAccount = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID otherAccount = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID child = UUID.randomUUID();

    private HealthAccessFixture fixture;
    private HealthAccessPolicy policy;
    private WellnessAccess wellness;
    private WellnessGoalRepository goalRepository;
    private GoalService goals;
    private HabitRepository habitRepository;
    private HabitCompletionRepository completionRepository;
    private HabitService habits;
    private MealService meals;
    private FamilyOverviewService service;

    private final Map<UUID, List<ObservationView>> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        fixture = new HealthAccessFixture(NOW);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        policy = spy(fixture.policy);

        wellness = mock(WellnessAccess.class);
        when(wellness.window(any(), any(), any(), any())).thenAnswer(inv -> visibleWindow(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));

        goalRepository = mock(WellnessGoalRepository.class);
        goals = spy(new GoalService(goalRepository, wellness, fixture.policy, fixture.trackingConsent,
                fixture.households, fixture.audit, clock));

        habitRepository = mock(HabitRepository.class);
        completionRepository = mock(HabitCompletionRepository.class);
        habits = spy(new HabitService(habitRepository, completionRepository, fixture.policy,
                fixture.trackingConsent, fixture.households, fixture.audit, clock));

        meals = mock(MealService.class);
        ExperienceService experiences = mock(ExperienceService.class);
        when(experiences.visibleTier(any())).thenAnswer(inv ->
                inv.<AccessDecision>getArgument(0).isChildSubject() ? ExperienceTier.CHILD : ExperienceTier.ADULT);

        service = new FamilyOverviewService(policy, fixture.households, wellness, goals, habits, meals,
                experiences, fixture.trackingConsent, clock);
    }

    /* ------------------------------------------------------------------ helpers */

    /** What wellness returns for a window: the subject's rows in the caller's visible categories, newest first. */
    private List<ObservationView> visibleWindow(UUID caller, UUID subject, Instant from, Instant to) {
        AccessDecision decision = fixture.policy.decide(caller, subject);
        return stored.getOrDefault(subject, List.of()).stream()
                .filter(o -> decision.canView(o.metricCategory().scope()))
                .filter(o -> !o.effectiveAt().isBefore(from) && !o.effectiveAt().isAfter(to))
                .sorted(Comparator.comparing(ObservationView::effectiveAt).reversed())
                .toList();
    }

    private void observation(UUID member, MetricCode code, String value, Instant effectiveAt) {
        stored.computeIfAbsent(member, k -> new ArrayList<>()).add(new ObservationView(UUID.randomUUID(), member,
                code, code.category(), new BigDecimal(value), code.canonicalUnit(), effectiveAt, effectiveAt,
                ObservationSource.MANUAL));
    }

    private static Instant ist(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(IST).toInstant();
    }

    private static MemberCard cardOf(FamilyOverviewView view, UUID memberId) {
        return view.members().stream().filter(c -> c.memberId().equals(memberId)).findFirst()
                .orElseThrow(() -> new AssertionError("no card for " + memberId));
    }

    private static AccessDecision forSubject(UUID memberId) {
        return argThat(d -> d != null && d.subjectId().equals(memberId));
    }

    private static void assertIdentityOnly(MemberCard card) {
        assertThat(card.relationship()).isEqualTo("NONE");
        assertThat(card.isYou()).isFalse();
        assertThat(card.visibleScopes()).isEmpty();
        assertThat(card.manageScopes()).isEmpty();
        assertThat(card.today()).isEmpty();
        assertThat(card.week()).isEmpty();
        assertThat(card.goals()).isEmpty();
        assertThat(card.habits()).isNull();
        assertThat(card.habitsWeek()).isNull();
        assertThat(card.trackingConsentActive()).isNull();
    }

    /* --------------------------------------------------------- NONE: identity only */

    @Test
    @DisplayName("a member who shares nothing gets an identity card, and their observations are never read")
    void noneMemberIsIdentityOnlyAndNeverRead() {
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherAccount, other);
        observation(other, MetricCode.STEPS, "5000", ist(TODAY, 9));
        observation(other, MetricCode.WATER_ML, "500", ist(TODAY, 9));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        MemberCard card = cardOf(view, other);
        assertIdentityOnly(card);
        assertThat(card.displayLabel()).isEqualTo(fixture.labelOf(other));
        assertThat(card.memberType()).isEqualTo("ADULT");
        assertThat(card.experience()).isEqualTo(ExperienceTier.ADULT);

        verify(wellness, never()).window(any(), eq(other), any(), any());
        verify(goals, never()).progressFrom(forSubject(other), any(), any(), any());
        verify(goalRepository, never()).findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(other);
        verify(habits, never()).daySummaries(forSubject(other), any(), any(), any());
        verify(policy, never()).auditRead(any(), forSubject(other), any(), any());
    }

    @Test
    @DisplayName("an adult who shared but withdrew tracking consent is back to an identity card (D-048)")
    void withdrawnConsentLeavesOnlyTheIdentityCard() {
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherAccount, other);
        fixture.grant(other, me, AccessScope.ACTIVITY, AccessLevel.VIEW);
        fixture.grant(other, me, AccessScope.HABITS, AccessLevel.VIEW);
        fixture.withdrawConsent(other);
        observation(other, MetricCode.STEPS, "5000", ist(TODAY, 9));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        assertIdentityOnly(cardOf(view, other));
        verify(wellness, never()).window(any(), eq(other), any(), any());
        assertThat(view.summary()).isNull();
    }

    /* ------------------------------------------------------- one read per member */

    @Test
    @DisplayName("exactly one window read per member with anything visible, spanning the household's week")
    void oneWindowReadPerVisibleMember() {
        UUID strangerAccount = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        fixture.adult(household, meAccount, me);
        fixture.child(household, child, meAccount);
        fixture.adult(household, otherAccount, other);
        fixture.adult(household, strangerAccount, stranger);
        fixture.grant(other, me, AccessScope.ACTIVITY, AccessLevel.VIEW);

        service.overview(meAccount, household, TODAY);

        // The week in India: local midnight six days back, to the last instant of the day asked for.
        assertThat(DashboardDays.startOf(TODAY.minusDays(6), IST)).isEqualTo(WEEK_FROM);
        assertThat(DashboardDays.endOf(TODAY, IST)).isEqualTo(WEEK_TO);

        verify(wellness, times(1)).window(meAccount, me, WEEK_FROM, WEEK_TO);
        verify(wellness, times(1)).window(meAccount, child, WEEK_FROM, WEEK_TO);
        verify(wellness, times(1)).window(meAccount, other, WEEK_FROM, WEEK_TO);
        verify(wellness, never()).window(any(), eq(stranger), any(), any());
        verify(wellness, times(3)).window(any(), any(), any(), any());
        // Nothing else reads observations: no per-goal sums, no latest-reading lookups.
        verify(wellness, never()).sumByMetric(any(), any(), any(), any());
        verify(wellness, never()).latestByMetric(any(), any(), any(), any());

        // Habits: one audited read each for the two whose habits are visible (me and my child).
        verify(policy, times(2)).auditRead(eq(meAccount), any(), eq(List.of(AccessScope.HABITS)), eq("HABIT"));
        verify(policy).auditRead(eq(meAccount), forSubject(child), eq(List.of(AccessScope.HABITS)), eq("HABIT"));
        verify(habits, never()).daySummaries(forSubject(other), any(), any(), any());
        verify(fixture.audit, times(1)).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), eq("HABIT"),
                eq(child), eq(meAccount), eq(household), eq(child), anyMap());
    }

    @Test
    @DisplayName("the day and the week are the household's own: today there, bounded by its midnights")
    void weekFollowsTheHouseholdZone() {
        // 10:00 UTC is already midnight starting Sunday 13 September at UTC+14.
        ZoneId kiritimati = ZoneId.of("Pacific/Kiritimati");
        fixture.zone(household, kiritimati);
        fixture.adult(household, meAccount, me);
        LocalDate thereToday = LocalDate.of(2026, 9, 13);

        FamilyOverviewView view = service.overview(meAccount, household, null);

        assertThat(view.timeZone()).isEqualTo("Pacific/Kiritimati");
        assertThat(view.date()).isEqualTo(thereToday);
        assertThat(view.week()).containsExactlyElementsOf(thereToday.minusDays(6).datesUntil(thereToday.plusDays(1)).toList());
        verify(wellness).window(meAccount, me,
                Instant.parse("2026-09-06T10:00:00Z"), Instant.parse("2026-09-13T10:00:00Z").minusNanos(1));
        verify(habits).daySummaries(forSubject(me), eq(thereToday.minusDays(6)), eq(thereToday), eq(kiritimati));
        verify(goals).progressFrom(forSubject(me), eq(thereToday), eq(kiritimati), any());
    }

    /* ------------------------------------------------------------------- goals */

    @Test
    @DisplayName("goal progress comes from the same read (progressFrom), never from goalProgress")
    void goalsComeFromTheSameRead() {
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherAccount, other);
        fixture.grant(other, me, AccessScope.ACTIVITY, AccessLevel.VIEW);
        WellnessGoal myWater = WellnessGoal.start(me, MetricCode.WATER_ML, GoalDirection.AT_LEAST,
                new BigDecimal("2000"), GoalPeriod.DAILY, TODAY.minusDays(10), meAccount, NOW);
        WellnessGoal theirSteps = WellnessGoal.start(other, MetricCode.STEPS, GoalDirection.AT_LEAST,
                new BigDecimal("50000"), GoalPeriod.WEEKLY, TODAY.minusDays(10), otherAccount, NOW);
        WellnessGoal theirWeight = WellnessGoal.start(other, MetricCode.WEIGHT_KG, GoalDirection.AT_MOST,
                new BigDecimal("70"), GoalPeriod.WEEKLY, TODAY.minusDays(10), otherAccount, NOW);
        when(goalRepository.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(me)).thenReturn(List.of(myWater));
        when(goalRepository.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(other))
                .thenReturn(List.of(theirSteps, theirWeight));
        observation(me, MetricCode.WATER_ML, "500", ist(TODAY, 8));
        observation(me, MetricCode.WATER_ML, "750", ist(TODAY, 13));
        observation(me, MetricCode.WATER_ML, "900", ist(TODAY.minusDays(1), 20));
        observation(other, MetricCode.STEPS, "4000", ist(TODAY.minusDays(6), 9));
        observation(other, MetricCode.STEPS, "6000", ist(TODAY, 9));
        observation(other, MetricCode.STEPS, "9999", ist(TODAY.minusDays(7), 9));
        observation(other, MetricCode.WEIGHT_KG, "72", ist(TODAY, 7));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        // Computed before verify(): the fake reads the fixture's mocks, which must not happen
        // while argument matchers are being recorded.
        List<ObservationView> myWeek = visibleWindow(meAccount, me, WEEK_FROM, WEEK_TO);
        List<ObservationView> theirWeek = visibleWindow(meAccount, other, WEEK_FROM, WEEK_TO);
        assertThat(theirWeek).extracting(ObservationView::metricCode).containsOnly(MetricCode.STEPS);
        verify(goals, never()).goalProgress(any(), any(), any(), any());
        verify(goals).progressFrom(forSubject(me), eq(TODAY), eq(IST), eq(myWeek));
        verify(goals).progressFrom(forSubject(other), eq(TODAY), eq(IST), eq(theirWeek));

        GoalProgressView water = cardOf(view, me).goals().get(0);
        assertThat(water.metricCode()).isEqualTo(MetricCode.WATER_ML);
        assertThat(water.actual()).isEqualByComparingTo("1250");
        assertThat(water.windowFrom()).isEqualTo(DashboardDays.startOf(TODAY, IST));
        assertThat(water.windowTo()).isEqualTo(DashboardDays.endOf(TODAY, IST));

        // Their weight goal is BODY data, which they did not share; steps are the week's total.
        List<GoalProgressView> theirs = cardOf(view, other).goals();
        assertThat(theirs).extracting(GoalProgressView::metricCode).containsExactly(MetricCode.STEPS);
        assertThat(theirs.get(0).actual()).isEqualByComparingTo("10000");
        assertThat(theirs.get(0).windowFrom()).isEqualTo(WEEK_FROM);
        assertThat(theirs.get(0).windowTo()).isEqualTo(WEEK_TO);
    }

    /* ------------------------------------------------------------- what is shown */

    @Test
    @DisplayName("SHARED and CAREGIVER cards hold only granted categories; scopes come from the decision")
    void cardsHoldOnlyWhatTheDecisionReleases() {
        UUID caredForAccount = UUID.randomUUID();
        UUID caredFor = UUID.randomUUID();
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherAccount, other);
        fixture.adult(household, caredForAccount, caredFor);
        fixture.grant(other, me, AccessScope.ACTIVITY, AccessLevel.VIEW);
        fixture.grant(other, me, AccessScope.SLEEP, AccessLevel.VIEW);
        fixture.grant(caredFor, me, AccessScope.HYDRATION, AccessLevel.MANAGE);
        fixture.grant(caredFor, me, AccessScope.ACTIVITY, AccessLevel.VIEW);
        for (UUID member : List.of(other, caredFor)) {
            observation(member, MetricCode.STEPS, "3000", ist(TODAY, 9));
            observation(member, MetricCode.WATER_ML, "400", ist(TODAY, 10));
            observation(member, MetricCode.WEIGHT_KG, "70", ist(TODAY, 7));
            observation(member, MetricCode.SLEEP_MINUTES, "400", ist(TODAY.minusDays(2), 6));
            observation(member, MetricCode.MOOD_SCORE, "3", ist(TODAY, 12));
            observation(member, MetricCode.SYSTOLIC_MMHG, "121", ist(TODAY, 8));
        }

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        MemberCard shared = cardOf(view, other);
        assertThat(shared.relationship()).isEqualTo("SHARED");
        assertThat(shared.visibleScopes()).containsExactly("ACTIVITY", "SLEEP")
                .isEqualTo(DashboardDays.scopeNames(fixture.policy.decide(meAccount, other)));
        assertThat(shared.manageScopes()).isEmpty();
        assertThat(shared.week()).extracting(MetricWeek::code)
                .containsExactly(MetricCode.STEPS, MetricCode.SLEEP_MINUTES);
        assertThat(shared.today()).extracting(MetricDay::code).containsExactly(MetricCode.STEPS);
        assertThat(shared.week()).allSatisfy(w -> assertThat(shared.visibleScopes()).contains(w.category().name()));
        assertThat(shared.habits()).isNull();
        assertThat(shared.habitsWeek()).isNull();
        assertThat(shared.trackingConsentActive()).isNull();
        assertThat(shared.experience()).isEqualTo(ExperienceTier.ADULT);

        MemberCard caregiver = cardOf(view, caredFor);
        assertThat(caregiver.relationship()).isEqualTo("CAREGIVER");
        assertThat(caregiver.visibleScopes()).containsExactly("ACTIVITY", "HYDRATION");
        assertThat(caregiver.manageScopes()).containsExactly("HYDRATION")
                .isEqualTo(DashboardDays.manageScopeNames(fixture.policy.decide(meAccount, caredFor)));
        assertThat(caregiver.week()).extracting(MetricWeek::code)
                .containsExactly(MetricCode.STEPS, MetricCode.WATER_ML);
        assertThat(caregiver.today()).extracting(MetricDay::code)
                .containsExactly(MetricCode.STEPS, MetricCode.WATER_ML);
        assertThat(caregiver.trackingConsentActive()).isNull();

        MemberCard mine = cardOf(view, me);
        assertThat(mine.isYou()).isTrue();
        assertThat(mine.visibleScopes()).isEqualTo(EVERY_SCOPE);
        assertThat(mine.manageScopes()).isEqualTo(EVERY_SCOPE);
        assertThat(mine.trackingConsentActive()).isTrue();
    }

    @Test
    @DisplayName("a card's week has every day of the overview, a gap on days with nothing, and today matches it")
    void weekRowsCoverTheWholeWeek() {
        fixture.adult(household, meAccount, me);
        observation(me, MetricCode.STEPS, "4000", ist(TODAY.minusDays(3), 9));
        observation(me, MetricCode.STEPS, "2500", ist(TODAY, 9));
        observation(me, MetricCode.STEPS, "500", ist(TODAY, 18));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        MemberCard mine = cardOf(view, me);
        MetricWeek steps = mine.week().get(0);
        assertThat(steps.days()).extracting(MetricWeek.DayPoint::date).containsExactlyElementsOf(view.week());
        assertThat(steps.days()).extracting(MetricWeek.DayPoint::value)
                .containsExactly(null, null, null, new BigDecimal("4000"), null, null, new BigDecimal("3000"));
        assertThat(steps.days()).extracting(MetricWeek.DayPoint::count).containsExactly(0, 0, 0, 1, 0, 0, 2);
        assertThat(mine.today()).singleElement().satisfies(day -> {
            assertThat(day.code()).isEqualTo(MetricCode.STEPS);
            assertThat(day.value()).isEqualByComparingTo("3000");
            assertThat(day.observationCount()).isEqualTo(2);
        });
    }

    /* -------------------------------------------------------------------- order */

    @Test
    @DisplayName("cards: you, then the children you guard, then everyone else — each group by when they joined")
    void cardOrder() {
        UUID otherAAccount = UUID.randomUUID();
        UUID otherA = UUID.randomUUID();
        UUID myFirstChild = UUID.randomUUID();
        UUID theirChild = UUID.randomUUID();
        UUID otherBAccount = UUID.randomUUID();
        UUID otherB = UUID.randomUUID();
        UUID mySecondChild = UUID.randomUUID();
        // The fixture's join order is the order members are added.
        fixture.adult(household, otherAAccount, otherA);
        fixture.child(household, myFirstChild, meAccount);
        fixture.child(household, theirChild, otherAAccount);
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherBAccount, otherB);
        fixture.child(household, mySecondChild, meAccount);
        fixture.grant(otherB, me, AccessScope.ACTIVITY, AccessLevel.VIEW);
        // The member list arrives in no useful order.
        List<MemberSummary> scrambled = new ArrayList<>(fixture.households.listActiveMemberSummaries(household));
        java.util.Collections.reverse(scrambled);
        when(fixture.households.listActiveMemberSummaries(household)).thenReturn(scrambled);

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        assertThat(view.members()).extracting(MemberCard::memberId)
                .containsExactly(me, myFirstChild, mySecondChild, otherA, theirChild, otherB);
        assertThat(view.members()).extracting(MemberCard::relationship)
                .containsExactly("SELF", "GUARDIAN", "GUARDIAN", "NONE", "NONE", "SHARED");
        assertThat(view.members()).extracting(MemberCard::experience)
                .containsExactly(ExperienceTier.ADULT, ExperienceTier.CHILD, ExperienceTier.CHILD,
                        ExperienceTier.ADULT, ExperienceTier.CHILD, ExperienceTier.ADULT);
    }

    /* -------------------------------------------------------------------- meals */

    @Test
    @DisplayName("meals awaiting confirmation are the caller's own, by their member id in this household")
    void mealsAwaitingUsesTheCallersOwnMemberId() {
        UUID elsewhere = UUID.randomUUID();
        UUID meElsewhere = UUID.randomUUID();
        // An earlier membership in another household, so "the caller's first membership" is wrong.
        fixture.adult(elsewhere, meAccount, meElsewhere);
        fixture.adult(household, otherAccount, other);
        fixture.adult(household, meAccount, me);
        fixture.child(household, child, meAccount);
        Instant since = NOW.minus(TodayService.AWAITING_WINDOW);
        assertThat(since).isEqualTo(NOW.minus(Duration.ofDays(14)));
        when(meals.countAwaitingConfirmation(meAccount, me, since)).thenReturn(3);

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        assertThat(view.mealsAwaitingConfirmation()).isEqualTo(3);
        verify(meals).countAwaitingConfirmation(meAccount, me, since);
        verify(meals, times(1)).countAwaitingConfirmation(any(), any(), any());
    }

    /* ------------------------------------------------------------------ summary */

    @Test
    @DisplayName("no summary in a two-person household where the other person shares nothing")
    void noSummaryWhenTheOtherSharesNothing() {
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherAccount, other);
        observation(me, MetricCode.STEPS, "4000", ist(TODAY, 9));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        assertThat(view.members()).hasSize(2);
        assertThat(view.summary()).isNull();
    }

    @Test
    @DisplayName("the summary is built from the finished cards once a second person shares a combinable category")
    void summaryIsBuiltFromTheCards() {
        fixture.adult(household, meAccount, me);
        fixture.adult(household, otherAccount, other);
        fixture.grant(other, me, AccessScope.ACTIVITY, AccessLevel.VIEW);
        observation(me, MetricCode.STEPS, "4000", ist(TODAY, 9));
        observation(other, MetricCode.STEPS, "6000", ist(TODAY, 10));
        observation(other, MetricCode.WATER_ML, "900", ist(TODAY, 10));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        FamilySummary summary = view.summary();
        assertThat(summary).isNotNull().isEqualTo(FamilySummaries.summarise(view.members(), view.week()));
        assertThat(summary.includedMemberIds()).containsExactly(me, other);
        FamilySummary.FamilyMetric steps = summary.metrics().stream()
                .filter(m -> m.code() == MetricCode.STEPS).findFirst().orElseThrow();
        assertThat(steps.eligiblePeople()).isEqualTo(2);
        assertThat(steps.days().get(6).total()).isEqualByComparingTo("10000");
        assertThat(steps.days().get(6).contributors()).isEqualTo(2);
        // Their water is not shared, so only my (empty) water counts: one eligible, no total.
        FamilySummary.FamilyMetric water = summary.metrics().stream()
                .filter(m -> m.code() == MetricCode.WATER_ML).findFirst().orElseThrow();
        assertThat(water.eligiblePeople()).isEqualTo(1);
        assertThat(water.days()).allSatisfy(d -> assertThat(d.total()).isNull());
    }

    /* ------------------------------------------------------------------- habits */

    @Test
    @DisplayName("the card's habits summary is the last day of its habits week")
    void habitsDaySummaryIsTheLastHabitsWeekDay() {
        assertThat(TODAY.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        fixture.adult(household, meAccount, me);
        fixture.child(household, child, meAccount);
        Habit walk = Habit.create(me, "Evening walk", HabitCadence.DAILY, meAccount, NOW.minus(Duration.ofDays(30)));
        Habit fruit = Habit.create(me, "Fruit at lunch", HabitCadence.WEEKDAYS, meAccount, NOW.minus(Duration.ofDays(30)));
        when(habitRepository.findByHouseholdMemberIdAndActiveTrueOrderByCreatedAtDesc(me)).thenReturn(List.of(walk, fruit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(anyCollection(), any(), any())).thenReturn(List.of(
                HabitCompletion.on(walk.getId(), TODAY, meAccount, NOW),
                HabitCompletion.on(fruit.getId(), TODAY.minusDays(1), meAccount, NOW)));

        FamilyOverviewView view = service.overview(meAccount, household, TODAY);

        MemberCard mine = cardOf(view, me);
        assertThat(mine.habitsWeek()).extracting(HabitDay::date).containsExactlyElementsOf(view.week());
        HabitDay last = mine.habitsWeek().get(6);
        // Saturday: both habits live, only the daily one due, and it was done.
        assertThat(last).isEqualTo(new HabitDay(TODAY, 2, 1, 1));
        assertThat(mine.habits()).isEqualTo(new DaySummary(last.active(), last.due(), last.done()));
        // Friday: both due, the weekday one done.
        assertThat(mine.habitsWeek().get(5)).isEqualTo(new HabitDay(TODAY.minusDays(1), 2, 2, 1));
        verify(habits).daySummaries(forSubject(me), eq(TODAY.minusDays(6)), eq(TODAY), eq(IST));

        // A guarded child with no habits: a week of zeros, and a zero day — not null.
        MemberCard theirs = cardOf(view, child);
        assertThat(theirs.habitsWeek()).hasSize(7).allSatisfy(d -> assertThat(d.active()).isZero());
        assertThat(theirs.habits()).isEqualTo(new DaySummary(0, 0, 0));
    }
}
