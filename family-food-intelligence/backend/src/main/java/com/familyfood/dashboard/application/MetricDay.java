package com.familyfood.dashboard.application;

import com.familyfood.wellness.domain.MetricAggregation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One metric's day, reduced the way the metric says it must be (see
 * {@link MetricCode#aggregation()}): water and steps are totalled, weight and blood pressure take
 * the newest reading. The rule is stated here as {@code aggregation} so a reader can tell "800 ml
 * across three glasses" from "72.4 kg, this morning's weigh-in".
 *
 * <p>Only a metric with at least one observation that day produces one of these. There is no
 * row, and never a zero, for a metric nobody logged — "not logged" and "0" are different facts
 * (release process, no-fake-data).
 *
 * <p>No interpretation: no range, no "high", no colour. Naming a number needs a reviewed
 * standard (D-037).
 *
 * @param value            the day's total (SUM) or its newest reading (LATEST)
 * @param observationCount how many readings the day holds for this metric
 * @param lastRecordedAt   when the newest of them applies to ({@code effectiveAt}) — the
 *                         moment the day's bucketing uses, and the reading a LATEST value is
 */
public record MetricDay(MetricCode code,
                        MetricCategory category,
                        String unit,
                        MetricAggregation aggregation,
                        BigDecimal value,
                        int observationCount,
                        Instant lastRecordedAt) {}
