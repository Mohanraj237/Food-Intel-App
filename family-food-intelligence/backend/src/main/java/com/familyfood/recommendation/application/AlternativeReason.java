package com.familyfood.recommendation.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A structured, deterministic reason a candidate is listed under {@code betterOn} or
 * {@code worseOn} (D-013) — never free text (mirrors {@code scoring.application.Reason}'s
 * own contract). Every field beyond {@code code} is nullable; which ones are populated
 * depends on {@code code}.
 *
 * <p>Codes:
 * <ul>
 *   <li>{@code SAFER_ALLERGENS} / {@code NEW_ALLERGEN_RISK} — a declared allergen resolved or
 *       introduced for one member (set membership, D-024 point 2);
 *   <li>{@code BETTER_VERDICT} / {@code WORSE_VERDICT} — one member's verdict differs;
 *   <li>{@code HIGHER_RATING} / {@code LOWER_RATING} — the candidate sits in a different star
 *       band of the ACTIVE rating method (D-060: the band edge, not an arbitrary delta, is
 *       what makes a difference real);
 *   <li>{@code LOWER_<NUTRIENT>} / {@code HIGHER_<NUTRIENT>} — the candidate lands in a
 *       different points band for that factor of the ACTIVE method (e.g.
 *       {@code LOWER_TOTAL_SUGAR_G}); the direction is the component's own (a risk factor
 *       lower is better, a positive factor higher is better), never a judgement coded here;
 *   <li>{@code LESS_PROCESSED} / {@code MORE_PROCESSED} — a different NOVA group.
 * </ul>
 * With the ACTIVE rule set empty (D-024) only the allergen and NOVA reasons can occur.
 */
public record AlternativeReason(
        String code,
        UUID memberId,           // for member reasons; null for product-level ones
        String allergenCode,     // SAFER_ALLERGENS / NEW_ALLERGEN_RISK
        String verdict,          // candidate's verdict, BETTER_/WORSE_VERDICT
        String comparedVerdict,  // current product's verdict, same cases
        String nutrientCode,     // LOWER_/HIGHER_<NUTRIENT>
        BigDecimal candidateValue, // candidate's value (nutrient per 100, stars, NOVA group)
        BigDecimal currentValue) { // the current product's value, same unit

    public static AlternativeReason member(String code, UUID memberId, String allergenCode, String verdict,
                                           String comparedVerdict) {
        return new AlternativeReason(code, memberId, allergenCode, verdict, comparedVerdict, null, null, null);
    }

    public static AlternativeReason product(String code, String nutrientCode, BigDecimal candidateValue,
                                            BigDecimal currentValue) {
        return new AlternativeReason(code, null, null, null, null, nutrientCode, candidateValue, currentValue);
    }
}
