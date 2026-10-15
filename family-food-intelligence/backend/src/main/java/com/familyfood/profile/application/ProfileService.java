package com.familyfood.profile.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditEvent.AuditOutcome;
import com.familyfood.audit.AuditService;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.domain.ActivityLevel;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.DietaryPreference;
import com.familyfood.profile.domain.GoalCode;
import com.familyfood.profile.domain.PersonProfile;
import com.familyfood.profile.domain.Sex;
import com.familyfood.profile.infra.PersonProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read and write access to a household member's profile.
 *
 * <p>Every method here runs the same authorization rule before touching any data (D-001,
 * D-002 made structural):
 *
 * <ul>
 *   <li>the target member must exist and be active — a departed member has no live
 *       profile access, regardless of who is asking;
 *   <li>a {@code CHILD} member's profile may only be written or read by one of their
 *       guardians ({@link HouseholdAccess#isGuardianOf});
 *   <li>an {@code ADULT} member's profile may only be written or read by that member
 *       themselves — nobody else, not even the household owner, may touch another adult's
 *       data (D-002).
 * </ul>
 *
 * <p>Consent-gating of profile <em>reads</em> (D-018) is out of scope here by design: it is
 * a later integration the orchestrator wires once a {@code ConsentGate} exists. This
 * service enforces membership/guardianship authorization only.
 */
@Service
public class ProfileService {

    /** {@code household_member.member_type} value for a dependent child. */
    private static final String CHILD_MEMBER_TYPE = "CHILD";
    private static final String RESOURCE_TYPE_PROFILE = "PersonProfile";

    private final PersonProfileRepository profiles;
    private final HouseholdAccess householdAccess;
    private final AuditService audit;
    private final Clock clock;

    public ProfileService(PersonProfileRepository profiles,
                          HouseholdAccess householdAccess,
                          AuditService audit,
                          Clock clock) {
        this.profiles = profiles;
        this.householdAccess = householdAccess;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Creates version 1 if the member has no profile yet, otherwise supersedes the
     * current version and inserts {@code version + 1}. Both writes happen in this one
     * transaction so that the "exactly one current row" invariant is never observable as
     * violated, even transiently.
     */
    @Transactional
    public ProfileView createOrUpdateProfile(UUID callerAccountId,
                                             UUID targetMemberId,
                                             AgeBand ageBand,
                                             Sex sexOrNull,
                                             DietaryPreference dietaryPreference,
                                             Set<GoalCode> goals,
                                             Set<AllergenCode> allergenCodes,
                                             Set<String> conditions) {
        return createOrUpdateProfile(callerAccountId, targetMemberId, ageBand, sexOrNull,
                dietaryPreference, null, goals, allergenCodes, conditions);
    }

    /**
     * As above, with the self-reported activity level (V14).
     *
     * <p><b>The band must match the member (D-046).</b> A CHILD member may only hold a child
     * band, and an adult account only an adult band. The band is the input to every age gate
     * in the product — metrics (D-036), child goals (D-047), the experience tier — so a child
     * saved as {@code A31_45} would have every adult-only metric opened to them by a form
     * choice. The UI already restricts the options; this is the place it becomes true.
     */
    @Transactional
    public ProfileView createOrUpdateProfile(UUID callerAccountId,
                                             UUID targetMemberId,
                                             AgeBand ageBand,
                                             Sex sexOrNull,
                                             DietaryPreference dietaryPreference,
                                             ActivityLevel activityLevelOrNull,
                                             Set<GoalCode> goals,
                                             Set<AllergenCode> allergenCodes,
                                             Set<String> conditions) {
        MemberInfo member = authorize(callerAccountId, targetMemberId);
        requireBandMatchesMember(member, ageBand);
        Instant now = clock.instant();

        Optional<PersonProfile> current =
                profiles.findByHouseholdMemberIdAndIsCurrentTrue(targetMemberId);
        int nextVersion = 1;
        boolean isUpdate = current.isPresent();
        if (current.isPresent()) {
            PersonProfile previous = current.get();
            previous.supersede(now);
            // saveAndFlush, not save: Hibernate's default flush ordering executes pending
            // inserts before pending updates within a single flush. Without forcing this
            // update to hit the database now, the new row's INSERT (is_current = true)
            // would be attempted while the old row is still is_current = true too, and
            // uq_profile_current — the one-current-row-per-member partial unique index —
            // would reject it even though the end state is correct.
            profiles.saveAndFlush(previous);
            nextVersion = previous.getVersion() + 1;
        }

        PersonProfile created = PersonProfile.newVersion(
                targetMemberId, nextVersion, ageBand, sexOrNull, dietaryPreference,
                activityLevelOrNull, goals, allergenCodes, conditions, callerAccountId, now);
        profiles.save(created);

        boolean isChild = member.memberType().equals(CHILD_MEMBER_TYPE);
        AuditAction action;
        if (isChild) {
            action = isUpdate ? AuditAction.CHILD_PROFILE_UPDATED : AuditAction.CHILD_PROFILE_CREATED;
        } else {
            action = isUpdate ? AuditAction.PROFILE_UPDATED : AuditAction.PROFILE_CREATED;
        }
        audit.record(action, RESOURCE_TYPE_PROFILE, created.getId(), callerAccountId,
                member.householdId(), AuditOutcome.SUCCESS,
                Map.of("householdMemberId", targetMemberId, "version", nextVersion));

        return ProfileView.from(created);
    }

    @Transactional(readOnly = true)
    public ProfileView getCurrentProfile(UUID callerAccountId, UUID targetMemberId) {
        MemberInfo member = authorize(callerAccountId, targetMemberId);

        PersonProfile current = profiles.findByHouseholdMemberIdAndIsCurrentTrue(targetMemberId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (member.memberType().equals(CHILD_MEMBER_TYPE)) {
            // Mandatory per the Constitution's child-data-access auditing rule: every read
            // of a child's data is logged, not just writes.
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_PROFILE, current.getId(),
                    callerAccountId, member.householdId());
        }

        return ProfileView.from(current);
    }

    /**
     * Every version, oldest omissions aside — the export counterpart to {@link
     * #getCurrentProfile} (AC-9.2). Same authorization, same child-access auditing; unlike
     * {@link #getCurrentProfile} this never throws {@code RESOURCE_NOT_FOUND} for a member
     * with no profile yet — it returns an empty list, since "no profile" is a valid export
     * result, not a failure.
     */
    @Transactional(readOnly = true)
    public List<ProfileView> getVersionHistory(UUID callerAccountId, UUID targetMemberId) {
        MemberInfo member = authorize(callerAccountId, targetMemberId);

        List<PersonProfile> versions = profiles.findByHouseholdMemberIdOrderByVersionDesc(targetMemberId);

        if (!versions.isEmpty() && member.memberType().equals(CHILD_MEMBER_TYPE)) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_PROFILE,
                    targetMemberId, callerAccountId, member.householdId());
        }

        return versions.stream().map(ProfileView::from).toList();
    }

    /**
     * Hard-deletes every version of a member's profile (AC-9.3: a profile — age band, sex,
     * dietary preference, goals, allergens, self-declared conditions — is exactly the
     * personal data erasure must remove). {@code profile_goal}/{@code profile_allergen}/
     * {@code profile_condition} cascade from the database FK, not from application code.
     *
     * <p>Deliberately unauthenticated and unauthorized, unlike every other method here —
     * this is a trusted-caller operation in the same style as {@code
     * RefreshTokenRevoker.revokeAllForAccount}: it exists to be called by an already-
     * authorized orchestrator ({@code datarights.application.DataRightsService}, which
     * verifies guardian/self standing before ever reaching this method), not by a
     * controller acting on a raw request. A no-op if the member never had a profile.
     */
    @Transactional
    public void eraseAllVersions(UUID targetMemberId) {
        profiles.deleteByHouseholdMemberId(targetMemberId);
    }

    /**
     * The member's current age band, for a safety gate — or empty if they have no profile.
     *
     * <p><b>A trusted-caller read, like {@link #eraseAllVersions}.</b> It exists because the
     * age gates (D-036 metrics, D-047 goals, D-046 experience tier) must be evaluated for the
     * <em>subject</em> regardless of who is acting. A caregiver recording an elder's blood
     * pressure (D-045) is not permitted to read the elder's profile — and must not need to be,
     * for the product to check that the elder is an adult. Returning only the band, with no
     * audit, is deliberate: the band is not disclosed to anyone by this call, it is consumed
     * by a gate. It must never be surfaced through an API response on another person's
     * behalf; {@link #getCurrentProfile} is the disclosure path, and it stays self/guardian.
     */
    @Transactional(readOnly = true)
    public Optional<AgeBand> currentAgeBand(UUID targetMemberId) {
        return profiles.findByHouseholdMemberIdAndIsCurrentTrue(targetMemberId)
                .map(PersonProfile::getAgeBand);
    }

    /**
     * D-046. A child member must hold a child band; an adult account must hold an adult band.
     * Checked against the membership record, which the caller cannot change, rather than
     * against anything in the request.
     */
    private static void requireBandMatchesMember(MemberInfo member, AgeBand ageBand) {
        if (ageBand == null) {
            return; // the not-null rule is reported by the request validator and the entity
        }
        boolean memberIsChild = member.memberType().equals(CHILD_MEMBER_TYPE);
        if (memberIsChild != ageBand.isChild()) {
            throw new AppException(ErrorCode.AGE_BAND_NOT_VALID_FOR_MEMBER,
                    Map.of("ageBand", ageBand.name(), "memberType", member.memberType()));
        }
    }

    /**
     * D-001 / D-002 made structural. Identical for every profile operation, per the
     * published contract: a missing or departed member is {@code RESOURCE_NOT_FOUND}, a
     * child's profile is guardian-only, an adult's profile is self-only.
     */
    private MemberInfo authorize(UUID callerAccountId, UUID targetMemberId) {
        MemberInfo member = householdAccess.findMember(targetMemberId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!member.active()) {
            // A departed member has no live profile access (deliberately indistinguishable
            // from "never existed" — see published HouseholdAccess contract).
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (member.memberType().equals(CHILD_MEMBER_TYPE)) {
            if (!householdAccess.isGuardianOf(callerAccountId, targetMemberId)) {
                audit.denied(AuditAction.CHILD_DATA_ACCESS_DENIED, RESOURCE_TYPE_PROFILE,
                        targetMemberId, callerAccountId, member.householdId(),
                        "NOT_A_GUARDIAN");
                throw new AppException(ErrorCode.NOT_A_GUARDIAN);
            }
        } else if (!callerAccountId.equals(member.userAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return member;
    }
}
