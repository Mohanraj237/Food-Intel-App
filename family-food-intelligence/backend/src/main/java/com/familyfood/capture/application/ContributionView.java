package com.familyfood.capture.application;

import java.time.Instant;
import java.util.UUID;

/** What a caller learns after submitting a contribution: an id and a status, nothing else. */
public record ContributionView(UUID id, String status, Instant submittedAt) {
}
