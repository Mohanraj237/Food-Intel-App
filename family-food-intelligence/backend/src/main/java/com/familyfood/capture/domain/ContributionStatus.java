package com.familyfood.capture.domain;

/**
 * Where a {@link ProductContribution} stands in ops review. Set once at submission
 * ({@code PENDING}) and only ever changed by the (separate, later) ops review console —
 * nothing in this module transitions a contribution's status itself.
 */
public enum ContributionStatus {
    PENDING,
    APPROVED,
    REJECTED
}
