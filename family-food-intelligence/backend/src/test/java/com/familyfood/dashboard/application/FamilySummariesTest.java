package com.familyfood.dashboard.application;

import com.familyfood.access.domain.AccessScope;
import com.familyfood.dashboard.application.FamilyOverviewView.MemberCard;
import com.familyfood.dashboard.application.FamilySummary.FamilyDay;
import com.familyfood.dashboard.application.FamilySummary.FamilyMetric;
import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.habits.application.HabitService.HabitDay;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The family summary (D-054) is a pure function of the caller's cards.
 *
 * <p>Cards are built by hand here, including some the service would never build (a card with a
 * week row for a category it cannot see), so that the summary's own rules are what is tested:
 * who is included, who is eligible for each metric, what is never combined (health readings,
 * body, mood; a kcal total), and that a day nobody logged has no total rather than a zero.
 */
class FamilySummariesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 12);
    private static final List<LocalDate> WEEK = DAY.minusDays(6).datesUntil(DAY.plusDays(1)).toList();

    private static final List<String> EVERY_SCOPE = Arrays.stream(AccessScope.values())
            .map(AccessScope::name).sorted().toList();

    /* ------------------------------------------------------------------ builders */

    private static MemberCard self(UUID id, List<MetricWeek> week, List<HabitDay> habitsWeek) {
        return card(id, "SELF", EVERY_SCOPE, week, habitsWeek);
    }

    private static MemberCard card(UUID id, String relationship, List<String> scopes,
                                   List<MetricWeek> week, List<HabitDay> habitsWeek) {
        return new MemberCard(id, "label-" + id.toString().substring(0, 4), "ADULT",
                "SELF".equals(relationship), relationship, ExperienceTier.ADULT, scopes,
                List.of(), null, null, false, List.of(), week, List.of(), habitsWeek);
    }

    private static MemberCard none(UUID id) {
        return new MemberCard(id, "label", "ADULT", false, "NONE", ExperienceTier.ADULT, List.of(),
                List.of(), null, null, false, List.of(), List.of(), List.of(), null);
    }

    /** One metric across the week: a value per day, {@code null} for a day with nothing logged. */
    private static MetricWeek series(MetricCode code, String... perDay) {
        assertThat(perDay).hasSize(7);
        List<MetricWeek.DayPoint> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(perDay[i] == null
                    ? new MetricWeek.DayPoint(WEEK.get(i), null, 0)
                    : new MetricWeek.DayPoint(WEEK.get(i), new BigDecimal(perDay[i]), 1));
        }
        return new MetricWeek(code, code.category(), code.canonicalUnit(), code.aggregation(), days);
    }

    private static List<HabitDay> habitWeek(int active, int due, int done) {
        return WEEK.stream().map(d -> new HabitDay(d, active, due, done)).toList();
    }

    private static FamilyMetric metric(FamilySummary summary, MetricCode code) {
        return summary.metrics().stream().filter(m -> m.code() == code).findFirst()
                .orElseThrow(() -> new AssertionError("no family metric " + code));
    }

    private static List<BigDecimal> totals(List<FamilyDay> days) {
        return days.stream().map(FamilyDay::total).toList();
    }

    private static List<Integer> contributors(List<FamilyDay> days) {
        return days.stream().map(FamilyDay::contributors).toList();
    }

    /* ------------------------------------------------------------- who is included */

    @Test
    @DisplayName("fewer than two people included gives no summary at all")
    void fewerThanTwoIncludedIsNull() {
        UUID me = UUID.randomUUID();
        MemberCard mine = self(me, List.of(series(MetricCode.STEPS, "1", null, null, null, null, null, null)), null);

        assertThat(FamilySummaries.summarise(List.of(), WEEK)).isNull();
        assertThat(FamilySummaries.summarise(List.of(mine), WEEK)).isNull();
        // Someone who shares nothing is on the screen but not in the summary.
        assertThat(FamilySummaries.summarise(List.of(mine, none(UUID.randomUUID())), WEEK)).isNull();
        // Nor is someone who shares only a reading category.
        assertThat(FamilySummaries.summarise(List.of(mine,
                card(UUID.randomUUID(), "SHARED", List.of("CLINICAL"), List.of(), null)), WEEK)).isNull();
    }

    @Test
    @DisplayName("includedMemberIds keeps the cards' order and skips who is not included")
    void includedMemberIdsKeepCardOrder() {
        UUID me = UUID.randomUUID();
        UUID nobody = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID readingsOnly = UUID.randomUUID();
        UUID sharesWater = UUID.randomUUID();
        UUID sharesHabits = UUID.randomUUID();

        FamilySummary summary = FamilySummaries.summarise(List.of(
                self(me, List.of(), null),
                none(nobody),
                card(child, "GUARDIAN", EVERY_SCOPE, List.of(), null),
                card(readingsOnly, "SHARED", List.of("BODY", "CLINICAL", "WELLBEING"), List.of(), null),
                card(sharesWater, "SHARED", List.of("HYDRATION"), List.of(), null),
                card(sharesHabits, "SHARED", List.of("HABITS"), List.of(), habitWeek(0, 0, 0))), WEEK);

        assertThat(summary).isNotNull();
        assertThat(summary.includedMemberIds()).containsExactly(me, child, sharesWater, sharesHabits);
    }

    /* ----------------------------------------------------------- eligibility */

    @Test
    @DisplayName("a card that cannot see a category is neither eligible nor counted for it, even with a row for it")
    void cardLackingTheCategoryIsNotEligible() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        MemberCard mine = self(me, List.of(
                series(MetricCode.STEPS, "1000", null, null, null, null, null, null)), null);
        // Hypothetical: a card that shares only water but carries a steps row. The service never
        // builds one; the summary must not trust the row either way.
        MemberCard sharesWater = card(other, "SHARED", List.of("HYDRATION"), List.of(
                series(MetricCode.STEPS, "5000", "5000", "5000", "5000", "5000", "5000", "5000"),
                series(MetricCode.WATER_ML, null, null, null, null, null, null, "750")), null);

        FamilySummary summary = FamilySummaries.summarise(List.of(mine, sharesWater), WEEK);

        FamilyMetric steps = metric(summary, MetricCode.STEPS);
        assertThat(steps.eligiblePeople()).isEqualTo(1);
        assertThat(totals(steps.days())).containsExactly(new BigDecimal("1000"), null, null, null, null, null, null);
        assertThat(contributors(steps.days())).containsExactly(1, 0, 0, 0, 0, 0, 0);

        FamilyMetric water = metric(summary, MetricCode.WATER_ML);
        assertThat(water.eligiblePeople()).isEqualTo(2);
        assertThat(totals(water.days())).containsExactly(null, null, null, null, null, null, new BigDecimal("750"));
        assertThat(contributors(water.days())).containsExactly(0, 0, 0, 0, 0, 0, 1);

        // Sleep: only I can be seen, and I logged none.
        FamilyMetric sleep = metric(summary, MetricCode.SLEEP_MINUTES);
        assertThat(sleep.eligiblePeople()).isEqualTo(1);
        assertThat(totals(sleep.days())).containsOnlyNulls();
    }

    @Test
    @DisplayName("a metric nobody included can see is left out, and so are meals")
    void metricOmittedWhenNobodySeesItsCategory() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // A caregiver's view of two people who each share only activity with them.
        FamilySummary summary = FamilySummaries.summarise(List.of(
                card(a, "SHARED", List.of("ACTIVITY"), List.of(
                        series(MetricCode.STEPS, null, null, null, null, null, "2000", null)), null),
                card(b, "SHARED", List.of("ACTIVITY"), List.of(), null)), WEEK);

        assertThat(summary.metrics()).extracting(FamilyMetric::code).containsExactly(MetricCode.STEPS);
        assertThat(metric(summary, MetricCode.STEPS).eligiblePeople()).isEqualTo(2);
        assertThat(summary.meals()).isNull();
        assertThat(summary.habits()).isNull();
    }

    /* ------------------------------------------------------- never combined */

    @Test
    @DisplayName("readings, body and mood are never combined; sharing only those does not include a person")
    void clinicalBodyWellbeingAreNeverCombined() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID readingsOnly = UUID.randomUUID();
        List<MetricWeek> sensitive = List.of(
                series(MetricCode.WEIGHT_KG, "70", null, null, null, null, null, "71"),
                series(MetricCode.SYSTOLIC_MMHG, "120", null, null, null, null, null, null),
                series(MetricCode.MOOD_SCORE, "4", null, null, null, null, null, null));

        FamilySummary summary = FamilySummaries.summarise(List.of(
                self(me, sensitive, null),
                card(other, "SHARED", List.of("ACTIVITY", "BODY", "CLINICAL", "WELLBEING"), sensitive, null),
                card(readingsOnly, "SHARED", List.of("BODY", "CLINICAL", "WELLBEING"), sensitive, null)), WEEK);

        assertThat(summary.includedMemberIds()).containsExactly(me, other);
        // Only the three summable metrics, in their fixed order — never weight, blood pressure or mood.
        assertThat(summary.metrics()).extracting(FamilyMetric::code)
                .containsExactly(MetricCode.WATER_ML, MetricCode.STEPS, MetricCode.SLEEP_MINUTES);
        assertThat(summary.metrics()).extracting(m -> m.code().category())
                .doesNotContain(MetricCategory.BODY, MetricCategory.CLINICAL, MetricCategory.WELLBEING);

        // Two people who share only readings with each other's viewer get nothing combined.
        assertThat(FamilySummaries.summarise(List.of(
                card(other, "SHARED", List.of("BODY", "CLINICAL", "WELLBEING"), sensitive, null),
                card(readingsOnly, "SHARED", List.of("BODY", "CLINICAL", "WELLBEING"), sensitive, null)), WEEK))
                .isNull();
    }

    /* ------------------------------------------------------------ arithmetic */

    @Test
    @DisplayName("each day: total over who logged, contributors counted, eligible fixed; nobody logged is a null total")
    void contributorsEligibleAndTotals() {
        UUID me = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        FamilySummary summary = FamilySummaries.summarise(List.of(
                self(me, List.of(series(MetricCode.STEPS, "1000", null, "2000", null, null, null, "500")), null),
                card(a, "SHARED", List.of("ACTIVITY"),
                        List.of(series(MetricCode.STEPS, "3000", null, null, null, null, null, null)), null),
                // Eligible, but logged nothing all week: no row at all.
                card(b, "CAREGIVER", List.of("ACTIVITY", "HYDRATION"), List.of(), null)), WEEK);

        FamilyMetric steps = metric(summary, MetricCode.STEPS);
        assertThat(steps.unit()).isEqualTo("steps");
        assertThat(steps.eligiblePeople()).isEqualTo(3);
        assertThat(steps.days()).extracting(FamilyDay::date).containsExactlyElementsOf(WEEK);
        assertThat(totals(steps.days())).containsExactly(
                new BigDecimal("4000"), null, new BigDecimal("2000"), null, null, null, new BigDecimal("500"));
        assertThat(contributors(steps.days())).containsExactly(2, 0, 1, 0, 0, 0, 1);

        // Water: me and b can be seen; neither logged any, so every day is a gap, not a 0.
        FamilyMetric water = metric(summary, MetricCode.WATER_ML);
        assertThat(water.eligiblePeople()).isEqualTo(2);
        assertThat(totals(water.days())).containsOnlyNulls();
        assertThat(contributors(water.days())).containsOnly(0);
    }

    @Test
    @DisplayName("meals are coverage — people with any nutrition that day — and never carry a kcal total")
    void mealsCoverageHasNullTotals() {
        UUID me = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        FamilySummary summary = FamilySummaries.summarise(List.of(
                // Two nutrition metrics on the same day are still one person who ate.
                self(me, List.of(
                        series(MetricCode.ENERGY_KCAL, "600", "450", null, null, null, null, "700"),
                        series(MetricCode.PROTEIN_G, "20", null, null, "15", null, null, "30")), null),
                card(a, "CAREGIVER", List.of("NUTRITION"), List.of(
                        series(MetricCode.ENERGY_KCAL, "300", null, null, null, null, null, null)), null),
                // Does not share nutrition; its hypothetical row must not count.
                card(b, "SHARED", List.of("ACTIVITY"), List.of(
                        series(MetricCode.ENERGY_KCAL, "900", "900", "900", "900", "900", "900", "900")), null)), WEEK);

        FamilySummary.FamilyCoverage meals = summary.meals();
        assertThat(meals).isNotNull();
        assertThat(meals.eligiblePeople()).isEqualTo(2);
        assertThat(meals.days()).extracting(FamilyDay::date).containsExactlyElementsOf(WEEK);
        assertThat(totals(meals.days())).containsOnlyNulls();
        assertThat(contributors(meals.days())).containsExactly(2, 1, 0, 1, 0, 0, 1);
        // And no nutrition metric is ever summed as a family figure.
        assertThat(summary.metrics()).extracting(m -> m.code().category()).doesNotContain(MetricCategory.NUTRITION);
    }

    @Test
    @DisplayName("habits are summed day by day over the people whose habits are visible; null when nobody's are")
    void habitsAreSummed() {
        UUID me = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        List<HabitDay> childWeek = new ArrayList<>(habitWeek(1, 1, 0));
        childWeek.set(6, new HabitDay(DAY, 1, 1, 1));

        FamilySummary summary = FamilySummaries.summarise(List.of(
                self(me, List.of(), habitWeek(2, 2, 1)),
                card(child, "GUARDIAN", EVERY_SCOPE, List.of(), childWeek),
                card(a, "SHARED", List.of("ACTIVITY"), List.of(), null)), WEEK);

        assertThat(summary.habits()).hasSize(7);
        assertThat(summary.habits()).extracting(HabitDay::date).containsExactlyElementsOf(WEEK);
        assertThat(summary.habits().subList(0, 6)).allSatisfy(d -> {
            assertThat(d.active()).isEqualTo(3);
            assertThat(d.due()).isEqualTo(3);
            assertThat(d.done()).isEqualTo(1);
        });
        assertThat(summary.habits().get(6)).isEqualTo(new HabitDay(DAY, 3, 3, 2));

        FamilySummary noHabits = FamilySummaries.summarise(List.of(
                card(me, "SHARED", List.of("ACTIVITY"), List.of(), null),
                card(a, "SHARED", List.of("SLEEP"), List.of(), null)), WEEK);
        assertThat(noHabits.habits()).isNull();
    }
}
