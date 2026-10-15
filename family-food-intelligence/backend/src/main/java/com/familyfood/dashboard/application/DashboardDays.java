package com.familyfood.dashboard.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.household.application.HouseholdAccess.HouseholdInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.domain.MetricAggregation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The arithmetic the Today summary and the family overview share: which day is meant, where it
 * starts and ends, and how a day of observations becomes one number per metric. Kept in one
 * place so the two screens cannot disagree about any of it.
 */
final class DashboardDays {

    /** India-first (D-019), and the household table's own default (D-053). */
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private DashboardDays() {
    }

    /** D-053: the household decides where a day starts. */
    static ZoneId zoneOf(Optional<HouseholdInfo> household) {
        return household.map(HouseholdInfo::zone).orElse(DEFAULT_ZONE);
    }

    /**
     * The day asked for, or today where the household lives. A day that has not started yet has
     * nothing in it to show, and an empty future day would read as "nothing logged".
     */
    static LocalDate resolveDate(LocalDate requested, ZoneId zone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw AppException.validation("date", "cannot be in the future");
        }
        return requested;
    }

    /** Local midnight at the start of the day. */
    static Instant startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }

    /**
     * The last instant of the day, not the next midnight: the observation query is inclusive at
     * both ends, so a reading at exactly midnight would otherwise count in two days.
     */
    static Instant endOf(LocalDate day, ZoneId zone) {
        return day.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
    }

    /**
     * One row per metric that has data, in the taxonomy's own order (body, activity, sleep,
     * hydration, nutrition, clinical, wellbeing). A metric with no observation produces no row —
     * never a zero.
     */
    static List<MetricDay> reduce(List<ObservationView> observations) {
        Map<MetricCode, Accumulator> byMetric = new EnumMap<>(MetricCode.class);
        for (ObservationView observation : observations) {
            byMetric.computeIfAbsent(observation.metricCode(), Accumulator::new).add(observation);
        }
        return byMetric.values().stream().map(Accumulator::toDay).toList();
    }

    /** {@code count} consecutive days ending on {@code last}, oldest first. */
    static List<LocalDate> daysEndingOn(LocalDate last, int count) {
        return last.minusDays(count - 1L).datesUntil(last.plusDays(1)).toList();
    }

    /** The observations that fall on one household-local day — the same bounds as {@link #startOf}/{@link #endOf}. */
    static List<ObservationView> onDay(List<ObservationView> observations, LocalDate day, ZoneId zone) {
        return observations.stream().filter(o -> dayOf(o, zone).equals(day)).toList();
    }

    /**
     * One {@link MetricWeek} per metric with data on any of {@code days}, in the taxonomy's order,
     * each day reduced by the same accumulator as {@link #reduce} — so a day in the week and that
     * day's {@link MetricDay} cannot disagree. Days with nothing logged are {@code null} points.
     */
    static List<MetricWeek> weekSeries(List<ObservationView> observations, List<LocalDate> days, ZoneId zone) {
        Map<MetricCode, Map<LocalDate, Accumulator>> byMetric = new EnumMap<>(MetricCode.class);
        for (ObservationView observation : observations) {
            LocalDate day = dayOf(observation, zone);
            if (days.contains(day)) {
                byMetric.computeIfAbsent(observation.metricCode(), code -> new HashMap<>())
                        .computeIfAbsent(day, d -> new Accumulator(observation.metricCode()))
                        .add(observation);
            }
        }
        List<MetricWeek> weeks = new ArrayList<>(byMetric.size());
        byMetric.forEach((code, perDay) -> weeks.add(new MetricWeek(code, code.category(), code.canonicalUnit(),
                code.aggregation(), days.stream().map(day -> pointOf(day, perDay.get(day))).toList())));
        return weeks;
    }

    /** Whether the decision lets the caller see any metric category at all. */
    static boolean seesAnyMetric(AccessDecision decision) {
        return decision.viewableScopes().stream().anyMatch(s -> MetricCategory.of(s).isPresent());
    }

    /** The scopes the caller may see, by name, sorted — a stable list for a client to test. */
    static List<String> scopeNames(AccessDecision decision) {
        return decision.viewableScopes().stream().map(AccessScope::name).sorted().toList();
    }

    /** The scopes the caller may log for the subject, by name, sorted. */
    static List<String> manageScopeNames(AccessDecision decision) {
        return decision.viewableScopes().stream().filter(decision::canManage).map(AccessScope::name).sorted().toList();
    }

    private static LocalDate dayOf(ObservationView observation, ZoneId zone) {
        return LocalDate.ofInstant(observation.effectiveAt(), zone);
    }

    private static MetricWeek.DayPoint pointOf(LocalDate day, Accumulator accumulator) {
        if (accumulator == null) {
            return new MetricWeek.DayPoint(day, null, 0);
        }
        MetricDay reduced = accumulator.toDay();
        return new MetricWeek.DayPoint(day, reduced.value(), reduced.observationCount());
    }

    /* ------------------------------------------------------------------ internals */

    private static final class Accumulator {

        private final MetricCode code;
        private BigDecimal total;
        private ObservationView newest;
        private int count;

        Accumulator(MetricCode code) {
            this.code = code;
        }

        void add(ObservationView observation) {
            count++;
            total = total == null ? observation.value() : total.add(observation.value());
            if (newest == null || isNewer(observation, newest)) {
                newest = observation;
            }
        }

        MetricDay toDay() {
            MetricAggregation aggregation = code.aggregation();
            BigDecimal value = aggregation == MetricAggregation.SUM ? total : newest.value();
            return new MetricDay(code, code.category(), code.canonicalUnit(), aggregation, value, count,
                    newest.effectiveAt());
        }

        /** Newer by when the reading applies; a tie goes to the one entered later. */
        private static boolean isNewer(ObservationView candidate, ObservationView current) {
            int byEffective = candidate.effectiveAt().compareTo(current.effectiveAt());
            if (byEffective != 0) {
                return byEffective > 0;
            }
            Instant a = candidate.recordedAt();
            Instant b = current.recordedAt();
            return a != null && (b == null || a.isAfter(b));
        }
    }
}
