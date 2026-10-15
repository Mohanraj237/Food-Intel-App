package com.familyfood.wellness.domain;

/**
 * How a window of observations of one metric becomes the one number shown or compared.
 *
 * <p>This exists because the obvious answer is wrong for half the taxonomy, and wrong in the
 * worst possible way — silently. Water and steps accumulate: 300 ml plus 500 ml is 800 ml.
 * Weight does not: totalling two weigh-ins on one day reports 144 kg, which is not a
 * measurement of anything, yet renders exactly like a real number.
 *
 * <p>Stated on the metric (see {@link MetricCode#aggregation()}) so that goals, the Today
 * summary, the family overview and insights cannot disagree about it. It was first written in
 * the goals module; it moved here in W1 when a second and third reader needed it.
 */
public enum MetricAggregation {

    /** Total the window — each observation adds to the others. */
    SUM,

    /** Take the most recent reading in the window — a point-in-time measurement. */
    LATEST
}
