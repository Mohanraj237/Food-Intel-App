package com.familyfood.habits.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.habits.domain.Habit;
import com.familyfood.habits.domain.HabitCadence;
import com.familyfood.habits.domain.HabitCompletion;
import com.familyfood.habits.infra.HabitCompletionRepository;
import com.familyfood.habits.infra.HabitRepository;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Habits record what happened; they never score it.
 *
 * <p>The last test in this class is structural rather than behavioural: it asserts that no
 * response shape carries a streak, badge, points or rank field. Those are not missing features.
 * A score attached to a child's behaviour is a permanent product boundary (D-036, CH-004), and
 * the cheapest place to stop one arriving is a test that fails the moment someone adds the field.
 */
class HabitServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 12);
    private static final UUID HOUSEHOLD = UUID.randomUUID();
    private static final UUID ME_ACCOUNT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER_ACCOUNT = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();

    private HabitRepository habits;
    private HabitCompletionRepository completions;
    private HealthAccessFixture fixture;
    private HabitService service;

    @BeforeEach
    void setUp() {
        habits = mock(HabitRepository.class);
        completions = mock(HabitCompletionRepository.class);
        // The real access policy and tracking-consent resolution (D-044, D-048) over an
        // in-memory household: me, another adult, and a child I guard — all consented.
        fixture = new HealthAccessFixture(NOW);
        service = new HabitService(habits, completions, fixture.policy, fixture.trackingConsent,
                fixture.households, fixture.audit, Clock.fixed(NOW, ZoneOffset.UTC));

        fixture.adult(HOUSEHOLD, ME_ACCOUNT, ME);
        fixture.adult(HOUSEHOLD, OTHER_ACCOUNT, OTHER);
        fixture.child(HOUSEHOLD, CHILD, ME_ACCOUNT);
        // UTC days, so the fixed clock's "today" is exactly TODAY in these tests.
        fixture.zone(HOUSEHOLD, ZoneOffset.UTC);
        when(habits.save(any(Habit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(completions.save(any(HabitCompletion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Habit givenHabit(UUID member, HabitCadence cadence) {
        Habit habit = Habit.create(member, "Walk after dinner", cadence, ME_ACCOUNT, NOW.minusSeconds(30L * 86_400));
        when(habits.findById(habit.getId())).thenReturn(Optional.of(habit));
        return habit;
    }

    @Test
    @DisplayName("an adult creates their own habit")
    void createOwnHabit() {
        HabitView view = service.createHabit(ME_ACCOUNT, ME, "Drink water with breakfast", HabitCadence.DAILY);

        assertThat(view.name()).isEqualTo("Drink water with breakfast");
        assertThat(view.active()).isTrue();
    }

    @Test
    @DisplayName("a guardian creates a habit for their child")
    void guardianCreatesChildHabit() {
        HabitView view = service.createHabit(ME_ACCOUNT, CHILD, "Fruit at lunch", HabitCadence.WEEKDAYS);
        assertThat(view.subjectMemberId()).isEqualTo(CHILD);
    }

    @Test
    @DisplayName("one adult cannot create a habit for another adult (D-002)")
    void otherAdultDenied() {
        assertThatThrownBy(() -> service.createHabit(ME_ACCOUNT, OTHER, "Sleep by 11", HabitCadence.DAILY))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("an empty name is invalid")
    void blankNameRejected() {
        assertThatThrownBy(() -> service.createHabit(ME_ACCOUNT, ME, "   ", HabitCadence.DAILY))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("completing a day records it")
    void completeRecords() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);
        when(completions.findByHabitIdAndCompletedOn(habit.getId(), TODAY)).thenReturn(Optional.empty());

        service.complete(ME_ACCOUNT, habit.getId(), TODAY);

        verify(completions).save(any(HabitCompletion.class));
    }

    @Test
    @DisplayName("completing the same day twice is one fact, not two")
    void doubleCompleteIsIdempotent() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);
        when(completions.findByHabitIdAndCompletedOn(habit.getId(), TODAY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(HabitCompletion.on(habit.getId(), TODAY, ME_ACCOUNT, NOW)));

        service.complete(ME_ACCOUNT, habit.getId(), TODAY);
        service.complete(ME_ACCOUNT, habit.getId(), TODAY);

        verify(completions, times(1)).save(any(HabitCompletion.class));
    }

    @Test
    @DisplayName("a future day cannot be completed")
    void futureDayRejected() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);

        assertThatThrownBy(() -> service.complete(ME_ACCOUNT, habit.getId(), TODAY.plusDays(1)))
                .isInstanceOf(AppException.class);
        verify(completions, never()).save(any());
    }

    @Test
    @DisplayName("an archived habit cannot take new completions")
    void archivedHabitRejectsCompletion() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);
        service.archiveHabit(ME_ACCOUNT, habit.getId());

        assertThatThrownBy(() -> service.complete(ME_ACCOUNT, habit.getId(), TODAY))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("un-completing removes the day")
    void uncompleteRemoves() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);

        service.uncomplete(ME_ACCOUNT, habit.getId(), TODAY);

        verify(completions).deleteByHabitIdAndCompletedOn(habit.getId(), TODAY);
    }

    @Test
    @DisplayName("someone else cannot complete my habit")
    void otherCannotComplete() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);

        assertThatThrownBy(() -> service.complete(OTHER_ACCOUNT, habit.getId(), TODAY))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("consistency is completed days out of expected days — two counts, nothing more")
    void consistencyIsTwoCounts() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);
        LocalDate from = TODAY.minusDays(6);
        when(completions.countByHabitIdAndCompletedOnBetween(eq(habit.getId()), any(), any())).thenReturn(5L);
        when(completions.findByHabitIdAndCompletedOnBetweenOrderByCompletedOnDesc(eq(habit.getId()), any(), any()))
                .thenReturn(List.of(
                        HabitCompletion.on(habit.getId(), TODAY, ME_ACCOUNT, NOW),
                        HabitCompletion.on(habit.getId(), TODAY.minusDays(1), ME_ACCOUNT, NOW),
                        HabitCompletion.on(habit.getId(), TODAY.minusDays(2), ME_ACCOUNT, NOW),
                        HabitCompletion.on(habit.getId(), TODAY.minusDays(4), ME_ACCOUNT, NOW),
                        HabitCompletion.on(habit.getId(), TODAY.minusDays(5), ME_ACCOUNT, NOW)));

        HabitConsistencyView view = service.consistency(ME_ACCOUNT, habit.getId(), from, TODAY);

        assertThat(view.completedDays()).isEqualTo(5);
        assertThat(view.expectedDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("history returns the completed dates")
    void historyReturnsDates() {
        Habit habit = givenHabit(ME, HabitCadence.DAILY);
        when(completions.findByHabitIdAndCompletedOnBetweenOrderByCompletedOnDesc(eq(habit.getId()), any(), any()))
                .thenReturn(List.of(HabitCompletion.on(habit.getId(), TODAY, ME_ACCOUNT, NOW)));

        assertThat(service.history(ME_ACCOUNT, habit.getId(), TODAY.minusDays(7), TODAY)).containsExactly(TODAY);
    }

    /* ------------------------------------------------------------ day summaries */

    /** Sunday 6 September to Saturday 12 September 2026. */
    private static final LocalDate WEEK_START = TODAY.minusDays(6);

    private Habit liveHabit(String name, HabitCadence cadence, Instant createdAt) {
        return Habit.create(ME, name, cadence, ME_ACCOUNT, createdAt);
    }

    /** The completion log: answers the one window query daySummaries makes, as the database would. */
    private void givenCompletions(HabitCompletion... rows) {
        List<HabitCompletion> log = List.of(rows);
        when(completions.findByHabitIdInAndCompletedOnBetween(anyCollection(), any(), any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            return log.stream()
                    .filter(c -> ids.contains(c.getHabitId()))
                    .filter(c -> !c.getCompletedOn().isBefore(from) && !c.getCompletedOn().isAfter(to))
                    .toList();
        });
    }

    /**
     * A WEEKDAYS habit from long ago, and a DAILY one started on Wednesday 9 September (UTC) —
     * with a tick backfilled onto Tuesday, before it existed.
     */
    private void givenAWeekOfHabits() {
        Habit fruit = liveHabit("Fruit at lunch", HabitCadence.WEEKDAYS, NOW.minusSeconds(30L * 86_400));
        Habit walk = liveHabit("Evening walk", HabitCadence.DAILY, Instant.parse("2026-09-09T07:00:00Z"));
        when(habits.findByHouseholdMemberIdAndActiveTrueOrderByCreatedAtDesc(ME)).thenReturn(List.of(walk, fruit));
        givenCompletions(
                HabitCompletion.on(fruit.getId(), LocalDate.of(2026, 9, 7), ME_ACCOUNT, NOW),
                HabitCompletion.on(fruit.getId(), LocalDate.of(2026, 9, 8), ME_ACCOUNT, NOW),
                HabitCompletion.on(fruit.getId(), LocalDate.of(2026, 9, 10), ME_ACCOUNT, NOW),
                HabitCompletion.on(walk.getId(), LocalDate.of(2026, 9, 8), ME_ACCOUNT, NOW),
                HabitCompletion.on(walk.getId(), LocalDate.of(2026, 9, 9), ME_ACCOUNT, NOW),
                HabitCompletion.on(walk.getId(), LocalDate.of(2026, 9, 11), ME_ACCOUNT, NOW));
    }

    @Test
    @DisplayName("a week of day summaries: weekends are not due for WEEKDAYS; a new habit counts only from its first day")
    void daySummariesAcrossAWeek() {
        assertThat(WEEK_START.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(TODAY.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        givenAWeekOfHabits();

        List<HabitService.HabitDay> week = service.daySummaries(
                fixture.policy.decide(ME_ACCOUNT, ME), WEEK_START, TODAY, ZoneOffset.UTC);

        assertThat(week).containsExactly(
                // Sunday: only the weekday habit exists, and it is not due at the weekend.
                new HabitService.HabitDay(LocalDate.of(2026, 9, 6), 1, 0, 0),
                new HabitService.HabitDay(LocalDate.of(2026, 9, 7), 1, 1, 1),
                // Tuesday: the walk's backfilled tick is from before the walk existed — not counted.
                new HabitService.HabitDay(LocalDate.of(2026, 9, 8), 1, 1, 1),
                // Wednesday: the walk starts — active and due from today, not before.
                new HabitService.HabitDay(LocalDate.of(2026, 9, 9), 2, 2, 1),
                new HabitService.HabitDay(LocalDate.of(2026, 9, 10), 2, 2, 1),
                new HabitService.HabitDay(LocalDate.of(2026, 9, 11), 2, 2, 1),
                // Saturday: only the daily walk is due.
                new HabitService.HabitDay(TODAY, 2, 1, 0));

        // One completions query for the whole week, however many habits and days.
        verify(completions, times(1)).findByHabitIdInAndCompletedOnBetween(anyCollection(), eq(WEEK_START), eq(TODAY));
        verify(completions, never()).findByHabitIdInAndCompletedOn(any(), any());
        verify(completions, never()).findByHabitIdAndCompletedOnBetweenOrderByCompletedOnDesc(any(), any(), any());
    }

    @Test
    @DisplayName("the day a habit was created is the household's day, not UTC's")
    void creationDayIsTheHouseholdsDay() {
        // 20:00 UTC on Tuesday 8 September is already 01:30 on Wednesday the 9th in India.
        Habit walk = liveHabit("Evening walk", HabitCadence.DAILY, Instant.parse("2026-09-08T20:00:00Z"));
        when(habits.findByHouseholdMemberIdAndActiveTrueOrderByCreatedAtDesc(ME)).thenReturn(List.of(walk));
        givenCompletions();
        var decision = fixture.policy.decide(ME_ACCOUNT, ME);
        LocalDate tuesday = LocalDate.of(2026, 9, 8);
        LocalDate wednesday = LocalDate.of(2026, 9, 9);

        assertThat(service.daySummaries(decision, tuesday, wednesday, ZoneOffset.UTC)).containsExactly(
                new HabitService.HabitDay(tuesday, 1, 1, 0), new HabitService.HabitDay(wednesday, 1, 1, 0));
        assertThat(service.daySummaries(decision, tuesday, wednesday, ZoneId.of("Asia/Kolkata"))).containsExactly(
                new HabitService.HabitDay(tuesday, 0, 0, 0), new HabitService.HabitDay(wednesday, 1, 1, 0));
    }

    @Test
    @DisplayName("daySummary is exactly the matching day of daySummaries")
    void daySummaryMatchesDaySummaries() {
        givenAWeekOfHabits();
        var decision = fixture.policy.decide(ME_ACCOUNT, ME);

        // The fixture's household is on UTC, which is the zone daySummary resolves for itself.
        List<HabitService.HabitDay> week = service.daySummaries(decision, WEEK_START, TODAY, ZoneOffset.UTC);

        for (HabitService.HabitDay day : week) {
            assertThat(service.daySummary(decision, day.date()))
                    .as("day summary for %s", day.date())
                    .isEqualTo(new HabitService.DaySummary(day.active(), day.due(), day.done()));
        }
    }

    @Test
    @DisplayName("with no live habits every day is zeros, and the completion log is not queried")
    void noLiveHabitsIsZerosWithoutAQuery() {
        List<HabitService.HabitDay> week = service.daySummaries(
                fixture.policy.decide(ME_ACCOUNT, ME), WEEK_START, TODAY, ZoneOffset.UTC);

        assertThat(week).hasSize(7).allSatisfy(d -> {
            assertThat(d.active()).isZero();
            assertThat(d.due()).isZero();
            assertThat(d.done()).isZero();
        });
        verify(completions, never()).findByHabitIdInAndCompletedOnBetween(any(), any(), any());
    }

    @Test
    @DisplayName("no response shape carries a streak, badge, points or rank (D-036, CH-004)")
    void noGamificationFieldsExist() {
        for (Class<?> type : List.of(HabitView.class, HabitConsistencyView.class,
                HabitService.DaySummary.class, HabitService.HabitDay.class)) {
            List<String> components = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .map(String::toLowerCase)
                    .toList();
            assertThat(components)
                    .as("%s must not carry an engagement score", type.getSimpleName())
                    .noneMatch(name -> name.contains("streak") || name.contains("badge")
                            || name.contains("point") || name.contains("rank")
                            || name.contains("score") || name.contains("leader"));
        }
    }
}
