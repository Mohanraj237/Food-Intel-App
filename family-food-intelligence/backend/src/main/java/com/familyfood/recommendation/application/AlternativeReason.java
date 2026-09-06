package com.familyfood.recommendation.application;

import java.util.UUID;

/**
 * A structured, deterministic reason a candidate is listed under {@code betterOn} or
 * {@code worseOn} (D-013) — never free text (mirrors {@code scoring.application.Reason}'s
 * own contract). Every field beyond {@code code} is nullable; which ones are populated
 * depends on {@code code}.
 *
 * <p>Only two dimensions exist today, deliberately: allergen safety (a factual
 * set-membership comparison, already real and active per D-024 point 2) and a fired-rule
 * verdict comparison (structurally real, but honestly inert — {@code VERDICT_BETTER}/
 * {@code VERDICT_WORSE} can never fire while D-024's active rule set ships empty, since
 * every verdict is {@code INSUFFICIENT_DATA}). Nothing here invents a nutrient-direction
 * judgment ("lower sugar is better") — see D-033 for why that is out of scope until a
 * reviewed rule set exists (D-025).
 */
public record AlternativeReason(
        String code,          // "SAFER_ALLERGENS" | "NEW_ALLERGEN_RISK" | "BETTER_VERDICT" | "WORSE_VERDICT"
        UUID memberId,        // which household member this concerns (self or a guarded child)
        String allergenCode,  // populated for SAFER_ALLERGENS / NEW_ALLERGEN_RISK
        String verdict,       // the candidate's verdict, populated for BETTER_VERDICT / WORSE_VERDICT
        String comparedVerdict // the current product's verdict for the same member, same cases
) {
}
