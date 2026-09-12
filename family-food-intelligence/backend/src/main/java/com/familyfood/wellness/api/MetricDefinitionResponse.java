package com.familyfood.wellness.api;

import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;

/**
 * One metric's definition, including whether it may be recorded for a child.
 *
 * <p>Published so a client renders the right form for the right subject instead of
 * hardcoding the age rules — a hardcoded copy would drift from the domain the moment a
 * metric changed class, and the drift would be silent in exactly the direction that matters.
 * The server still refuses a disallowed write regardless of what the client offers (D-036).
 */
public record MetricDefinitionResponse(
        MetricCode code,
        MetricCategory category,
        String unit,
        boolean adultOnly) {

    public static MetricDefinitionResponse from(MetricCode code) {
        return new MetricDefinitionResponse(
                code,
                code.category(),
                code.canonicalUnit(),
                code.permittedFor() == MetricCode.AgeClass.ADULT_ONLY);
    }
}
