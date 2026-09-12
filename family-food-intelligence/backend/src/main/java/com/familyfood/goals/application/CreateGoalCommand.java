package com.familyfood.goals.application;

import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalPeriod;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A request to start a goal.
 *
 * <p>No unit field, for the same reason {@code RecordObservationCommand} has none: the unit is
 * fixed by {@link MetricCode#canonicalUnit()}. A target in pounds compared against a history
 * in kilograms is wrong without ever looking wrong.
 *
 * <p>No {@code startedOn} either. A goal starts when it is created; back-dating one would let a
 * member manufacture a period of progress they never logged.
 */
public record CreateGoalCommand(
        UUID memberId,
        MetricCode metricCode,
        GoalDirection direction,
        BigDecimal targetValue,
        GoalPeriod period) {
}
