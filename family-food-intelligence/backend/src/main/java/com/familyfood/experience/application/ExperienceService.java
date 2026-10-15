package com.familyfood.experience.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.domain.Relationship;
import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.domain.AgeBand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Derives a member's experience tier (D-046).
 *
 * <p>The tier is computed from the member type and the current profile's age band, through the
 * trusted {@link ProfileService#currentAgeBand} read: it is consumed here to decide what the
 * product offers, not disclosed.
 *
 * <p><b>What another person is told.</b> A member's full tier reveals their age class, and
 * {@code OLDER_ADULT} reveals an age band — profile data that is theirs (D-002). So a caller who
 * is not the member, their guardian or a caregiver they chose is told only what the household
 * member list already shows: child or adult.
 */
@Service
public class ExperienceService {

    private static final String CHILD_MEMBER_TYPE = "CHILD";

    private final ProfileService profiles;
    private final HealthAccessPolicy access;

    public ExperienceService(ProfileService profiles, HealthAccessPolicy access) {
        this.profiles = profiles;
        this.access = access;
    }

    /** The member's own tier. Not authorized: callers pass a member they have resolved. */
    @Transactional(readOnly = true)
    public ExperienceTier tierOf(MemberInfo member) {
        if (CHILD_MEMBER_TYPE.equals(member.memberType())) {
            return ExperienceTier.CHILD;
        }
        Optional<AgeBand> band = profiles.currentAgeBand(member.id());
        if (band.isEmpty()) {
            return ExperienceTier.UNKNOWN;
        }
        if (band.get().isChild()) {
            return ExperienceTier.CHILD; // fail closed; ProfileService now refuses this pairing
        }
        return band.get().isOlderAdult() ? ExperienceTier.OLDER_ADULT : ExperienceTier.ADULT;
    }

    /** The tier as the caller may be told it, with the capabilities that follow from it. */
    @Transactional(readOnly = true)
    public MemberExperience experienceFor(UUID callerAccountId, UUID memberId) {
        AccessDecision decision = access.decide(callerAccountId, memberId);
        ExperienceTier tier = visibleTier(decision);
        return new MemberExperience(memberId, tier, Capabilities.forTier(tier));
    }

    /** As {@link #experienceFor}, for a decision the caller already holds. */
    public ExperienceTier visibleTier(AccessDecision decision) {
        Relationship r = decision.relationship();
        if (r == Relationship.SELF || r == Relationship.GUARDIAN || r == Relationship.CAREGIVER) {
            return tierOf(decision.subject());
        }
        // Shared-with or unrelated: what the member list already says, nothing more.
        return decision.isChildSubject() ? ExperienceTier.CHILD : ExperienceTier.ADULT;
    }

    public record MemberExperience(UUID memberId, ExperienceTier tier, Capabilities capabilities) {}
}
