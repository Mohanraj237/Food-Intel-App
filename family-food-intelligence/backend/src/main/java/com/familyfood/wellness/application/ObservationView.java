package com.familyfood.wellness.application;

import com.familyfood.wellness.domain.HealthObservation;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One observation as the API returns it.
 *
 * <p>Carries the recorded value, its unit and its timestamps — and deliberately no
 * interpretation: no category, no reference range, no "normal"/"elevated" flag. Naming a
 * number is a clinical claim and requires a citable reviewed standard (D-037), so the field
 * that would hold one does not exist.
 */
public record ObservationView(
        UUID id,
        UUID subjectMemberId,
        MetricCode metricCode,
        MetricCategory metricCategory,
        BigDecimal value,
        String unit,
        Instant effectiveAt,
        Instant recordedAt,
        ObservationSource source) {

    public static ObservationView from(HealthObservation o) {
        return new ObservationView(
                o.getId(),
                o.getHouseholdMemberId(),
                o.getMetricCode(),
                o.getMetricCategory(),
                o.getValue(),
                o.getUnit(),
                o.getEffectiveAt(),
                o.getRecordedAt(),
                o.getSource());
    }
}
