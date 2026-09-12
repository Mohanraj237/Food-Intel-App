package com.familyfood.goals.application;

import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalMetricAggregation;
import com.familyfood.goals.domain.GoalPeriod;
import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What a goal's window actually contained, next to what it was aiming for.
 *
 * <p><b>{@code actual} is null exactly when {@code dataAvailable} is false, and is never
 * substituted with zero.</b> "You drank 0 ml of water today" and "you have not logged any
 * water today" are different sentences, and presenting the second as the first invents a
 * measurement and then scolds the user for it. The boolean exists alongside the nullable field
 * so a JSON consumer that coerces null to 0 still has the fact available.
 *
 * <p>No "achieved", "percentage" or "on track" field. Progress states the arithmetic —
 * {@code actual}, {@code target}, {@code direction} — and the window it covers, so a reader
 * can reproduce it by hand. {@code windowFrom}/{@code windowTo} are part of that: a total is
 * uninterpretable without knowing what it totals.
 */
public record GoalProgressView(
        UUID goalId,
        UUID subjectMemberId,
        MetricCode metricCode,
        String unit,
        GoalDirection direction,
        GoalPeriod period,
        /** How the window was reduced to one number — summed, or the latest reading. */
        GoalMetricAggregation aggregation,
        BigDecimal target,
        BigDecimal actual,
        boolean dataAvailable,
        Instant windowFrom,
        Instant windowTo) {

    static GoalProgressView measured(WellnessGoal goal, BigDecimal actual, Instant from, Instant to) {
        return of(goal, actual, true, from, to);
    }

    /** Nothing was recorded in the window. Not a failure, and not a zero. */
    static GoalProgressView absent(WellnessGoal goal, Instant from, Instant to) {
        return of(goal, null, false, from, to);
    }

    private static GoalProgressView of(WellnessGoal goal, BigDecimal actual, boolean dataAvailable,
                                       Instant from, Instant to) {
        return new GoalProgressView(
                goal.getId(),
                goal.getHouseholdMemberId(),
                goal.getMetricCode(),
                goal.getMetricCode().canonicalUnit(),
                goal.getDirection(),
                goal.getPeriod(),
                GoalMetricAggregation.forMetric(goal.getMetricCode()),
                goal.getTargetValue(),
                actual,
                dataAvailable,
                from,
                to);
    }
}
