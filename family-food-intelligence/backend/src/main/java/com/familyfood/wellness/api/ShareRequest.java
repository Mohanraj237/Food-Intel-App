package com.familyfood.wellness.api;

import com.familyfood.wellness.domain.MetricCategory;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** One category, one audience member. There is deliberately no "share with everyone"
 *  shortcut — D-038 is per-audience so that revoking one person's access is possible. */
public record ShareRequest(
        @NotNull UUID audienceMemberId,
        @NotNull MetricCategory metricCategory) {
}
