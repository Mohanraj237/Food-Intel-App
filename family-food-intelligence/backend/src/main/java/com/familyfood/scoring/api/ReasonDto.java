package com.familyfood.scoring.api;

import com.familyfood.scoring.application.Reason;

import java.math.BigDecimal;

/** Mirrors {@link Reason} — a structured, deterministic explanation, never free text (D-011). */
public record ReasonDto(
        String code,
        String nutrientCode,
        String basis,
        BigDecimal value,
        BigDecimal threshold,
        String standardRef) {

    static ReasonDto from(Reason reason) {
        return new ReasonDto(reason.code(), reason.nutrientCode(), reason.basis(),
                reason.value(), reason.threshold(), reason.standardRef());
    }
}
