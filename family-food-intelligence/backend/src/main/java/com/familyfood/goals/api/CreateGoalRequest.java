package com.familyfood.goals.api;

import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalPeriod;
import com.familyfood.wellness.domain.MetricCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * No unit field: the unit is fixed by the metric. A target in pounds against a history in
 * kilograms is wrong without ever looking wrong.
 *
 * <p>The bounds on {@code targetValue} are structural — a non-negative number that fits
 * {@code numeric(12,3)}. They are not plausibility limits: judging a self-set target
 * unreasonable is an interpretation, and interpretation needs a citable standard (D-037).
 */
public record CreateGoalRequest(
        @NotNull UUID memberId,
        @NotNull MetricCode metricCode,
        @NotNull GoalDirection direction,
        @NotNull @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal targetValue,
        @NotNull GoalPeriod period) {
}
