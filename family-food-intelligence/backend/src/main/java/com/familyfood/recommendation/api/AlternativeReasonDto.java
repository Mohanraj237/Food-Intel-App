package com.familyfood.recommendation.api;

import com.familyfood.recommendation.application.AlternativeReason;

import java.util.UUID;

/** Mirrors {@link AlternativeReason}. A structured reason, never free text (D-011, D-013). */
public record AlternativeReasonDto(
        String code,
        UUID memberId,
        String allergenCode,
        String verdict,
        String comparedVerdict) {

    static AlternativeReasonDto from(AlternativeReason reason) {
        return new AlternativeReasonDto(
                reason.code(), reason.memberId(), reason.allergenCode(),
                reason.verdict(), reason.comparedVerdict());
    }
}
