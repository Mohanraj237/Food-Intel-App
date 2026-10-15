package com.familyfood.dashboard.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.domain.Relationship;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.domain.MetricAggregation;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The day arithmetic the Today screen and the family overview share (D-053).
 *
 * <p>The cases that matter: a day is the household's midnight to midnight, not UTC's; a day
 * nobody logged is a gap ({@code null}, count 0) and never a zero; and a day in the week and
 * that day's {@link MetricDay} come out of the same accumulator, so they cannot disagree.
 */
class DashboardDaysTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** A Saturday. The week is Sunday 6 September to Saturday 12 September. */
    private static final LocalDate DAY = LocalDate.of(2026, 9, 12);
    private static final List<LocalDate> WEEK = DashboardDays.daysEndingOn(DAY, 7);
    private static final UUID MEMBER = UUID.randomUUID();

    private static ObservationView obs(MetricCode code, String value, Instant effectiveAt) {
        return obs(code, value, effectiveAt, effectiveAt);
    }

    private static ObservationView obs(MetricCode code, String value, Instant effectiveAt, Instant recordedAt) {
        return new ObservationView(UUID.randomUUID(), MEMBER, code, code.category(), new BigDecimal(value),
                code.canonicalUnit(), effectiveAt, recordedAt, ObservationSource.MANUAL);
    }

    /** A household-local wall-clock time in India. */
    private static Instant ist(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(IST).toInstant();
    }

    private static MetricWeek weekOf(List<MetricWeek> weeks, MetricCode code) {
        return weeks.stream().filter(w -> w.code() == code).findFirst()
                .orElseThrow(() -> new AssertionError("no week row for " + code));
    }

    private static MetricWeek.DayPoint pointOn(MetricWeek week, LocalDate day) {
        return week.days().stream().filter(p -> p.date().equals(day)).findFirst()
                .orElseThrow(() -> new AssertionError("no point for " + day));
    }

    /* ------------------------------------------------------------------ the week */

    @Test
    @DisplayName("the week is seven consecutive days ending on the day asked for, oldest first")
    void weekIsSevenDaysOldestFirst() {
        assertThat(WEEK).containsExactly(
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), DAY);
    }

    /* ----------------------------------------------------------- midnight in India */

    @Test
    @DisplayName("23:59 and 00:00 in India land on different days, although both are the same UTC day")
    void istMidnightSplitsTheDay() {
        LocalDate thursday = LocalDate.of(2026, 9, 10);
        LocalDate friday = LocalDate.of(2026, 9, 11);
        ObservationView lateThursday = obs(MetricCode.WATER_ML, "250", ist(thursday, 23, 59));
        ObservationView fridayMidnight = obs(MetricCode.WATER_ML, "400", ist(friday, 0, 0));

        // Both instants fall on 10 September in UTC; bucketing by UTC would merge them into one day.
        assertThat(LocalDate.ofInstant(lateThursday.effectiveAt(), ZoneOffset.UTC)).isEqualTo(thursday);
        assertThat(LocalDate.ofInstant(fridayMidnight.effectiveAt(), ZoneOffset.UTC)).isEqualTo(thursday);

        List<ObservationView> both = List.of(fridayMidnight, lateThursday);
        assertThat(DashboardDays.onDay(both, thursday, IST)).containsExactly(lateThursday);
        assertThat(DashboardDays.onDay(both, friday, IST)).containsExactly(fridayMidnight);

        MetricWeek water = weekOf(DashboardDays.weekSeries(both, WEEK, IST), MetricCode.WATER_ML);
        assertThat(pointOn(water, thursday).value()).isEqualByComparingTo("250");
        assertThat(pointOn(water, thursday).count()).isEqualTo(1);
        assertThat(pointOn(water, friday).value()).isEqualByComparingTo("400");
        assertThat(pointOn(water, friday).count()).isEqualTo(1);

        // The same boundary the observation query uses: local midnight starts the day, and the
        // previous day ends one nanosecond before it.
        assertThat(DashboardDays.startOf(friday, IST)).isEqualTo(Instant.parse("2026-09-10T18:30:00Z"));
        assertThat(DashboardDays.endOf(thursday, IST)).isEqualTo(Instant.parse("2026-09-10T18:30:00Z").minusNanos(1));
        assertThat(fridayMidnight.effectiveAt()).isEqualTo(DashboardDays.startOf(friday, IST));
        assertThat(lateThursday.effectiveAt()).isBefore(DashboardDays.endOf(thursday, IST));
    }

    /* ---------------------------------------------------------------- reductions */

    @Test
    @DisplayName("a summed metric is totalled per day")
    void sumPerDay() {
        List<ObservationView> observations = List.of(
                obs(MetricCode.WATER_ML, "250", ist(DAY, 8, 0)),
                obs(MetricCode.WATER_ML, "300", ist(DAY, 13, 0)),
                obs(MetricCode.WATER_ML, "400", ist(DAY.minusDays(1), 9, 0)));

        MetricWeek water = weekOf(DashboardDays.weekSeries(observations, WEEK, IST), MetricCode.WATER_ML);

        assertThat(water.aggregation()).isEqualTo(MetricAggregation.SUM);
        assertThat(water.unit()).isEqualTo("ml");
        assertThat(pointOn(water, DAY).value()).isEqualByComparingTo("550");
        assertThat(pointOn(water, DAY).count()).isEqualTo(2);
        assertThat(pointOn(water, DAY.minusDays(1)).value()).isEqualByComparingTo("400");
        assertThat(pointOn(water, DAY.minusDays(1)).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a point-in-time metric takes the day's newest reading by when it applies, not by list order")
    void latestNewestPerDay() {
        List<ObservationView> observations = List.of(
                obs(MetricCode.WEIGHT_KG, "72.0", ist(DAY, 7, 0)),
                obs(MetricCode.WEIGHT_KG, "71.6", ist(DAY, 21, 0), ist(DAY, 7, 5)),
                obs(MetricCode.WEIGHT_KG, "72.4", ist(DAY, 12, 0), ist(DAY, 22, 0)),
                obs(MetricCode.WEIGHT_KG, "70.0", ist(DAY.minusDays(1), 7, 0)));

        MetricWeek weight = weekOf(DashboardDays.weekSeries(observations, WEEK, IST), MetricCode.WEIGHT_KG);

        assertThat(weight.aggregation()).isEqualTo(MetricAggregation.LATEST);
        // 21:00 applies latest, even though 72.4 was the last one typed in. Never a sum (216 kg).
        assertThat(pointOn(weight, DAY).value()).isEqualByComparingTo("71.6");
        assertThat(pointOn(weight, DAY).count()).isEqualTo(3);
        assertThat(pointOn(weight, DAY.minusDays(1)).value()).isEqualByComparingTo("70.0");
    }

    @Test
    @DisplayName("two readings for the same moment: the one entered later wins, whatever the list order")
    void latestTieGoesToTheLaterEntry() {
        Instant sameMoment = ist(DAY, 7, 0);
        ObservationView enteredFirst = obs(MetricCode.WEIGHT_KG, "72.0", sameMoment, ist(DAY, 7, 1));
        ObservationView correction = obs(MetricCode.WEIGHT_KG, "71.0", sameMoment, ist(DAY, 9, 0));

        for (List<ObservationView> order : List.of(List.of(enteredFirst, correction), List.of(correction, enteredFirst))) {
            MetricWeek weight = weekOf(DashboardDays.weekSeries(order, WEEK, IST), MetricCode.WEIGHT_KG);
            assertThat(pointOn(weight, DAY).value()).isEqualByComparingTo("71.0");
            assertThat(DashboardDays.reduce(order).get(0).value()).isEqualByComparingTo("71.0");
        }
    }

    /* ------------------------------------------------------------- gaps and edges */

    @Test
    @DisplayName("a day nobody logged is a null value with count 0 — never a zero; an unlogged metric has no row")
    void unloggedDayIsNullNotZero() {
        List<ObservationView> observations = List.of(obs(MetricCode.STEPS, "5000", ist(DAY, 10, 0)));

        List<MetricWeek> weeks = DashboardDays.weekSeries(observations, WEEK, IST);

        assertThat(weeks).extracting(MetricWeek::code).containsExactly(MetricCode.STEPS);
        MetricWeek steps = weeks.get(0);
        assertThat(steps.days()).extracting(MetricWeek.DayPoint::date).containsExactlyElementsOf(WEEK);
        for (LocalDate day : WEEK.subList(0, 6)) {
            assertThat(pointOn(steps, day).value()).as("value on %s", day).isNull();
            assertThat(pointOn(steps, day).count()).as("count on %s", day).isZero();
        }
        assertThat(pointOn(steps, DAY).value()).isEqualByComparingTo("5000");
        assertThat(pointOn(steps, DAY).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("observations outside the week are dropped, to the nanosecond at either end")
    void outsideTheWeekIsDropped() {
        LocalDate first = WEEK.get(0);
        ObservationView dayBefore = obs(MetricCode.STEPS, "9999", ist(first.minusDays(1), 23, 59));
        ObservationView dayAfter = obs(MetricCode.STEPS, "8888", ist(DAY.plusDays(1), 0, 0));
        ObservationView firstMinute = obs(MetricCode.STEPS, "100", ist(first, 0, 0));
        ObservationView onlyOutside = obs(MetricCode.WATER_ML, "700", ist(first.minusDays(3), 12, 0));

        List<MetricWeek> weeks = DashboardDays.weekSeries(
                List.of(dayAfter, firstMinute, dayBefore, onlyOutside), WEEK, IST);

        // A metric logged only outside the week gets no row at all.
        assertThat(weeks).extracting(MetricWeek::code).containsExactly(MetricCode.STEPS);
        MetricWeek steps = weeks.get(0);
        assertThat(steps.days()).hasSize(7);
        assertThat(pointOn(steps, first).value()).isEqualByComparingTo("100");
        assertThat(pointOn(steps, first).count()).isEqualTo(1);
        assertThat(steps.days()).extracting(MetricWeek.DayPoint::value)
                .filteredOn(v -> v != null)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"));
    }

    @Test
    @DisplayName("onDay + reduce gives exactly the week's point for every metric on every day")
    void onDayReduceEqualsTheWeekPoint() {
        List<ObservationView> observations = List.of(
                obs(MetricCode.STEPS, "4000", ist(WEEK.get(0), 8, 0)),
                obs(MetricCode.STEPS, "1500", ist(WEEK.get(0), 19, 30)),
                obs(MetricCode.STEPS, "6000", ist(WEEK.get(3), 23, 59)),
                obs(MetricCode.WATER_ML, "500", ist(WEEK.get(3), 0, 0)),
                obs(MetricCode.WATER_ML, "250", ist(DAY, 11, 0)),
                obs(MetricCode.WEIGHT_KG, "72.5", ist(WEEK.get(2), 7, 0)),
                obs(MetricCode.WEIGHT_KG, "72.1", ist(WEEK.get(2), 20, 0)),
                obs(MetricCode.SLEEP_MINUTES, "420", ist(DAY, 6, 30)),
                obs(MetricCode.BEDTIME_OFFSET_MIN, "-30", ist(DAY, 6, 30)),
                obs(MetricCode.BEDTIME_OFFSET_MIN, "15", ist(DAY, 6, 45)));

        List<MetricWeek> weeks = DashboardDays.weekSeries(observations, WEEK, IST);

        // Taxonomy order, as reduce() lists a day.
        assertThat(weeks).extracting(MetricWeek::code).containsExactly(
                MetricCode.WEIGHT_KG, MetricCode.STEPS, MetricCode.SLEEP_MINUTES,
                MetricCode.BEDTIME_OFFSET_MIN, MetricCode.WATER_ML);

        for (LocalDate day : WEEK) {
            List<MetricDay> reduced = DashboardDays.reduce(DashboardDays.onDay(observations, day, IST));
            for (MetricWeek week : weeks) {
                MetricWeek.DayPoint point = pointOn(week, day);
                MetricDay metricDay = reduced.stream().filter(m -> m.code() == week.code()).findFirst().orElse(null);
                if (metricDay == null) {
                    assertThat(point.value()).as("%s on %s", week.code(), day).isNull();
                    assertThat(point.count()).as("%s on %s", week.code(), day).isZero();
                } else {
                    assertThat(point.value()).as("%s on %s", week.code(), day).isEqualTo(metricDay.value());
                    assertThat(point.count()).as("%s on %s", week.code(), day).isEqualTo(metricDay.observationCount());
                    assertThat(week.aggregation()).isEqualTo(metricDay.aggregation());
                    assertThat(week.unit()).isEqualTo(metricDay.unit());
                }
            }
        }
        // Spot-check the arithmetic the loop compares, so both sides are not merely equally wrong.
        assertThat(pointOn(weekOf(weeks, MetricCode.STEPS), WEEK.get(0)).value()).isEqualByComparingTo("5500");
        assertThat(pointOn(weekOf(weeks, MetricCode.WEIGHT_KG), WEEK.get(2)).value()).isEqualByComparingTo("72.1");
        assertThat(pointOn(weekOf(weeks, MetricCode.BEDTIME_OFFSET_MIN), DAY).value()).isEqualByComparingTo("15");
    }

    /* ------------------------------------------------------------------- scopes */

    @Test
    @DisplayName("manageScopeNames lists only MANAGE scopes, sorted by name; scopeNames lists every visible one")
    void manageScopeNamesListsOnlyManageSorted() {
        MemberInfo subject = new MemberInfo(MEMBER, UUID.randomUUID(), UUID.randomUUID(), "ADULT", true);
        // Enum order is ACTIVITY, SLEEP, HYDRATION; by name it is ACTIVITY, HYDRATION, SLEEP.
        AccessDecision caregiver = new AccessDecision(subject, Relationship.CAREGIVER, Map.of(
                AccessScope.SLEEP, AccessLevel.MANAGE,
                AccessScope.HYDRATION, AccessLevel.MANAGE,
                AccessScope.ACTIVITY, AccessLevel.MANAGE,
                AccessScope.BODY, AccessLevel.VIEW,
                AccessScope.HABITS, AccessLevel.VIEW));

        assertThat(DashboardDays.manageScopeNames(caregiver)).containsExactly("ACTIVITY", "HYDRATION", "SLEEP");
        assertThat(DashboardDays.scopeNames(caregiver))
                .containsExactly("ACTIVITY", "BODY", "HABITS", "HYDRATION", "SLEEP");

        AccessDecision viewer = new AccessDecision(subject, Relationship.SHARED,
                Map.of(AccessScope.ACTIVITY, AccessLevel.VIEW));
        assertThat(DashboardDays.manageScopeNames(viewer)).isEmpty();

        AccessDecision none = new AccessDecision(subject, Relationship.NONE, Map.of());
        assertThat(DashboardDays.manageScopeNames(none)).isEmpty();
        assertThat(DashboardDays.scopeNames(none)).isEmpty();
    }
}
