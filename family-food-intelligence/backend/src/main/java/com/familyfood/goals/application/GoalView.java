package com.familyfood.goals.application;

import com.familyfood.goals.domain.GoalDirection;
import com.familyfood.goals.domain.GoalPeriod;
import com.familyfood.goals.domain.WellnessGoal;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One goal as the API returns it.
 *
 * <p>Carries the target and its window, and no judgement about it: no score, no rank, no
 * "on track" flag. What a number means is the reader's to decide (D-037).
 */
public record GoalView(
        UUID id,
        UUID subjectMemberId,
        MetricCode metricCode,
        String unit,
        GoalDirection direction,
        BigDecimal targetValue,
        GoalPeriod period,
        LocalDate startedOn,
        LocalDate endedOn,
        boolean active) {

    public static GoalView from(WellnessGoal goal) {
        return new GoalView(
                goal.getId(),
                goal.getHouseholdMemberId(),
                goal.getMetricCode(),
                goal.getMetricCode().canonicalUnit(),
                goal.getDirection(),
                goal.getTargetValue(),
                goal.getPeriod(),
                goal.getStartedOn(),
                goal.getEndedOn(),
                goal.isActive());
    }
}
