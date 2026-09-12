package com.familyfood.wellness.application;

import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A request to record one measurement.
 *
 * <p>No unit field: the unit comes from {@link MetricCode#canonicalUnit()}. Accepting one
 * from the caller would let a history hold kilograms and pounds in the same column, which
 * makes every trend silently wrong rather than visibly broken.
 *
 * <p>{@code effectiveAt} null means "now". It is separate from the recording time because a
 * weight entered on Monday evening for Sunday morning belongs to Sunday in every trend.
 */
public record RecordObservationCommand(
        UUID subjectMemberId,
        MetricCode metricCode,
        BigDecimal value,
        Instant effectiveAt,
        ObservationSource source) {
}
