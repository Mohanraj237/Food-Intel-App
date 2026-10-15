package com.familyfood.experience.application;

import com.familyfood.experience.domain.ExperienceTier;
import com.familyfood.profile.domain.ActivityLevel;
import com.familyfood.profile.domain.AgeBand;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Who the signed-in person is to the product, in one read: which lens they start in (D-043),
 * which experience they get and what it offers (D-046), and whether tracking is switched on for
 * them (D-048). The client renders this and never works any of it out for itself.
 *
 * <p><b>Nulls are written, not omitted.</b> The API's default is to drop null fields; here a
 * missing {@code household} and a {@code household} that is {@code null} would mean the same
 * thing to a careful client and different things to a hurried one, so this shape states its
 * nulls explicitly ({@code household}, {@code member}, {@code profile.ageBand},
 * {@code profile.activityLevel}).
 *
 * @param mode                NONE until the person has a household; INDIVIDUAL for a household of
 *                            one made through "Just me"; FAMILY otherwise (D-043)
 * @param household           the primary household — the caller's oldest live membership — or
 *                            {@code null} when mode is NONE
 * @param member              the caller's own membership in that household, or {@code null}
 * @param profile             whether the caller has a profile yet, with the two fields the home
 *                            screen needs; never anyone else's
 * @param experience          the caller's own tier; UNKNOWN until an age band is known
 * @param capabilities        what that tier offers (see {@link Capabilities})
 * @param trackingConsent     the caller's own tracking purpose and whether it is active (D-048)
 * @param otherHouseholdCount live memberships beyond the primary one
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MeView(UUID accountId,
                     Mode mode,
                     HouseholdPart household,
                     MemberPart member,
                     ProfilePart profile,
                     ExperienceTier experience,
                     Capabilities capabilities,
                     TrackingConsentPart trackingConsent,
                     int otherHouseholdCount) {

    /** The lens a person starts in (D-043). Presentation only — the data path is the same. */
    public enum Mode { NONE, INDIVIDUAL, FAMILY }

    /**
     * @param individual  created through "Just me" and not yet joined by anyone else (D-043)
     * @param timeZone    IANA zone whose midnight bounds this household's days (D-053)
     * @param memberCount live members, the caller included
     */
    public record HouseholdPart(UUID id, String name, boolean individual, String timeZone, int memberCount) {}

    /** The caller's own membership: the label they chose, and the household's own vocabulary. */
    public record MemberPart(UUID id, String displayLabel, String memberType, String role) {}

    /**
     * @param exists        false until a profile has been saved — the product then asks for one
     *                      before tracking (PROFILE_REQUIRED_FOR_METRIC)
     * @param ageBand       {@code null} when there is no profile
     * @param activityLevel {@code null} when not stated
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProfilePart(boolean exists, AgeBand ageBand, ActivityLevel activityLevel) {

        static ProfilePart none() {
            return new ProfilePart(false, null, null);
        }
    }

    /** @param purposeCode HEALTH_TRACKING for an account holder — every account is an adult */
    public record TrackingConsentPart(String purposeCode, boolean active) {}
}
