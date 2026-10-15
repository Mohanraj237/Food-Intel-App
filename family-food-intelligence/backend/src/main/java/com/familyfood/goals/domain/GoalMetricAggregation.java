package com.familyfood.goals.domain;

import com.familyfood.wellness.domain.MetricAggregation;
import com.familyfood.wellness.domain.MetricCode;

/**
 * How a window of observations becomes the one number a target is compared against.
 *
 * <p>The rule itself now lives on the metric ({@link MetricCode#aggregation()}), because the
 * Today summary, the family overview and insights need exactly the same answer and three copies
 * would drift. This type remains as the goal module's name for it, so that
 * {@code GoalProgressView.aggregation} keeps its published values ({@code SUM}/{@code LATEST}).
 */
public enum GoalMetricAggregation {

    /** Total the window — for metrics where each observation adds to the others. */
    SUM,

    /** Take the most recent reading in the window — for point-in-time measurements. */
    LATEST;

    public static GoalMetricAggregation forMetric(MetricCode metric) {
        return metric.aggregation() == MetricAggregation.SUM ? SUM : LATEST;
    }
}
