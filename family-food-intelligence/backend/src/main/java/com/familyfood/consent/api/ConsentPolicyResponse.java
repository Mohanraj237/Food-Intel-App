package com.familyfood.consent.api;

import com.familyfood.consent.application.ConsentPolicyView;

import java.time.Instant;

public record ConsentPolicyResponse(
        String purposeCode,
        String purposeName,
        String purposeDescription,
        boolean requiresVerifiable,
        boolean essential,
        String policyVersion,
        String documentUri,
        String summary,
        Instant effectiveFrom) {

    public static ConsentPolicyResponse from(ConsentPolicyView view) {
        return new ConsentPolicyResponse(
                view.purposeCode(),
                view.purposeName(),
                view.purposeDescription(),
                view.requiresVerifiable(),
                view.essential(),
                view.policyVersion(),
                view.documentUri(),
                view.summary(),
                view.effectiveFrom());
    }
}
