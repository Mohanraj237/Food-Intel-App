package com.familyfood.insights.domain;

/**
 * The kinds of statement this module can make.
 *
 * <p>A closed set on purpose. Each value corresponds to one deterministic computation in
 * {@code InsightService}; there is no "general observation" catch-all, because that is where
 * an unsupported claim would eventually be filed.
 */
public enum InsightType {
    /** How a nutrient's daily total moved between two windows. */
    NUTRITION_TREND,
    /** Progress against a goal the user set themselves. */
    GOAL_PROGRESS,
    /** How many of the expected days a habit was completed. */
    HABIT_CONSISTENCY,
    /** Which meals were logged, and how consistently. */
    MEAL_PATTERN,
    /** How an activity metric moved between two windows. */
    ACTIVITY_TREND,
    /** How hydration moved between two windows. */
    HYDRATION_TREND,
    /** Not a finding — a statement that too little was recorded to say anything. */
    DATA_COVERAGE
}
