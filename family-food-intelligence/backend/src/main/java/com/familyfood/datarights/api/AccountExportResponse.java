package com.familyfood.datarights.api;

import com.familyfood.capture.application.ContributionService.ContributionSummary;
import com.familyfood.consent.application.ConsentExportView;
import com.familyfood.datarights.application.AccountExportView;
import com.familyfood.identity.application.AccountErasureService.AccountSummary;
import com.familyfood.profile.application.ProfileView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirrors {@link AccountExportView} field-for-field — see that class for the export's scope. */
public record AccountExportResponse(
        AccountSummary account,
        List<HouseholdExport> households,
        List<ContributionSummary> contributions,
        Instant exportedAt) {

    public static AccountExportResponse from(AccountExportView view) {
        return new AccountExportResponse(
                view.account(),
                view.households().stream().map(HouseholdExport::from).toList(),
                view.contributions(),
                view.exportedAt());
    }

    public record HouseholdExport(
            UUID householdId,
            String householdName,
            UUID memberId,
            String role,
            String displayLabel,
            Instant joinedAt,
            List<ProfileView> profileVersions,
            List<ConsentExportView> consent,
            List<ChildExport> guardedChildren) {

        static HouseholdExport from(AccountExportView.HouseholdExport view) {
            return new HouseholdExport(
                    view.householdId(),
                    view.householdName(),
                    view.memberId(),
                    view.role(),
                    view.displayLabel(),
                    view.joinedAt(),
                    view.profileVersions(),
                    view.consent(),
                    view.guardedChildren().stream().map(ChildExport::from).toList());
        }
    }

    public record ChildExport(
            UUID memberId,
            String displayLabel,
            Instant joinedAt,
            List<ProfileView> profileVersions,
            List<ConsentExportView> consent) {

        static ChildExport from(AccountExportView.ChildExport view) {
            return new ChildExport(
                    view.memberId(), view.displayLabel(), view.joinedAt(),
                    view.profileVersions(), view.consent());
        }
    }
}
