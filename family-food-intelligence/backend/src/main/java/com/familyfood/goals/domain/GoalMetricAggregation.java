package com.familyfood.goals.domain;

import com.familyfood.wellness.domain.MetricCode;

/**
 * How a window of observations becomes the one number a target is compared against.
 *
 * <p>This type exists because the obvious answer is wrong for half the taxonomy, and wrong in
 * the worst possible way — silently. Water and steps accumulate: 300 ml plus 500 ml is 800 ml
 * towards a daily target. Weight does not: totalling two weigh-ins on one day reports 144 kg,
 * which is not a measurement of anything, yet renders exactly like a real number next to a
 * real target.
 *
 * <p>Stated per metric rather than left to whichever read a service happened to call, so that
 * adding a metric forces the question to be answered once, in the open.
 */
public enum GoalMetricAggregation {

    /** Total the window — for metrics where each observation adds to the others. */
    SUM,

    /** Take the most recent reading in the window — for point-in-time measurements. */
    LATEST;

    public static GoalMetricAggregation forMetric(MetricCode metric) {
        return switch (metric) {
            // Clock-like scalars: the latest bedtime is a fact, a total of bedtimes is noise.
            case BEDTIME_OFFSET_MIN, WAKE_OFFSET_MIN -> LATEST;
            default -> switch (metric.category()) {
                case NUTRITION, HYDRATION, ACTIVITY, SLEEP -> SUM;
                case BODY, CLINICAL, WELLBEING -> LATEST;
            };
        };
    }
}
