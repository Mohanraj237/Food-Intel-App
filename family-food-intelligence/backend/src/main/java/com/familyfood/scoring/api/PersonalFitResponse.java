package com.familyfood.scoring.api;

import com.familyfood.scoring.application.PersonalFitView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors {@link PersonalFitView}. {@code status} distinguishes a real result ({@code
 * "ASSESSED"}) from every reason one might be unavailable ({@code "NO_PROFILE"}, {@code
 * "CONSENT_REQUIRED"}, {@code "CONSENT_WITHDRAWN"}, {@code "NOT_AUTHORIZED"}) — the frontend
 * must render each of these honestly rather than treating anything but "ASSESSED" as a generic
 * error. {@code verdict}/{@code reasons}/{@code allergenMatches}/{@code ruleSetVersion} are
 * only meaningful when {@code status="ASSESSED"}.
 */
public record PersonalFitResponse(
        UUID memberId,
        String status,
        String verdict,
        List<ReasonDto> reasons,
        List<String> allergenMatches,
        String ruleSetVersion,
        Instant computedAt) {

    static PersonalFitResponse from(PersonalFitView view) {
        return new PersonalFitResponse(
                view.memberId(),
                view.status(),
                view.verdict(),
                view.reasons().stream().map(ReasonDto::from).toList(),
                view.allergenMatches(),
                view.ruleSetVersion(),
                view.computedAt());
    }
}
