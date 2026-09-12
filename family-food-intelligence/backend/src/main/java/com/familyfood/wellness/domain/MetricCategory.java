package com.familyfood.wellness.domain;

/**
 * The grouping a metric belongs to, and the unit of sharing.
 *
 * <p>Visibility is granted per category rather than per metric (D-038) because that is the
 * decision a person can actually make: "my family can see my activity" is a sentence someone
 * means, whereas "my family can see my active minutes but not my step count" is a settings
 * screen nobody finishes. Categories are also the axis insights aggregate over.
 */
public enum MetricCategory {
    BODY,
    ACTIVITY,
    SLEEP,
    HYDRATION,
    NUTRITION,
    CLINICAL,
    WELLBEING
}
