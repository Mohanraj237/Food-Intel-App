package com.familyfood.wellness.api;

import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricVisibilityGrant;

import java.time.Instant;
import java.util.UUID;

public record VisibilityGrantResponse(
        UUID id,
        UUID subjectMemberId,
        UUID audienceMemberId,
        MetricCategory metricCategory,
        Instant grantedAt) {

    public static VisibilityGrantResponse from(MetricVisibilityGrant g) {
        return new VisibilityGrantResponse(
                g.getId(), g.getSubjectMemberId(), g.getAudienceMemberId(),
                g.getMetricCategory(), g.getGrantedAt());
    }
}
