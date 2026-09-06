package com.familyfood.consent.api;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * {@code purposeCode} is validated against the reference table inside {@code ConsentService}
 * (it is DB-seeded data, not a closed Java enum, so an unknown code surfaces as
 * {@code RESOURCE_NOT_FOUND} rather than a JSON binding failure). {@code evidence} is
 * optional context (e.g. a hashed user agent) the caller may attach; it is never rendered
 * back in any listing.
 */
public record GrantConsentRequest(@NotBlank String purposeCode, Map<String, Object> evidence) {

    public GrantConsentRequest {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
