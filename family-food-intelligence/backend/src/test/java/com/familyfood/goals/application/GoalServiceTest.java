package com.familyfood.goals.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalMetricAggregation;
import com.familyfood.goals.domain.GoalPeriod;
import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.goals.infra.WellnessGoalRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.WellnessService;
import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import com.familyfood.wellness.infra.HealthObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Goals go through the same age gate as observations — not a copy of it — plus the D-047 goal
 * rules, and the one access policy (D-044).
 *
 * <p>The REAL {@link WellnessService} (spied, so the tests can prove which gate answered) and the
 * REAL access policy run here. The test that matters most is {@link #childWeightGoalIsRefused}: a
 * weight target for a child must be exactly as impossible as a weight reading for a child, and it
 * must be refused by the same {@code WellnessAccess.isMetricPermittedForSubject} call. If this
 * module ever grew its own age check, the two could drift and one would eventually let through
 * what the other stops.
 */
class GoalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final UUID HOUSEHOLD = UUID.randomUUID();
    private static final UUID ME_ACCOUNT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER_ACCOUNT = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();

    /** The day every progressFrom/goalProgress window below ends on, in the fixture's zone (India). */
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 9, 12);
    private static final ZoneId IST = HealthAccessFixture.HOUSEHOLD_ZONE;

    /** Rows behind the fake observation table — see {@link #givenObservations}. */
    private final List<HealthObservation> stored = new ArrayList<>();

    private HealthAccessFixture fixture;
    private WellnessGoalRepository goals;
    private HealthObservationRepository observations;
    private ProfileService profiles;
    private WellnessService wellness;
    private AuditService audit;
    private GoalService service;

    @BeforeEach
    void setUp() {
        fixture = new HealthAccessFixture(NOW);
        goals = mock(WellnessGoalRepository.class);
        observations = mock(HealthObservationRepository.class);
        profiles = mock(ProfileService.class);
        audit = fixture.audit;
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        wellness = spy(new WellnessService(observations, fixture.policy,
                fixture.trackingConsent, fixture.households, profiles, audit, clock));
        service = new GoalService(goals, wellness, fixture.policy, fixture.trackingConsent,
                fixture.households, audit, clock);

        fixture.adult(HOUSEHOLD, ME_ACCOUNT, ME);
        fixture.adult(HOUSEHOLD, OTHER_ACCOUNT, OTHER);
        fixture.child(HOUSEHOLD, CHILD, ME_ACCOUNT);

        when(profiles.currentAgeBand(ME)).thenReturn(Optional.of(AgeBand.A31_45));
        when(profiles.currentAgeBand(OTHER)).thenReturn(Optional.of(AgeBand.A60_PLUS));
        when(profiles.currentAgeBand(CHILD)).thenReturn(Optional.of(AgeBand.C7_9));
        when(goals.save(any(WellnessGoal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goals.findByHouseholdMemberIdAndMetricCodeAndActiveTrue(any(), any())).thenReturn(Optional.empty());
    }

    private static CreateGoalCommand goal(UUID member, MetricCode metric, GoalDirection direction, String target) {
        return new CreateGoalCommand(member, metric, direction, new BigDecimal(target), GoalPeriod.WEEKLY);
    }

    private static CreateGoalCommand goal(UUID member, MetricCode metric, String target) {
        return goal(member, metric, GoalDirection.AT_MOST, target);
    }

    private static ErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            return e.code();
        }
        throw new AssertionError("expected an AppException");
    }

    private void verifyRefusedForAge(UUID subject) {
        verify(audit).deniedForSubject(eq(AuditAction.HEALTH_METRIC_REFUSED_FOR_AGE), eq("WELLNESS_GOAL"),
                eq(subject), eq(ME_ACCOUNT), eq(HOUSEHOLD), eq(subject), anyString());
    }

    /* ------------------------------------------------------------ the age gate */

    @Test
    @DisplayName("an adult may set a weight goal — D-036 supersedes D-017 for adults")
    void adultWeightGoalAllowed() {
        GoalView view = service.createGoal(ME_ACCOUNT, goal(ME, MetricCode.WEIGHT_KG, "70"));

        assertThat(view.metricCode()).isEqualTo(MetricCode.WEIGHT_KG);
        assertThat(view.startedOn()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("a child weight goal is REFUSED, by the same check that refuses a weight reading")
    void childWeightGoalIsRefused() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(CHILD, MetricCode.WEIGHT_KG, "20"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);

        // Refused before anything is persisted, and via the shared gate — not a local rule.
        verify(goals, never()).save(any());
        verify(wellness).isMetricPermittedForSubject(ME_ACCOUNT, CHILD, MetricCode.WEIGHT_KG);
        verifyRefusedForAge(CHILD);
    }

    @Test
    @DisplayName("an adult with no profile cannot set a goal on a body metric — unknown age fails closed")
    void unknownAgeGoalRefused() {
        when(profiles.currentAgeBand(ME)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(ME, MetricCode.WEIGHT_KG, "70"))))
                .isEqualTo(ErrorCode.METRIC_NOT_PERMITTED_FOR_AGE);
        verify(goals, never()).save(any());
    }

    /* ------------------------------------------------------ D-047: child goals */

    @Test
    @DisplayName("a child hydration goal is allowed")
    void childHydrationGoalAllowed() {
        GoalView view = service.createGoal(ME_ACCOUNT, new CreateGoalCommand(
                CHILD, MetricCode.WATER_ML, GoalDirection.AT_LEAST, new BigDecimal("1200"), GoalPeriod.DAILY));

        assertThat(view.metricCode()).isEqualTo(MetricCode.WATER_ML);
        // A guardian's write of child data is audited in the strict category too.
        verify(audit).successForSubject(eq(AuditAction.CHILD_DATA_ACCESSED), eq("WELLNESS_GOAL"), any(),
                eq(ME_ACCOUNT), eq(HOUSEHOLD), eq(CHILD), anyMap());
    }

    @Test
    @DisplayName("a child sleep goal of AT_LEAST is allowed")
    void childSleepAtLeastAllowed() {
        GoalView view = service.createGoal(ME_ACCOUNT, goal(CHILD, MetricCode.SLEEP_MINUTES, GoalDirection.AT_LEAST, "600"));

        assertThat(view.direction()).isEqualTo(GoalDirection.AT_LEAST);
    }

    @Test
    @DisplayName("a child NUTRITION goal is refused — no sugar, calorie or macro target for a minor (D-047)")
    void childNutritionGoalRefused() {
        // SUGAR_G is recordable for a child; the goal is what is refused.
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT,
                goal(CHILD, MetricCode.SUGAR_G, GoalDirection.AT_LEAST, "10"))))
                .isEqualTo(ErrorCode.GOAL_NOT_PERMITTED_FOR_CHILD);

        verify(goals, never()).save(any());
        verifyRefusedForAge(CHILD);
    }

    @Test
    @DisplayName("a child calorie cap is refused as a child goal, not merely as a calorie cap")
    void childCalorieCapRefusedAsChildGoal() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT,
                goal(CHILD, MetricCode.ENERGY_KCAL, GoalDirection.AT_MOST, "1200"))))
                .isEqualTo(ErrorCode.GOAL_NOT_PERMITTED_FOR_CHILD);
        verify(goals, never()).save(any());
    }

    @Test
    @DisplayName("no AT_MOST on anything for a child — even activity")
    void childActivityAtMostRefused() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT,
                goal(CHILD, MetricCode.STEPS, GoalDirection.AT_MOST, "5000"))))
                .isEqualTo(ErrorCode.GOAL_NOT_PERMITTED_FOR_CHILD);
        verify(goals, never()).save(any());
    }

    @Test
    @DisplayName("no wellbeing score to aim at for a child")
    void childWellbeingGoalRefused() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT,
                goal(CHILD, MetricCode.MOOD_SCORE, GoalDirection.AT_LEAST, "4"))))
                .isEqualTo(ErrorCode.GOAL_NOT_PERMITTED_FOR_CHILD);
    }

    /* ----------------------------------------------------- D-047: adult guardrail */

    @Test
    @DisplayName("an adult calorie cap waits for a reviewed safety floor (D-047)")
    void adultCalorieCapRefused() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT,
                goal(ME, MetricCode.ENERGY_KCAL, GoalDirection.AT_MOST, "300"))))
                .isEqualTo(ErrorCode.GOAL_REQUIRES_REVIEWED_STANDARD);
        verify(goals, never()).save(any());
    }

    @Test
    @DisplayName("an adult AT_LEAST energy goal is allowed — only the cap is held back")
    void adultEnergyAtLeastAllowed() {
        GoalView view = service.createGoal(ME_ACCOUNT, goal(ME, MetricCode.ENERGY_KCAL, GoalDirection.AT_LEAST, "1800"));

        assertThat(view.metricCode()).isEqualTo(MetricCode.ENERGY_KCAL);
    }

    /* ------------------------------------------------------------ D-048: consent */

    @Test
    @DisplayName("a goal needs the subject's tracking consent")
    void goalNeedsConsent() {
        fixture.withdrawConsent(ME);

        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(ME, MetricCode.STEPS, GoalDirection.AT_LEAST, "8000"))))
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
        verify(goals, never()).save(any());
    }

    /* --------------------------------------------------------- D-044 / D-045 */

    @Test
    @DisplayName("one adult cannot set a goal for another adult (D-002)")
    void otherAdultDenied() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(OTHER, MetricCode.STEPS, "50000"))))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(goals, never()).save(any());
    }

    @Test
    @DisplayName("a caregiver with MANAGE on ACTIVITY may set a steps goal for the adult who chose them")
    void caregiverMayCreateGoalInManagedScope() {
        fixture.grant(OTHER, ME, AccessScope.ACTIVITY, AccessLevel.MANAGE);

        GoalView view = service.createGoal(ME_ACCOUNT, goal(OTHER, MetricCode.STEPS, GoalDirection.AT_LEAST, "30000"));

        assertThat(view.subjectMemberId()).isEqualTo(OTHER);
        ArgumentCaptor<WellnessGoal> saved = ArgumentCaptor.forClass(WellnessGoal.class);
        verify(goals).save(saved.capture());
        assertThat(saved.getValue().getHouseholdMemberId()).isEqualTo(OTHER);
        verify(audit).successForSubject(eq(AuditAction.GOAL_CREATED), eq("WELLNESS_GOAL"), any(),
                eq(ME_ACCOUNT), eq(HOUSEHOLD), eq(OTHER), anyMap());
    }

    @Test
    @DisplayName("a SHARED viewer may not set a goal, and a caregiver may not step outside their scope")
    void viewerAndOutOfScopeCaregiverDenied() {
        fixture.grant(OTHER, ME, AccessScope.ACTIVITY, AccessLevel.VIEW);
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(OTHER, MetricCode.STEPS, GoalDirection.AT_LEAST, "30000"))))
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        fixture.grant(OTHER, ME, AccessScope.SLEEP, AccessLevel.MANAGE);
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(OTHER, MetricCode.WEIGHT_KG, "60"))))
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(goals, never()).save(any());
    }

    @Test
    @DisplayName("listGoals shows a viewer only the goals whose category they may see")
    void listGoalsFiltersToViewableScopes() {
        WellnessGoal steps = WellnessGoal.start(OTHER, MetricCode.STEPS, GoalDirection.AT_LEAST,
                new BigDecimal("50000"), GoalPeriod.WEEKLY, LocalDate.of(2026, 9, 1), OTHER_ACCOUNT, NOW);
        WellnessGoal weight = WellnessGoal.start(OTHER, MetricCode.WEIGHT_KG, GoalDirection.AT_MOST,
                new BigDecimal("70"), GoalPeriod.WEEKLY, LocalDate.of(2026, 9, 1), OTHER_ACCOUNT, NOW);
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(OTHER)).thenReturn(List.of(steps, weight));
        fixture.grant(OTHER, ME, AccessScope.ACTIVITY, AccessLevel.VIEW);

        List<GoalView> visible = service.listGoals(ME_ACCOUNT, OTHER, false);

        // A weight goal is BODY data; sharing activity does not disclose it.
        assertThat(visible).extracting(GoalView::metricCode).containsExactly(MetricCode.STEPS);
        verify(audit).successForSubject(eq(AuditAction.HEALTH_DATA_ACCESSED), eq("WELLNESS_GOAL"), eq(OTHER),
                eq(ME_ACCOUNT), eq(HOUSEHOLD), eq(OTHER), anyMap());
    }

    @Test
    @DisplayName("someone who can see nothing of a member is refused, not shown an empty goal list")
    void listGoalsRefusesWhenNothingVisible() {
        assertThat(codeOf(() -> service.listGoals(ME_ACCOUNT, OTHER, false))).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(goals, never()).findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(any());
    }

    /* ----------------------------------------------------------- validation */

    @Test
    @DisplayName("a second live goal on the same metric is refused")
    void duplicateActiveGoalRefused() {
        when(goals.findByHouseholdMemberIdAndMetricCodeAndActiveTrue(ME, MetricCode.STEPS))
                .thenReturn(Optional.of(WellnessGoal.start(ME, MetricCode.STEPS, GoalDirection.AT_LEAST,
                        new BigDecimal("50000"), GoalPeriod.WEEKLY, LocalDate.of(2026, 9, 1), ME_ACCOUNT, NOW)));

        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(ME, MetricCode.STEPS, GoalDirection.AT_LEAST, "60000"))))
                .isEqualTo(ErrorCode.GOAL_ALREADY_ACTIVE);
    }

    @Test
    @DisplayName("a negative target is invalid")
    void invalidTarget() {
        assertThat(codeOf(() -> service.createGoal(ME_ACCOUNT, goal(ME, MetricCode.WATER_ML, "-5"))))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(goals, never()).save(any());
    }

    /* -------------------------------------------------------------- progress */

    @Test
    @DisplayName("progress with real data reports the measured total against the target")
    void progressWithData() {
        WellnessGoal water = WellnessGoal.start(ME, MetricCode.WATER_ML, GoalDirection.AT_LEAST,
                new BigDecimal("2000"), GoalPeriod.DAILY, LocalDate.of(2026, 9, 12), ME_ACCOUNT, NOW);
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(ME)).thenReturn(List.of(water));
        doReturn(Map.of(MetricCode.WATER_ML, new BigDecimal("1500")))
                .when(wellness).sumByMetric(eq(ME_ACCOUNT), eq(ME), any(), any());

        GoalProgressView p = service.goalProgress(ME_ACCOUNT, ME, null, null).get(0);

        assertThat(p.dataAvailable()).isTrue();
        assertThat(p.actual()).isEqualByComparingTo("1500");
        assertThat(p.target()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("progress with NO data reports absent — never a measured zero")
    void progressWithoutDataIsAbsent() {
        WellnessGoal water = WellnessGoal.start(ME, MetricCode.WATER_ML, GoalDirection.AT_LEAST,
                new BigDecimal("2000"), GoalPeriod.DAILY, LocalDate.of(2026, 9, 12), ME_ACCOUNT, NOW);
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(ME)).thenReturn(List.of(water));
        doReturn(Map.of()).when(wellness).sumByMetric(eq(ME_ACCOUNT), eq(ME), any(), any());

        GoalProgressView p = service.goalProgress(ME_ACCOUNT, ME, null, null).get(0);

        // "You drank 0 ml" and "you have not logged water" are different sentences; the first
        // invents a measurement and then scolds the user for it.
        assertThat(p.dataAvailable()).isFalse();
        assertThat(p.actual()).isNull();
    }

    @Test
    @DisplayName("a weight goal uses the latest reading, not a sum of weigh-ins")
    void weightUsesLatestReading() {
        WellnessGoal weight = WellnessGoal.start(ME, MetricCode.WEIGHT_KG, GoalDirection.AT_MOST,
                new BigDecimal("70"), GoalPeriod.WEEKLY, LocalDate.of(2026, 9, 1), ME_ACCOUNT, NOW);
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(ME)).thenReturn(List.of(weight));
        doReturn(Map.of(MetricCode.WEIGHT_KG, new ObservationView(UUID.randomUUID(), ME, MetricCode.WEIGHT_KG,
                MetricCode.WEIGHT_KG.category(), new BigDecimal("72.4"), "kg", NOW, NOW, ObservationSource.MANUAL)))
                .when(wellness).latestByMetric(eq(ME_ACCOUNT), eq(ME), any(), any());

        GoalProgressView p = service.goalProgress(ME_ACCOUNT, ME, null, null).get(0);

        // Summing three weigh-ins of 72 kg would report 216 kg. The latest reading is the fact.
        assertThat(p.actual()).isEqualByComparingTo("72.4");
        verify(wellness, never()).sumByMetric(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a daily goal's window is the household's day, midnight to midnight (D-053)")
    void dailyWindowIsTheHouseholdDay() {
        WellnessGoal water = WellnessGoal.start(ME, MetricCode.WATER_ML, GoalDirection.AT_LEAST,
                new BigDecimal("2000"), GoalPeriod.DAILY, LocalDate.of(2026, 9, 12), ME_ACCOUNT, NOW);
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(ME)).thenReturn(List.of(water));
        doReturn(Map.of()).when(wellness).sumByMetric(eq(ME_ACCOUNT), eq(ME), any(), any());

        GoalProgressView p = service.goalProgress(ME_ACCOUNT, ME, null, null).get(0);

        // 2026-09-12 in Asia/Kolkata starts at 2026-09-11T18:30Z, and ends one nanosecond before
        // the next local midnight — not at a UTC boundary.
        assertThat(p.windowFrom()).isEqualTo(Instant.parse("2026-09-11T18:30:00Z"));
        assertThat(p.windowTo()).isEqualTo(Instant.parse("2026-09-12T18:30:00Z").minusNanos(1));
    }

    /* ------------------------------------- progressFrom: the family overview's one read */

    /**
     * The observation table behind the REAL wellness service: answers its window query by member,
     * category and inclusive bounds, newest first — so {@code goalProgress} (through
     * {@code sumByMetric}/{@code latestByMetric}) and {@code progressFrom} (through one week's
     * {@code window}) both read the same rows.
     */
    private void givenObservations(HealthObservation... rows) {
        stored.addAll(List.of(rows));
        when(observations.findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(
                any(), any(), any(), any())).thenAnswer(inv -> {
                    UUID member = inv.getArgument(0);
                    Collection<MetricCategory> categories = inv.getArgument(1);
                    Instant from = inv.getArgument(2);
                    Instant to = inv.getArgument(3);
                    return stored.stream()
                            .filter(o -> o.getHouseholdMemberId().equals(member))
                            .filter(o -> categories.contains(o.getMetricCategory()))
                            .filter(o -> !o.getEffectiveAt().isBefore(from) && !o.getEffectiveAt().isAfter(to))
                            .sorted(Comparator.comparing(HealthObservation::getEffectiveAt).reversed())
                            .toList();
                });
    }

    /** A reading at a wall-clock time in India. */
    private static HealthObservation reading(UUID member, MetricCode code, String value,
                                             LocalDate day, int hour, int minute) {
        Instant at = day.atTime(hour, minute).atZone(IST).toInstant();
        return HealthObservation.record(member, code, new BigDecimal(value), at, ME_ACCOUNT,
                ObservationSource.MANUAL, at);
    }

    /** What the family overview reads once per member: the seven household days ending on {@link #LAST_DAY}. */
    private List<ObservationView> weekRead(UUID caller, UUID member) {
        return wellness.window(caller, member,
                LAST_DAY.minusDays(6).atStartOfDay(IST).toInstant(),
                LAST_DAY.plusDays(1).atStartOfDay(IST).toInstant().minusNanos(1));
    }

    /**
     * Runs both paths for the given goals and requires them to agree field for field — actual,
     * dataAvailable and window included — returning progressFrom's answer.
     */
    private List<GoalProgressView> agreeing(UUID caller, UUID member, WellnessGoal... live) {
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(member)).thenReturn(List.of(live));

        List<GoalProgressView> viaWellness = service.goalProgress(caller, member, null, LAST_DAY);
        List<GoalProgressView> fromRead = service.progressFrom(
                fixture.policy.decide(caller, member), LAST_DAY, IST, weekRead(caller, member));

        assertThat(fromRead).hasSameSizeAs(viaWellness);
        for (int i = 0; i < fromRead.size(); i++) {
            GoalProgressView a = fromRead.get(i);
            GoalProgressView b = viaWellness.get(i);
            assertThat(a.goalId()).isEqualTo(b.goalId());
            assertThat(a.actual()).as("actual of %s", a.metricCode()).isEqualTo(b.actual());
            assertThat(a.dataAvailable()).as("dataAvailable of %s", a.metricCode()).isEqualTo(b.dataAvailable());
            assertThat(a.windowFrom()).as("windowFrom of %s", a.metricCode()).isEqualTo(b.windowFrom());
            assertThat(a.windowTo()).as("windowTo of %s", a.metricCode()).isEqualTo(b.windowTo());
        }
        assertThat(fromRead).isEqualTo(viaWellness);
        return fromRead;
    }

    private static WellnessGoal liveGoal(UUID member, MetricCode metric, GoalDirection direction,
                                         String target, GoalPeriod period) {
        return WellnessGoal.start(member, metric, direction, new BigDecimal(target), period,
                LAST_DAY.minusDays(20), ME_ACCOUNT, NOW);
    }

    @Test
    @DisplayName("progressFrom agrees with goalProgress for a DAILY summed goal — the household's day only")
    void progressFromAgreesForDailySum() {
        givenObservations(
                reading(ME, MetricCode.WATER_ML, "500", LAST_DAY, 0, 0),              // local midnight: today
                reading(ME, MetricCode.WATER_ML, "750", LAST_DAY, 14, 0),
                reading(ME, MetricCode.WATER_ML, "900", LAST_DAY.minusDays(1), 23, 59)); // yesterday: in the week read only

        GoalProgressView p = agreeing(ME_ACCOUNT, ME,
                liveGoal(ME, MetricCode.WATER_ML, GoalDirection.AT_LEAST, "2000", GoalPeriod.DAILY)).get(0);

        assertThat(p.dataAvailable()).isTrue();
        assertThat(p.actual()).isEqualByComparingTo("1250");
        assertThat(p.windowFrom()).isEqualTo(Instant.parse("2026-09-11T18:30:00Z"));
        assertThat(p.windowTo()).isEqualTo(Instant.parse("2026-09-12T18:30:00Z").minusNanos(1));
    }

    @Test
    @DisplayName("progressFrom agrees with goalProgress for a WEEKLY summed goal — seven household days")
    void progressFromAgreesForWeeklySum() {
        givenObservations(
                reading(ME, MetricCode.STEPS, "4000", LAST_DAY.minusDays(6), 0, 0),    // first instant of the week
                reading(ME, MetricCode.STEPS, "6000", LAST_DAY.minusDays(3), 12, 0),
                reading(ME, MetricCode.STEPS, "3000", LAST_DAY, 23, 59),
                reading(ME, MetricCode.STEPS, "9999", LAST_DAY.minusDays(7), 23, 59)); // the day before it

        GoalProgressView p = agreeing(ME_ACCOUNT, ME,
                liveGoal(ME, MetricCode.STEPS, GoalDirection.AT_LEAST, "50000", GoalPeriod.WEEKLY)).get(0);

        assertThat(p.dataAvailable()).isTrue();
        assertThat(p.actual()).isEqualByComparingTo("13000");
        assertThat(p.windowFrom()).isEqualTo(Instant.parse("2026-09-05T18:30:00Z"));
        assertThat(p.windowTo()).isEqualTo(Instant.parse("2026-09-12T18:30:00Z").minusNanos(1));
    }

    @Test
    @DisplayName("progressFrom agrees with goalProgress for a LATEST goal — the newest reading, never a sum")
    void progressFromAgreesForLatest() {
        givenObservations(
                reading(ME, MetricCode.WEIGHT_KG, "72.4", LAST_DAY.minusDays(4), 7, 0),
                reading(ME, MetricCode.WEIGHT_KG, "71.9", LAST_DAY.minusDays(1), 7, 0),
                reading(ME, MetricCode.WEIGHT_KG, "72.1", LAST_DAY.minusDays(2), 21, 0),
                reading(ME, MetricCode.WEIGHT_KG, "73.0", LAST_DAY.minusDays(9), 7, 0));

        GoalProgressView p = agreeing(ME_ACCOUNT, ME,
                liveGoal(ME, MetricCode.WEIGHT_KG, GoalDirection.AT_MOST, "70", GoalPeriod.WEEKLY)).get(0);

        assertThat(p.aggregation()).isEqualTo(GoalMetricAggregation.LATEST);
        assertThat(p.dataAvailable()).isTrue();
        assertThat(p.actual()).isEqualByComparingTo("71.9");
    }

    @Test
    @DisplayName("progressFrom agrees with goalProgress when the window holds nothing — absent, not zero")
    void progressFromAgreesWhenAbsent() {
        // Sleep was logged yesterday: inside the week read, outside today's daily window.
        givenObservations(reading(ME, MetricCode.SLEEP_MINUTES, "480", LAST_DAY.minusDays(1), 7, 0));

        List<GoalProgressView> progress = agreeing(ME_ACCOUNT, ME,
                liveGoal(ME, MetricCode.SLEEP_MINUTES, GoalDirection.AT_LEAST, "420", GoalPeriod.DAILY),
                liveGoal(ME, MetricCode.RESTING_HEART_RATE_BPM, GoalDirection.AT_MOST, "70", GoalPeriod.WEEKLY));

        assertThat(progress).hasSize(2).allSatisfy(p -> {
            assertThat(p.dataAvailable()).isFalse();
            assertThat(p.actual()).isNull();
        });
    }

    @Test
    @DisplayName("progressFrom leaves out goals whose category the decision cannot see, as goalProgress does")
    void progressFromSkipsGoalsOutsideTheDecision() {
        fixture.grant(OTHER, ME, AccessScope.ACTIVITY, AccessLevel.VIEW);
        givenObservations(
                reading(OTHER, MetricCode.STEPS, "8000", LAST_DAY, 9, 0),
                reading(OTHER, MetricCode.WEIGHT_KG, "80", LAST_DAY, 7, 0));

        List<GoalProgressView> visible = agreeing(ME_ACCOUNT, OTHER,
                liveGoal(OTHER, MetricCode.STEPS, GoalDirection.AT_LEAST, "50000", GoalPeriod.WEEKLY),
                liveGoal(OTHER, MetricCode.WEIGHT_KG, GoalDirection.AT_MOST, "75", GoalPeriod.WEEKLY));

        // A weight goal is BODY data; sharing activity does not disclose it.
        assertThat(visible).extracting(GoalProgressView::metricCode).containsExactly(MetricCode.STEPS);
        assertThat(visible.get(0).actual()).isEqualByComparingTo("8000");

        // The skip is the decision's, not the read's: even handed the unfiltered rows, the weight
        // goal stays out.
        List<ObservationView> unfiltered = stored.stream()
                .sorted(Comparator.comparing(HealthObservation::getEffectiveAt).reversed())
                .map(ObservationView::from)
                .toList();
        assertThat(service.progressFrom(fixture.policy.decide(ME_ACCOUNT, OTHER), LAST_DAY, IST, unfiltered))
                .extracting(GoalProgressView::metricCode).containsExactly(MetricCode.STEPS);

        // Someone who sees nothing of the member is given nothing (the overview never asks).
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(ME)).thenReturn(List.of(
                liveGoal(ME, MetricCode.STEPS, GoalDirection.AT_LEAST, "50000", GoalPeriod.WEEKLY)));
        assertThat(service.progressFrom(fixture.policy.decide(OTHER_ACCOUNT, ME), LAST_DAY, IST, unfiltered)).isEmpty();
    }

    @Test
    @DisplayName("progressFrom reads no observations and writes no audit — the overview's one read already did")
    void progressFromReadsAndAuditsNothing() {
        fixture.grant(OTHER, ME, AccessScope.ACTIVITY, AccessLevel.VIEW);
        when(goals.findByHouseholdMemberIdAndActiveTrueOrderByStartedOnDesc(OTHER)).thenReturn(List.of(
                liveGoal(OTHER, MetricCode.STEPS, GoalDirection.AT_LEAST, "50000", GoalPeriod.WEEKLY)));
        List<ObservationView> alreadyRead = List.of(
                ObservationView.from(reading(OTHER, MetricCode.STEPS, "2500", LAST_DAY, 9, 0)));

        List<GoalProgressView> progress = service.progressFrom(
                fixture.policy.decide(ME_ACCOUNT, OTHER), LAST_DAY, IST, alreadyRead);

        assertThat(progress).singleElement().satisfies(p -> assertThat(p.actual()).isEqualByComparingTo("2500"));
        verify(wellness, never()).window(any(), any(), any(), any());
        verify(wellness, never()).sumByMetric(any(), any(), any(), any());
        verify(wellness, never()).latestByMetric(any(), any(), any(), any());
        verify(observations, never())
                .findByHouseholdMemberIdAndMetricCategoryInAndEffectiveAtBetweenOrderByEffectiveAtDesc(any(), any(), any(), any());
        verify(audit, never()).successForSubject(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("goal progress carries no 'achieved', score or percentage field")
    void progressIsArithmeticNotAScore() {
        List<String> components = java.util.Arrays.stream(GoalProgressView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(components).doesNotContain("achieved", "score", "percentage", "onTrack", "streak", "rank");
    }
}
