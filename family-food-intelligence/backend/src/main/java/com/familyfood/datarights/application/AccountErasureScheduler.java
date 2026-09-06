package com.familyfood.datarights.application;

import com.familyfood.identity.application.AccountErasureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the hard-erasure step for every account whose {@code familyfood.privacy.erasure-
 * grace-period} has elapsed since {@code AccountErasureService.requestDeletion}. Hourly, on
 * a fixed schedule with no configuration knob — the same "a reviewable constant, not a
 * tunable" choice {@code HouseholdService.INVITATION_TTL} already makes, for the same
 * reason: a grace period whose length can silently drift is a privacy commitment nobody can
 * actually rely on.
 *
 * <p>One account's failure must never block another's: each is erased in {@code
 * DataRightsService.performFullErasure}'s own transaction, and a failure here is logged,
 * not rethrown, so the sweep continues to the next account rather than abandoning the
 * entire batch over one bad row.
 */
@Component
public class AccountErasureScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountErasureScheduler.class);

    private final AccountErasureService accountErasure;
    private final DataRightsService dataRights;

    public AccountErasureScheduler(AccountErasureService accountErasure, DataRightsService dataRights) {
        this.accountErasure = accountErasure;
        this.dataRights = dataRights;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void eraseDueAccounts() {
        List<UUID> due = accountErasure.findAccountsDueForErasure();
        for (UUID accountId : due) {
            try {
                dataRights.performFullErasure(accountId);
            } catch (RuntimeException e) {
                log.error("account_erasure_failed accountId={}", accountId, e);
            }
        }
    }
}
