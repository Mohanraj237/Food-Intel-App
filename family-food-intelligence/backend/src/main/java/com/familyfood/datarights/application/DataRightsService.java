package com.familyfood.datarights.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.capture.application.ContributionService;
import com.familyfood.consent.application.ConsentService;
import com.familyfood.datarights.application.AccountExportView.ChildExport;
import com.familyfood.datarights.application.AccountExportView.HouseholdExport;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.GuardedChildInfo;
import com.familyfood.household.application.HouseholdService;
import com.familyfood.household.application.HouseholdService.HouseholdSummary;
import com.familyfood.identity.application.AccountErasureService;
import com.familyfood.profile.application.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Data rights (NFR-09, CH-008, FR-010's erasure gap, AC-9.2/AC-9.3): export and the
 * cross-module halves of account and child erasure.
 *
 * <p>This is the one module allowed to reach into {@code identity}, {@code household},
 * {@code profile} and {@code capture} together, for the same reason {@code
 * scoring.application.PersonalFitServiceImpl} already reaches into {@code household} and
 * {@code profile} and {@code consent} together: each of those modules owns and mutates only
 * its own tables ({@code household} never touches {@code person_profile}; {@code profile}
 * never touches {@code household_member}), so an operation that spans several of them —
 * "erase everything this account owns" is inescapably one — has to be coordinated one level
 * up. Every module this class calls into is that module's own public {@code application}
 * surface, never its {@code domain}/{@code infra}/{@code api} package.
 *
 * <p>Two things this class deliberately does <em>not</em> do, per AC-9.3 and this session's
 * brief: it never touches {@code consent_record} or {@code audit_event} (both survive every
 * erasure this class performs, untouched — see {@code ProfileService.eraseAllVersions},
 * {@code HouseholdService.eraseAccountMemberships}/{@code eraseChildMember}, none of which
 * reach {@code consent.infra} at all), and it never re-authorizes what its callees already
 * authorize — {@link #eraseChild} trusts {@code HouseholdService.eraseChildMember}'s own
 * guardian check rather than duplicating it.
 */
@Service
public class DataRightsService {

    private final HouseholdAccess householdAccess;
    private final HouseholdService householdService;
    private final ProfileService profileService;
    private final ConsentService consentService;
    private final ContributionService contributionService;
    private final AccountErasureService accountErasure;
    private final AuditService audit;
    private final Clock clock;

    public DataRightsService(HouseholdAccess householdAccess,
                             HouseholdService householdService,
                             ProfileService profileService,
                             ConsentService consentService,
                             ContributionService contributionService,
                             AccountErasureService accountErasure,
                             AuditService audit,
                             Clock clock) {
        this.householdAccess = householdAccess;
        this.householdService = householdService;
        this.profileService = profileService;
        this.consentService = consentService;
        this.contributionService = contributionService;
        this.accountErasure = accountErasure;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------- export

    /**
     * The caller's own data, and nothing anyone else's (AC-9.2). Every read here goes
     * through the owning module's already-authorized methods ({@code ProfileService},
     * {@code ConsentService}) called with the caller's own account id — the same
     * authorization that already governs an ordinary read of that data, not a bypass of it.
     */
    @Transactional(readOnly = true)
    public AccountExportView exportMyData(UUID accountId) {
        List<HouseholdSummary> memberships = householdService.listMyHouseholds(accountId);
        List<GuardedChildInfo> guardedChildren = householdAccess.listGuardedChildren(accountId);
        Map<UUID, List<GuardedChildInfo>> childrenByHousehold =
                guardedChildren.stream().collect(Collectors.groupingBy(GuardedChildInfo::householdId));

        List<HouseholdExport> households = memberships.stream()
                .map(membership -> new HouseholdExport(
                        membership.householdId(),
                        membership.householdName(),
                        membership.memberId(),
                        membership.role(),
                        membership.displayLabel(),
                        membership.joinedAt(),
                        profileService.getVersionHistory(accountId, membership.memberId()),
                        consentService.listForExport(accountId, membership.memberId()),
                        childrenByHousehold
                                .getOrDefault(membership.householdId(), List.of())
                                .stream()
                                .map(child -> new ChildExport(
                                        child.id(),
                                        child.displayLabel(),
                                        child.joinedAt(),
                                        profileService.getVersionHistory(accountId, child.id()),
                                        consentService.listForExport(accountId, child.id())))
                                .toList()))
                .toList();

        AccountExportView export = new AccountExportView(
                accountErasure.getSummary(accountId),
                households,
                contributionService.listMine(accountId),
                clock.instant());

        audit.success(AuditAction.DATA_EXPORTED, "UserAccount", accountId, accountId, null);
        return export;
    }

    // -------------------------------------------------------------- account erasure

    /**
     * The scheduled hard-erasure step (called only by {@code AccountErasureScheduler}, once
     * {@code AccountErasureService.requestDeletion}'s grace period has elapsed). Every step
     * runs in this one transaction: either the account's memberships, profiles,
     * contributions and identity row are all erased, or none of them are.
     */
    @Transactional
    public void performFullErasure(UUID accountId) {
        List<UUID> erasedMemberIds = householdService.eraseAccountMemberships(accountId, clock.instant());
        erasedMemberIds.forEach(profileService::eraseAllVersions);
        int contributionsAnonymized = contributionService.anonymizeSubmitter(accountId);

        accountErasure.eraseIdentityRow(accountId);

        audit.record(AuditAction.DATA_ERASURE_COMPLETED, "UserAccount", accountId, accountId, null,
                AuditOutcome.SUCCESS, Map.of(
                        "membershipsErased", erasedMemberIds.size(),
                        "contributionsAnonymized", contributionsAnonymized));
    }

    // ---------------------------------------------------------------- child erasure

    /**
     * Guardian-authorized child erasure (CH-008, FR-010's child-erasure gap). Authorization
     * happens entirely inside {@link HouseholdService#eraseChildMember} — if the caller is
     * not an active member of the household, or not an active guardian of this specific
     * child, or the target is not a live CHILD member of this household, it throws before
     * this method ever reaches {@code profileService.eraseAllVersions}.
     */
    @Transactional
    public void eraseChild(UUID callerAccountId, UUID householdId, UUID childMemberId) {
        householdService.eraseChildMember(callerAccountId, householdId, childMemberId);
        profileService.eraseAllVersions(childMemberId);

        audit.record(AuditAction.DATA_ERASURE_COMPLETED, "HouseholdMember", childMemberId,
                callerAccountId, householdId, AuditOutcome.SUCCESS, Map.of());
    }
}
