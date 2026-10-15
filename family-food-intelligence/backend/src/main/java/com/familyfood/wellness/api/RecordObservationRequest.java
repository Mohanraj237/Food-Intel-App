package com.familyfood.wellness.api;

import com.familyfood.wellness.domain.MetricCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * No unit field, on purpose: the unit is fixed by the metric (see
 * {@code MetricCode.canonicalUnit()}). Accepting one here would let one member's weight
 * history hold both kilograms and pounds, which makes every trend quietly wrong instead of
 * visibly broken.
 *
 * <p>No source field either (removed in W1): every write through this request is MANUAL. A
 * client that could label its own reading DERIVED or IMPORT could make a typed number look
 * like it came from a meal or a device.
 *
 * @param effectiveAt when the reading applies to; null means now. Separate from the
 *                    recording time so a weight entered tonight for yesterday morning lands
 *                    on yesterday in every trend.
 */
public record RecordObservationRequest(
        @NotNull MetricCode metricCode,
        // Bounds here are structural only — a non-negative number that fits the column.
        // They are not clinical plausibility limits: deciding that a blood-pressure reading
        // is out of range is an interpretation, and interpretation needs a citable standard
        // (D-037). The column CHECK and this annotation agree deliberately.
        @NotNull @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal value,
        Instant effectiveAt) {
}
