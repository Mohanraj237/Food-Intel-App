package com.familyfood.datarights.application;

import com.familyfood.capture.application.ContributionService.ContributionSummary;
import com.familyfood.consent.application.ConsentExportView;
import com.familyfood.identity.application.AccountErasureService.AccountSummary;
import com.familyfood.profile.application.ProfileView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full shape of {@code GET /api/v1/account/export} (AC-9.2): the caller's own account,
 * every household they belong to (their own membership, their own profile version history,
 * their own consent ledger), every child they currently, actively guard within each of
 * those households (same shape, since a guardian is authorized to see exactly this about a
 * child they guard — {@code ProfileService}/{@code ConsentService}'s own authorization
 * rules, re-run rather than bypassed), and their own product contributions.
 *
 * <p>Deliberately excludes: the password hash, any other member's profile or consent, the
 * audit log, and every operational/security table (login attempts, rate limiting) — none of
 * that is "this person's personal data" in AC-9.2's sense, and some of it (the audit log) is
 * explicitly the kind of internal record this session's brief says not to export by default.
 */
public record AccountExportView(
        AccountSummary account,
        List<HouseholdExport> households,
        List<ContributionSummary> contributions,
        Instant exportedAt) {

    public record HouseholdExport(
            UUID householdId,
            String householdName,
            UUID memberId,
            String role,
            String displayLabel,
            Instant joinedAt,
            List<ProfileView> profileVersions,
            List<ConsentExportView> consent,
            List<ChildExport> guardedChildren) {}

    public record ChildExport(
            UUID memberId,
            String displayLabel,
            Instant joinedAt,
            List<ProfileView> profileVersions,
            List<ConsentExportView> consent) {}
}
