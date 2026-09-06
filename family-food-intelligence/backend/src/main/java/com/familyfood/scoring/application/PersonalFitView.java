package com.familyfood.scoring.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One household member's personal fit for a product — how <em>their own</em> recorded
 * profile changes how a product looks to them, separate from the product's own health
 * score (D-003, D-004).
 *
 * <p>{@code status} carries every authorization, consent and data-availability outcome as
 * a value rather than an exception (see {@link PersonalFitService#evaluate}), because a
 * household-fit caller assembling several of these needs to skip one member without
 * failing the whole request (D-024 point 4).
 *
 * <ul>
 *   <li>{@code ASSESSED} — {@code verdict}, {@code reasons} and {@code ruleSetVersion} are
 *       populated; {@code allergenMatches} may be empty but is always populated.
 *   <li>{@code NO_PROFILE}, {@code CONSENT_REQUIRED}, {@code CONSENT_WITHDRAWN},
 *       {@code NOT_AUTHORIZED} — {@code verdict} and {@code ruleSetVersion} are
 *       {@code null}; {@code reasons} and {@code allergenMatches} are empty.
 * </ul>
 *
 * <p>{@code allergenMatches} is a factual set-membership check (declared product allergens
 * present in this member's own profile), not a health judgment, and is never folded into
 * {@code verdict} — allergen is a separate hard flag (D-004, D-024 point 2).
 */
public record PersonalFitView(
        UUID memberId,
        String status,        // "ASSESSED" | "NO_PROFILE" | "CONSENT_REQUIRED" | "CONSENT_WITHDRAWN" | "NOT_AUTHORIZED"
        String verdict,        // "EVERYDAY"|"OCCASIONAL"|"LIMIT"|"DOESNT_FIT"|"INSUFFICIENT_DATA" — null unless status="ASSESSED"
        List<Reason> reasons,          // empty unless status="ASSESSED"
        List<String> allergenMatches,  // declared-allergen codes also on this member's profile; empty unless status="ASSESSED"
        String ruleSetVersion,                    // null unless status="ASSESSED"
        Instant computedAt) {
}
