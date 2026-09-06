package com.familyfood.capture.api;

import com.familyfood.capture.application.ContributionView;

import java.time.Instant;
import java.util.UUID;

public record ContributionResponse(UUID id, String status, Instant submittedAt) {

    static ContributionResponse from(ContributionView view) {
        return new ContributionResponse(view.id(), view.status(), view.submittedAt());
    }
}
