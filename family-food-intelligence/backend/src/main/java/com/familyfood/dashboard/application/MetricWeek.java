package com.familyfood.dashboard.application;

import com.familyfood.wellness.domain.MetricAggregation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One metric across the overview's week, a day at a time — each day reduced exactly as
 * {@link MetricDay} reduces one, by the same accumulator, so a bar in the week and the number on
 * the card can never disagree.
 *
 * <p>Produced only for a metric with at least one observation somewhere in the week. A day with
 * nothing logged is a {@link DayPoint} with a {@code null} value and a zero count — a gap to draw
 * as a gap, never a zero-height bar.
 *
 * <p>No interpretation, as for {@link MetricDay}: no range, no trend verdict, no colour (D-037).
 *
 * @param days every day of the week, oldest first, logged or not
 */
public record MetricWeek(MetricCode code,
                         MetricCategory category,
                         String unit,
                         MetricAggregation aggregation,
                         List<DayPoint> days) {

    /**
     * @param value the day's total (SUM) or its newest reading (LATEST); {@code null} when nothing
     *              was logged that day — written as {@code null}, not omitted, despite the
     *              application-wide {@code non_null} default
     * @param count how many readings the day holds — for nutrition, how many meal shares
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DayPoint(LocalDate date, BigDecimal value, int count) {}
}
