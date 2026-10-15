package com.familyfood.datarights.application;

import com.familyfood.access.application.AccessGrantService;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.capture.application.ContributionService;
import com.familyfood.consent.application.ConsentService;
import com.familyfood.datarights.application.AccountExportView.ChildExport;
import com.familyfood.datarights.application.AccountExportView.HealthExport;
import com.familyfood.datarights.application.AccountExportView.HouseholdExport;
import com.familyfood.goals.application.GoalService;
import com.familyfood.habits.application.HabitService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.GuardedChildInfo;
import com.familyfood.household.application.HouseholdService;
import com.familyfood.household.application.HouseholdService.HouseholdSummary;
import com.familyfood.identity.application.AccountErasureService;
import com.familyfood.nutrition.application.MealService;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.wellness.application.WellnessService;
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
 * {@code profile}, {@code capture} and the health modules ({@code wellness}, {@code goals},
 * {@code habits}, {@code nutrition}, {@code access}) together, for the same reason {@code
 * scoring.application.PersonalFitServiceImpl} already reaches into {@code household} and
 * {@code profile} and {@code consent} together: each of those modules owns and mutates only
 * its own tables ({@code household} never touches {@code person_profile}; {@code profile}
 * never touches {@code household_member}), so an operation that spans several of them —
 * "erase everything this account owns" is inescapably one — has to be coordinated one level
 * up. Every module this class calls into is that module's own public {@code application}
 * surface, never its {@code domain}/{@code infra}/{@code api} package.
 *
 * <h2>Why the health modules have to be called explicitly (D-042, defect 4)</h2>
 * Every health table cascades from {@code household_member} in the database, but erasure
 * never deletes a {@code household_member} row — it scrubs it in place (D-028), because
 * {@code consent_record} and {@code audit_event} still point at it. So no cascade ever fires,
 * and before W1 an erased member's observations, goals, habits, meal portions and sharing
 * grants all survived their own erasure. Each module now erases its own rows when asked.
 *
 * <p>Three things this class deliberately does <em>not</em> do, per AC-9.3 and this session's
 * brief: it never touches {@code consent_record} or {@code audit_event} (both survive every
 * erasure this class performs, untouched — none of the modules it calls reach {@code
 * consent.infra} at all); it never erases anything of another member (a family meal survives
 * minus the erased member's share, and the other people's own derived nutrition with it); and
 * it never re-authorizes what its callees already authorize — {@link #eraseChild} trusts
 * {@code HouseholdService.eraseChildMember}'s own guardian check rather than duplicating it.
 */
@Service
public class DataRightsService {

    private final HouseholdAccess householdAccess;
    private final HouseholdService householdService;
    private final ProfileService profileService;
    private final ConsentService consentService;
    private final ContributionService contributionService;
    private final AccountErasureService accountErasure;
    private final WellnessService wellness;
    private final GoalService goals;
    private final HabitService habits;
    private final MealService meals;
    private final AccessGrantService accessGrants;
    private final AuditService audit;
    private final Clock clock;

    public DataRightsService(HouseholdAccess householdAccess,
                             HouseholdService householdService,
                             ProfileService profileService,
                             ConsentService consentService,
                             ContributionService contributionService,
                             AccountErasureService accountErasure,
                             WellnessService wellness,
                             GoalService goals,
                             HabitService habits,
                             MealService meals,
                             AccessGrantService accessGrants,
                             AuditService audit,
                             Clock clock) {
        this.householdAccess = householdAccess;
        this.householdService = householdService;
        this.profileService = profileService;
        this.consentService = consentService;
        this.contributionService = contributionService;
        this.accountErasure = accountErasure;
        this.wellness = wellness;
        this.goals = goals;
        this.habits = habits;
        this.meals = meals;
        this.accessGrants = accessGrants;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------- export

    /**
     * The caller's own data, and nothing anyone else's (AC-9.2). Profile and consent reads go
     * through the owning module's already-authorized methods ({@code ProfileService},
     * {@code ConsentService}) called with the caller's own account id — the same authorization
     * that already governs an ordinary read of that data, not a bypass of it.
     *
     * <p>The health reads ({@link #healthOf}) are the modules' unauthorized {@code
     * exportForMember} methods, so the authorization is here instead, and it is structural: the
     * only member ids they are ever called with are the caller's own active memberships
     * ({@code listMyHouseholds}) and the children the caller actively guards ({@code
     * listGuardedChildren}), both resolved from the caller's account id. Nothing a caregiver or
     * a VIEW grant reaches is included: D-045 says a caregiver cannot export the subject's data.
     * Tracking consent is not consulted, because withdrawing it must never stop a person seeing
     * and taking away what was already collected (D-048).
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
                        healthOf(membership.memberId()),
                        childrenByHousehold
                                .getOrDefault(membership.householdId(), List.of())
                                .stream()
                                .map(child -> new ChildExport(
                                        child.id(),
                                        child.displayLabel(),
                                        child.joinedAt(),
                                        profileService.getVersionHistory(accountId, child.id()),
                                        consentService.listForExport(accountId, child.id()),
                                        healthOf(child.id())))
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

    /**
     * One member's health record, for their export. Only ever called with a member id
     * {@link #exportMyData} resolved from the caller's own account — see there.
     */
    private HealthExport healthOf(UUID memberId) {
        return new HealthExport(
                wellness.exportForMember(memberId),
                goals.exportForMember(memberId),
                habits.exportForMember(memberId),
                meals.exportForMember(memberId),
                accessGrants.exportForSubject(memberId));
    }

    // -------------------------------------------------------------- account erasure

    /**
     * The scheduled hard-erasure step (called only by {@code AccountErasureScheduler}, once
     * {@code AccountErasureService.requestDeletion}'s grace period has elapsed). Every step
     * runs in this one transaction: either the account's memberships, health data, profiles,
     * contributions and identity row are all erased, or none of them are.
     *
     * <p>Every membership the account ever held is erased, departed ones included —
     * {@code eraseAccountMemberships} returns them all, and data recorded under a membership
     * the person has since left is still theirs. A child the account guards is never touched
     * (D-028 point 5): a child is not one of the account's memberships.
     */
    @Transactional
    public void performFullErasure(UUID accountId) {
        List<UUID> erasedMemberIds = householdService.eraseAccountMemberships(accountId, clock.instant());
        erasedMemberIds.forEach(this::eraseMemberData);
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
     * this method ever reaches {@link #eraseMemberData}.
     */
    @Transactional
    public void eraseChild(UUID callerAccountId, UUID householdId, UUID childMemberId) {
        householdService.eraseChildMember(callerAccountId, householdId, childMemberId);
        eraseMemberData(childMemberId);

        audit.record(AuditAction.DATA_ERASURE_COMPLETED, "HouseholdMember", childMemberId,
                callerAccountId, householdId, AuditOutcome.SUCCESS, Map.of());
    }

    // -------------------------------------------------------------------- internals

    /**
     * Everything the member's own record holds, module by module, inside the caller's
     * transaction. Nothing here touches another member's rows: each call is keyed on this
     * member id alone.
     *
     * <p>Meals go first. A meal the member shared keeps the other diners' portions and their
     * own derived nutrition; a meal only this member ate goes entirely. Doing it before the
     * observations means nothing that runs afterwards can find a portion of this member's to
     * derive from. Sharing goes last among the health modules: the member's own grants are
     * deleted with their data, and grants others made <em>to</em> them are revoked but kept,
     * because those are the other person's sharing history.
     */
    private void eraseMemberData(UUID memberId) {
        meals.eraseForMember(memberId);
        wellness.eraseAllForMember(memberId);
        goals.eraseAllForMember(memberId);
        habits.eraseAllForMember(memberId);
        accessGrants.eraseForMember(memberId);
        profileService.eraseAllVersions(memberId);
    }
}
