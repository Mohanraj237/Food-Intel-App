package com.familyfood.consent.application;

import com.familyfood.consent.domain.ConsentPolicyVersion;
import com.familyfood.consent.domain.ConsentPurpose;

import java.time.Instant;

/**
 * The current policy text for a purpose, paired with the purpose's own metadata. Backs the
 * public {@code GET /api/v1/consent/policies} route — the text a person is shown before
 * granting anything, so it carries no consent state at all.
 */
public record ConsentPolicyView(
        String purposeCode,
        String purposeName,
        String purposeDescription,
        boolean requiresVerifiable,
        boolean essential,
        String policyVersion,
        String documentUri,
        String summary,
        Instant effectiveFrom) {

    public static ConsentPolicyView of(ConsentPurpose purpose, ConsentPolicyVersion version) {
        return new ConsentPolicyView(
                purpose.getCode(),
                purpose.getName(),
                purpose.getDescription(),
                purpose.isRequiresVerifiable(),
                purpose.isEssential(),
                version.getVersion(),
                version.getDocumentUri(),
                version.getSummary(),
                version.getEffectiveFrom());
    }
}
