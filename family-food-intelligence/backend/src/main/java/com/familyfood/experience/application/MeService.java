package com.familyfood.experience.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.Relationship;
import com.familyfood.experience.application.ExperienceService.MemberExperience;
import com.familyfood.experience.application.MeView.HouseholdPart;
import com.familyfood.experience.application.MeView.MemberPart;
import com.familyfood.experience.application.MeView.Mode;
import com.familyfood.experience.application.MeView.ProfilePart;
import com.familyfood.experience.application.MeView.TrackingConsentPart;
import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.HouseholdInfo;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.household.application.HouseholdAccess.MemberSummary;
import com.familyfood.household.application.HouseholdService;
import com.familyfood.household.application.HouseholdService.IndividualProvisioning;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * "Me" (D-043): the signed-in person's own standing, and the "Just me" onboarding path.
 *
 * <p>Composition only. Membership comes from {@link HouseholdAccess}, the tier from
 * {@link ExperienceService}, the profile from {@link ProfileService}'s self read, and consent from
 * {@link TrackingConsent}; nothing here decides a rule those modules own.
 *
 * <p><b>Primary household.</b> A person can belong to more than one household. The one this
 * screen describes is their oldest live membership — the same default the household list gives
 * the frontend — and the rest are counted, not hidden.
 */
@Service
public class MeService {

    /** Matches {@code household_member.display_label}'s CHECK (1..60 after trimming). */
    static final int DISPLAY_LABEL_MAX_LENGTH = 60;

    private final HouseholdAccess households;
    private final HouseholdService householdService;
    private final ProfileService profiles;
    private final ExperienceService experiences;
    private final TrackingConsent trackingConsent;
    private final HealthAccessPolicy access;

    public MeService(HouseholdAccess households,
                     HouseholdService householdService,
                     ProfileService profiles,
                     ExperienceService experiences,
                     TrackingConsent trackingConsent,
                     HealthAccessPolicy access) {
        this.households = households;
        this.householdService = householdService;
        this.profiles = profiles;
        this.experiences = experiences;
        this.trackingConsent = trackingConsent;
        this.access = access;
    }

    /** The caller's own view of themselves. Never another person's. */
    @Transactional(readOnly = true)
    public MeView me(UUID callerAccountId) {
        return build(callerAccountId);
    }

    /**
     * "Just me" (D-043): a household of one, then the caller's view of themselves.
     *
     * <p>Idempotent — {@link HouseholdService#provisionIndividual} never makes a second
     * household. {@code created} says which happened, so the API can answer 201 or 200. One
     * transaction, so the view is built from exactly what was just written.
     */
    @Transactional
    public Provisioned provisionIndividual(UUID callerAccountId, String displayLabel) {
        String label = requireDisplayLabel(displayLabel);
        IndividualProvisioning result = householdService.provisionIndividual(callerAccountId, label);
        return new Provisioned(build(callerAccountId), result.created());
    }

    /** @param created false when the caller already had a household and nothing was made */
    public record Provisioned(MeView me, boolean created) {}

    /**
     * A member's tier as the caller may be told it (D-046) — the full tier for the member
     * themselves, their guardian or a caregiver they chose; otherwise only child or adult.
     *
     * <p>Only for someone who shares the member's household. A member id is not a public fact:
     * an account outside the household gets "not found", exactly as if the id did not exist,
     * rather than learning whether a stranger's id belongs to a child.
     */
    @Transactional(readOnly = true)
    public MemberExperience experienceOf(UUID callerAccountId, UUID memberId) {
        AccessDecision decision = access.decide(callerAccountId, memberId);
        if (decision.relationship() == Relationship.NONE
                && households.findActiveMembership(decision.householdId(), callerAccountId).isEmpty()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ExperienceTier tier = experiences.visibleTier(decision);
        return new MemberExperience(memberId, tier, Capabilities.forTier(tier));
    }

    /* ------------------------------------------------------------------ internals */

    private MeView build(UUID callerAccountId) {
        List<MemberInfo> memberships = households.findActiveMembershipsOf(callerAccountId);

        MemberInfo primary = null;
        HouseholdInfo primaryHousehold = null;
        int liveMemberships = 0;
        for (MemberInfo membership : memberships) {
            Optional<HouseholdInfo> household = households.findHousehold(membership.householdId());
            if (household.isEmpty()) {
                continue; // a membership of a household that no longer exists describes nothing
            }
            liveMemberships++;
            if (primary == null) { // oldest first, per HouseholdAccess.findActiveMembershipsOf
                primary = membership;
                primaryHousehold = household.get();
            }
        }

        if (primary == null) {
            ExperienceTier unknown = ExperienceTier.UNKNOWN;
            return new MeView(callerAccountId, Mode.NONE, null, null, ProfilePart.none(), unknown,
                    Capabilities.forTier(unknown),
                    new TrackingConsentPart(TrackingConsent.ADULT_PURPOSE, false), 0);
        }

        List<MemberSummary> summaries = households.listActiveMemberSummaries(primaryHousehold.id());
        MemberInfo self = primary;
        MemberSummary own = summaries.stream()
                .filter(s -> s.id().equals(self.id()))
                .findFirst()
                .or(() -> households.findMemberSummary(self.id()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        HouseholdPart household = new HouseholdPart(primaryHousehold.id(), primaryHousehold.name(),
                primaryHousehold.individual(), primaryHousehold.zone().getId(), summaries.size());
        MemberPart member = new MemberPart(own.id(), own.displayLabel(), own.memberType(), own.role());
        ExperienceTier tier = experiences.tierOf(self);

        return new MeView(callerAccountId,
                primaryHousehold.individual() ? Mode.INDIVIDUAL : Mode.FAMILY,
                household,
                member,
                profileOf(callerAccountId, self),
                tier,
                Capabilities.forTier(tier),
                new TrackingConsentPart(trackingConsent.purposeFor(self), trackingConsent.isActive(self)),
                Math.max(0, liveMemberships - 1));
    }

    /**
     * The caller's own profile, through the self read. "No profile yet" is established first
     * with the trusted band lookup, so the self read is only asked for a profile that exists:
     * letting it throw "not found" inside this transaction would mark the whole read for
     * rollback.
     */
    private ProfilePart profileOf(UUID callerAccountId, MemberInfo self) {
        if (profiles.currentAgeBand(self.id()).isEmpty()) {
            return ProfilePart.none();
        }
        ProfileView view = profiles.getCurrentProfile(callerAccountId, self.id());
        return new ProfilePart(true, view.ageBand(), view.activityLevel());
    }

    /** Trimmed here, and measured after trimming, as the column CHECK measures it. */
    private static String requireDisplayLabel(String displayLabel) {
        String trimmed = displayLabel == null ? "" : displayLabel.trim();
        if (trimmed.isEmpty() || trimmed.length() > DISPLAY_LABEL_MAX_LENGTH) {
            throw AppException.validation("displayLabel",
                    "must be 1 to " + DISPLAY_LABEL_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
