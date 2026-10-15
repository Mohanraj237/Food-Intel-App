package com.familyfood.dashboard.application;

import com.familyfood.access.domain.AccessScope;
import com.familyfood.dashboard.application.FamilyOverviewView.MemberCard;
import com.familyfood.dashboard.application.FamilySummary.FamilyCoverage;
import com.familyfood.dashboard.application.FamilySummary.FamilyDay;
import com.familyfood.dashboard.application.FamilySummary.FamilyMetric;
import com.familyfood.habits.application.HabitService.HabitDay;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The family summary (D-054), computed from the caller's finished cards and nothing else.
 *
 * <p>That is the whole privacy argument: a card holds only what the access policy lets the caller
 * see, so a sum over cards can never contain a number the caller could not already read on one of
 * them (D-038's aggregate rule, architecture §11). This class takes no ids, reads no repository and
 * cannot be handed anything wider.
 *
 * <p>What it refuses to produce, by construction rather than by care at the call site:
 * <ul>
 *   <li>any per-person figure, ordering, "most" or "least" — the output has no member dimension
 *       beyond the list of who is included (D-051);</li>
 *   <li>any combination of health readings, body measurements or mood across people — only
 *       {@link #SUMMED} metrics are added up (D-036, D-037);</li>
 *   <li>a kcal total — meals are counted as coverage, so a child's energy intake never becomes a
 *       family headline (UXD-08);</li>
 *   <li>a zero for a day nobody logged — that day's total is {@code null}.</li>
 * </ul>
 */
final class FamilySummaries {

    /** A summary of one person is only their own card again. */
    static final int MINIMUM_PEOPLE = 2;

    /** The only metrics ever added up across people, in the order they are shown. */
    static final List<MetricCode> SUMMED = List.of(MetricCode.WATER_ML, MetricCode.STEPS, MetricCode.SLEEP_MINUTES);

    /** Visibility of any of these makes a member part of the summary. Readings, body and mood never do. */
    private static final Set<String> COMBINABLE_SCOPES = EnumSet.of(
                    AccessScope.HYDRATION, AccessScope.ACTIVITY, AccessScope.SLEEP,
                    AccessScope.NUTRITION, AccessScope.HABITS).stream()
            .map(AccessScope::name)
            .collect(Collectors.toUnmodifiableSet());

    private FamilySummaries() {
    }

    /**
     * @param cards the caller's cards, in the order they are shown
     * @param days  the overview's week, oldest first
     * @return {@code null} when fewer than {@link #MINIMUM_PEOPLE} people are included
     */
    static FamilySummary summarise(List<MemberCard> cards, List<LocalDate> days) {
        List<MemberCard> included = cards.stream().filter(FamilySummaries::combinable).toList();
        if (included.size() < MINIMUM_PEOPLE) {
            return null;
        }
        List<FamilyMetric> metrics = SUMMED.stream()
                .map(code -> metric(code, included, days))
                .flatMap(Optional::stream)
                .toList();
        return new FamilySummary(included.stream().map(MemberCard::memberId).toList(),
                metrics, meals(included, days), habits(included, days));
    }

    /* ------------------------------------------------------------------ internals */

    private static boolean combinable(MemberCard card) {
        return card.visibleScopes().stream().anyMatch(COMBINABLE_SCOPES::contains);
    }

    private static boolean sees(MemberCard card, MetricCategory category) {
        return card.visibleScopes().contains(category.scope().name());
    }

    /** Empty when nobody included shares the metric's category with the caller. */
    private static Optional<FamilyMetric> metric(MetricCode code, List<MemberCard> included, List<LocalDate> days) {
        List<MemberCard> eligible = included.stream().filter(card -> sees(card, code.category())).toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        List<FamilyDay> perDay = days.stream().map(day -> {
            List<BigDecimal> logged = eligible.stream()
                    .map(card -> valueOn(card, code, day))
                    .filter(Objects::nonNull)
                    .toList();
            BigDecimal total = logged.isEmpty() ? null : logged.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return new FamilyDay(day, total, logged.size());
        }).toList();
        return Optional.of(new FamilyMetric(code, code.canonicalUnit(), eligible.size(), perDay));
    }

    /** How many people had any meal nutrition logged each day — a count of people, never a kcal total. */
    private static FamilyCoverage meals(List<MemberCard> included, List<LocalDate> days) {
        List<MemberCard> eligible = included.stream().filter(card -> sees(card, MetricCategory.NUTRITION)).toList();
        if (eligible.isEmpty()) {
            return null;
        }
        List<FamilyDay> perDay = days.stream().map(day -> {
            int contributors = (int) eligible.stream().filter(card -> ateOn(card, day)).count();
            return new FamilyDay(day, null, contributors);
        }).toList();
        return new FamilyCoverage(eligible.size(), perDay);
    }

    /** Habits due and done each day, summed over the people whose habits the caller can see. */
    private static List<HabitDay> habits(List<MemberCard> included, List<LocalDate> days) {
        List<List<HabitDay>> weeks = included.stream().map(MemberCard::habitsWeek).filter(Objects::nonNull).toList();
        if (weeks.isEmpty()) {
            return null;
        }
        return days.stream().map(day -> {
            int active = 0;
            int due = 0;
            int done = 0;
            for (List<HabitDay> week : weeks) {
                for (HabitDay d : week) {
                    if (d.date().equals(day)) {
                        active += d.active();
                        due += d.due();
                        done += d.done();
                    }
                }
            }
            return new HabitDay(day, active, due, done);
        }).toList();
    }

    private static BigDecimal valueOn(MemberCard card, MetricCode code, LocalDate day) {
        return card.week().stream()
                .filter(w -> w.code() == code)
                .flatMap(w -> w.days().stream())
                .filter(p -> p.date().equals(day))
                .map(MetricWeek.DayPoint::value)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static boolean ateOn(MemberCard card, LocalDate day) {
        return card.week().stream()
                .filter(w -> w.category() == MetricCategory.NUTRITION)
                .flatMap(w -> w.days().stream())
                .anyMatch(p -> p.date().equals(day) && p.value() != null);
    }
}
