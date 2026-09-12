package com.familyfood.insights.application;

import com.familyfood.insights.domain.Insight;
import com.familyfood.insights.domain.Insight.Availability;
import com.familyfood.insights.domain.Insight.Evidence;
import com.familyfood.insights.domain.InsightType;
import com.familyfood.insights.domain.InsightWindow;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.WellnessAccess;
import com.familyfood.wellness.domain.MetricCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic insight generation over recorded observations.
 *
 * <p>Three rules govern everything in this class, and they are the reason it is worth having
 * as its own module rather than a few helper methods on a dashboard endpoint:
 *
 * <ol>
 *   <li><b>Every number is computed here, from persisted data.</b> No threshold is invented,
 *       no norm is assumed, and nothing is compared against a population reference — that
 *       would be interpretation, which needs a citable standard (D-037). A trend compares a
 *       person against their own earlier data, which is a fact about their log.</li>
 *   <li><b>Insufficient data is an answer, not a gap to fill.</b> Below
 *       {@link InsightWindow#minimumDays()} the module says how little it has rather than
 *       computing a percentage over two data points. Same posture as {@code
 *       INSUFFICIENT_DATA} in scoring (D-024).</li>
 *   <li><b>Reads go through {@link WellnessAccess}</b>, so the D-038 visibility rules apply.
 *       An insight about another member can only be built from data the caller may already
 *       see; there is no aggregate back door.</li>
 * </ol>
 *
 * <p>An AI layer may later rephrase these sentences. It may not produce them — D-011, made
 * checkable by the evidence list on every {@link Insight}.
 */
@Service
public class InsightService {

    /** Below this relative change, a movement is noise and is reported as "about the same". */
    private static final BigDecimal MATERIAL_CHANGE_PCT = new BigDecimal("5");

    private final WellnessAccess wellness;
    private final Clock clock;

    public InsightService(WellnessAccess wellness, Clock clock) {
        this.wellness = wellness;
        this.clock = clock;
    }

    /**
     * Everything this module can currently say about one member, in one window.
     *
     * <p>Returns only insights that cleared their evidence threshold, plus — when nothing
     * did — a single {@link InsightType#DATA_COVERAGE} statement, so the caller always has
     * something honest to render rather than an empty list it has to explain itself.
     */
    @Transactional(readOnly = true)
    public List<Insight> forMember(UUID callerAccountId, UUID subjectMemberId, InsightWindow window) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window.length());
        Instant priorStart = windowStart.minus(window.length());

        List<ObservationView> current = wellness.window(callerAccountId, subjectMemberId, windowStart, now);
        List<ObservationView> prior = wellness.window(callerAccountId, subjectMemberId, priorStart, windowStart);

        if (current.isEmpty()) {
            return List.of(Insight.noData(InsightType.DATA_COVERAGE, subjectMemberId, window,
                    "Nothing recorded in this period yet.", now));
        }

        List<Insight> insights = new ArrayList<>();
        addTrend(insights, subjectMemberId, window, current, prior, MetricCode.ENERGY_KCAL,
                InsightType.NUTRITION_TREND, "Energy", now);
        addTrend(insights, subjectMemberId, window, current, prior, MetricCode.PROTEIN_G,
                InsightType.NUTRITION_TREND, "Protein", now);
        addTrend(insights, subjectMemberId, window, current, prior, MetricCode.SODIUM_MG,
                InsightType.NUTRITION_TREND, "Sodium", now);
        addTrend(insights, subjectMemberId, window, current, prior, MetricCode.FIBRE_G,
                InsightType.NUTRITION_TREND, "Fibre", now);
        addTrend(insights, subjectMemberId, window, current, prior, MetricCode.WATER_ML,
                InsightType.HYDRATION_TREND, "Water", now);
        addTrend(insights, subjectMemberId, window, current, prior, MetricCode.STEPS,
                InsightType.ACTIVITY_TREND, "Steps", now);

        if (insights.isEmpty()) {
            insights.add(coverage(subjectMemberId, window, current, now));
        }
        return insights;
    }

    /**
     * A trend for one metric, or nothing.
     *
     * <p>Nothing is the common case early on, and that is deliberate. The method returns
     * without adding an insight whenever the evidence would not support one — too few days,
     * no prior period to compare against, or a prior total of zero (where a percentage
     * change is undefined, not infinite).
     */
    private void addTrend(List<Insight> out,
                          UUID subject,
                          InsightWindow window,
                          List<ObservationView> current,
                          List<ObservationView> prior,
                          MetricCode metric,
                          InsightType type,
                          String label,
                          Instant now) {
        List<ObservationView> currentForMetric = forMetric(current, metric);
        if (currentForMetric.isEmpty()) {
            return;
        }

        int days = distinctDays(currentForMetric);
        BigDecimal currentTotal = total(currentForMetric);

        if (days < window.minimumDays()) {
            // Enough to acknowledge, not enough to trend. Saying so is more useful than
            // silence, and far more useful than a percentage over two data points.
            out.add(new Insight(InsightType.DATA_COVERAGE, subject, window, Availability.PARTIAL,
                    "%s logged on %d of the last %d days.".formatted(label, days, expectedDays(window)),
                    "A little more logging and we can show how this is moving.",
                    List.of(new Evidence(label + " total", currentTotal,
                            metric.canonicalUnit(), metric.category(), days)),
                    now));
            return;
        }

        List<ObservationView> priorForMetric = forMetric(prior, metric);
        BigDecimal priorTotal = total(priorForMetric);

        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence("This period", currentTotal, metric.canonicalUnit(),
                metric.category(), days));

        if (priorForMetric.isEmpty() || priorTotal.signum() == 0) {
            // No baseline. State the total — which is a fact — and stop short of a
            // comparison, which would not be.
            out.add(new Insight(type, subject, window, Availability.SUFFICIENT,
                    "%s: %s %s across %d days.".formatted(label, trim(currentTotal),
                            metric.canonicalUnit(), days),
                    "No earlier period to compare against yet.",
                    evidence, now));
            return;
        }

        evidence.add(new Evidence("Previous period", priorTotal, metric.canonicalUnit(),
                metric.category(), distinctDays(priorForMetric)));

        // Compare daily averages, not totals: an eight-day window against a six-day one
        // would otherwise report a rise that is only a difference in logging.
        BigDecimal currentAvg = average(currentTotal, days);
        BigDecimal priorAvg = average(priorTotal, distinctDays(priorForMetric));
        BigDecimal changePct = percentChange(priorAvg, currentAvg);

        String headline;
        if (changePct.abs().compareTo(MATERIAL_CHANGE_PCT) < 0) {
            headline = "%s is about the same as the previous period.".formatted(label);
        } else {
            headline = "%s per day is %s %s%% compared with the previous period."
                    .formatted(label, changePct.signum() > 0 ? "up" : "down", trim(changePct.abs()));
        }

        out.add(new Insight(type, subject, window, Availability.SUFFICIENT, headline,
                "Based on %s %s a day now against %s a day before."
                        .formatted(trim(currentAvg), metric.canonicalUnit(), trim(priorAvg)),
                evidence, now));
    }

    /** What the module knows, when it cannot yet say anything about how it is moving. */
    private Insight coverage(UUID subject, InsightWindow window,
                             List<ObservationView> current, Instant now) {
        int days = distinctDays(current);
        Set<MetricCode> metrics = current.stream()
                .map(ObservationView::metricCode)
                .collect(Collectors.toCollection(HashSet::new));

        return new Insight(InsightType.DATA_COVERAGE, subject, window, Availability.PARTIAL,
                "%d %s recorded across %d %s.".formatted(
                        metrics.size(), metrics.size() == 1 ? "measurement" : "measurements",
                        days, days == 1 ? "day" : "days"),
                "Keep logging and trends will appear here.",
                current.stream().limit(3)
                        .map(o -> new Evidence(o.metricCode().name(), o.value(), o.unit(),
                                o.metricCategory(), 1))
                        .toList(),
                now);
    }

    /* ---------------------------------------------------------------- arithmetic */

    private static List<ObservationView> forMetric(List<ObservationView> all, MetricCode metric) {
        return all.stream().filter(o -> o.metricCode() == metric).toList();
    }

    private static BigDecimal total(List<ObservationView> observations) {
        return observations.stream()
                .map(ObservationView::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Distinct calendar days with at least one observation.
     *
     * <p>The denominator for every average, so that three weigh-ins on one day do not read
     * as three days of data.
     */
    private static int distinctDays(List<ObservationView> observations) {
        Set<LocalDate> days = new HashSet<>();
        for (ObservationView o : observations) {
            days.add(o.effectiveAt().atZone(ZoneOffset.UTC).toLocalDate());
        }
        return days.size();
    }

    private static int expectedDays(InsightWindow window) {
        return (int) window.length().toDays();
    }

    private static BigDecimal average(BigDecimal total, int days) {
        if (days <= 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from.signum() == 0) return BigDecimal.ZERO;
        return to.subtract(from)
                .multiply(BigDecimal.valueOf(100))
                .divide(from, 1, RoundingMode.HALF_UP);
    }

    /** Drops a trailing ".0" so a whole number reads as one. */
    private static String trim(BigDecimal value) {
        BigDecimal scaled = value.setScale(1, RoundingMode.HALF_UP);
        return scaled.stripTrailingZeros().scale() <= 0
                ? scaled.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : scaled.toPlainString();
    }

    /** Exposed for the household view: which members the caller can say anything about. */
    @Transactional(readOnly = true)
    public Map<UUID, List<Insight>> forMembers(UUID callerAccountId,
                                               List<UUID> subjectMemberIds,
                                               InsightWindow window) {
        return subjectMemberIds.stream()
                .collect(Collectors.toMap(id -> id, id -> forMember(callerAccountId, id, window)));
    }
}
