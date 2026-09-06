package com.familyfood.datarights.api;

import java.time.Instant;

/**
 * {@code status} is always {@code "DELETION_REQUESTED"} today — the request always
 * succeeds or throws, there is no partial state to report. The field exists so a future
 * status (were a cancellation window ever added) would not be a breaking response shape
 * change.
 */
public record AccountDeletionResponse(String status, Instant erasureEffectiveAt) {

    public static AccountDeletionResponse requested(Instant erasureEffectiveAt) {
        return new AccountDeletionResponse("DELETION_REQUESTED", erasureEffectiveAt);
    }
}
