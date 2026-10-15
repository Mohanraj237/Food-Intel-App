package com.familyfood.datarights.application;

import com.familyfood.access.application.AccessGrantView;
import com.familyfood.capture.application.ContributionService.ContributionSummary;
import com.familyfood.consent.application.ConsentExportView;
import com.familyfood.goals.application.GoalView;
import com.familyfood.habits.application.HabitService.HabitExport;
import com.familyfood.identity.application.AccountErasureService.AccountSummary;
import com.familyfood.nutrition.application.MealView;
import com.familyfood.profile.application.ProfileView;
import com.familyfood.wellness.application.ObservationView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full shape of {@code GET /api/v1/account/export} (AC-9.2): the caller's own account,
 * every household they belong to (their own membership, their own profile version history,
 * their own consent ledger, their own health data), every child they currently, actively
 * guard within each of those households (same shape, since a guardian is authorized to see
 * exactly this about a child they guard — {@code ProfileService}/{@code ConsentService}'s own
 * authorization rules, re-run rather than bypassed), and their own product contributions.
 *
 * <p>Deliberately excludes: the password hash, any other member's profile, consent or health
 * data, the audit log, and every operational/security table (login attempts, rate limiting) —
 * none of that is "this person's personal data" in AC-9.2's sense, and some of it (the audit
 * log) is explicitly the kind of internal record this session's brief says not to export by
 * default.
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
            HealthExport health,
            List<ChildExport> guardedChildren) {}

    public record ChildExport(
            UUID memberId,
            String displayLabel,
            Instant joinedAt,
            List<ProfileView> profileVersions,
            List<ConsentExportView> consent,
            HealthExport health) {}

    /**
     * One member's health and wellness record (D-042, defect 4): everything the wellness,
     * goals, habits, nutrition and access modules hold about that member, and nothing they hold
     * about anyone else.
     *
     * @param observations  every observation recorded for the member, manual and derived
     * @param goals         every goal the member has had, ended ones included
     * @param habits        every habit, archived ones included, with the days it was done
     * @param meals         the meals the member had a share of, each carrying only the member's
     *                      own share — never another person's portion or what it came to
     * @param sharingGrants what the member shared, and with whom, revoked grants included —
     *                      never what other people shared with them, which is their record
     */
    public record HealthExport(
            List<ObservationView> observations,
            List<GoalView> goals,
            List<HabitExport> habits,
            List<MealView> meals,
            List<AccessGrantView> sharingGrants) {}
}
