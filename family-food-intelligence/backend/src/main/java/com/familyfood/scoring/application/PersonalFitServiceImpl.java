package com.familyfood.scoring.application;

import com.familyfood.consent.application.ConsentGate;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.profile.domain.GoalCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link PersonalFitService}: authorize (mirroring {@code ProfileService}'s D-001/
 * D-002 shape exactly, re-derived rather than borrowed — see class javadoc on
 * {@code ProfileService}), gate on consent (D-018), read the member's own current profile,
 * compute the factual allergen match (D-024 point 2), and aggregate a goal-based verdict
 * across every goal the profile records (most-severe-wins, per
 * {@code system-architecture.md} §5).
 *
 * <p>Every early-exit path returns a {@link PersonalFitView} rather than throwing, exactly
 * as {@link PersonalFitService#evaluate} documents: a household-fit caller assembling
 * several of these needs to skip one member's unavailable fit without failing the whole
 * request.
 *
 * <p>Never logs profile contents (goals, allergens, conditions) — only ids, and even that
 * only incidentally via whatever {@code ConsentGate}/{@code ProfileService} already audit
 * downstream. This class itself does not log at all.
 */
@Service
public class PersonalFitServiceImpl implements PersonalFitService {

    private static final String CHILD_MEMBER_TYPE = "CHILD";
    private static final String PURPOSE_CHILD_FOOD_SUITABILITY = "CHILD_FOOD_SUITABILITY";
    private static final String PURPOSE_PERSONAL_FIT = "PERSONAL_FIT";

    private static final String VERDICT_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    /** Higher rank = more severe. Only the four real verdicts participate in aggregation. */
    private static final Map<String, Integer> VERDICT_SEVERITY = Map.of(
            "EVERYDAY", 0,
            "OCCASIONAL", 1,
            "LIMIT", 2,
            "DOESNT_FIT", 3);

    private final HouseholdAccess householdAccess;
    private final ConsentGate consentGate;
    private final ProfileService profileService;
    private final RuleEngine ruleEngine;
    private final Clock clock;

    public PersonalFitServiceImpl(HouseholdAccess householdAccess,
                                  ConsentGate consentGate,
                                  ProfileService profileService,
                                  RuleEngine ruleEngine,
                                  Clock clock) {
        this.householdAccess = householdAccess;
        this.consentGate = consentGate;
        this.profileService = profileService;
        this.ruleEngine = ruleEngine;
        this.clock = clock;
    }

    @Override
    public PersonalFitView evaluate(UUID callerAccountId, UUID memberId, ProductAssessmentInput input) {
        Optional<MemberInfo> maybeMember = householdAccess.findMember(memberId);
        if (maybeMember.isEmpty() || !maybeMember.get().active()) {
            return notAuthorized(memberId);
        }

        MemberInfo member = maybeMember.get();
        boolean isChild = CHILD_MEMBER_TYPE.equals(member.memberType());
        if (isChild) {
            if (!householdAccess.isGuardianOf(callerAccountId, memberId)) {
                return notAuthorized(memberId);
            }
        } else if (!callerAccountId.equals(member.userAccountId())) {
            return notAuthorized(memberId);
        }

        String purposeCode = isChild ? PURPOSE_CHILD_FOOD_SUITABILITY : PURPOSE_PERSONAL_FIT;
        try {
            consentGate.require(memberId, purposeCode);
        } catch (AppException e) {
            if (e.code() == ErrorCode.CONSENT_REQUIRED) {
                return statusOnly(memberId, "CONSENT_REQUIRED");
            }
            if (e.code() == ErrorCode.CONSENT_WITHDRAWN) {
                return statusOnly(memberId, "CONSENT_WITHDRAWN");
            }
            throw e;
        }

        ProfileView profile;
        try {
            profile = profileService.getCurrentProfile(callerAccountId, memberId);
        } catch (AppException e) {
            if (e.code() == ErrorCode.RESOURCE_NOT_FOUND) {
                return statusOnly(memberId, "NO_PROFILE");
            }
            // Defensive only: step 1 already authorized against the same HouseholdAccess
            // facts ProfileService re-derives internally, so this should not normally
            // fire. Treated as NOT_AUTHORIZED rather than propagated, per the contract
            // that this method never throws for an authorization-shaped outcome.
            return notAuthorized(memberId);
        }

        List<String> allergenMatches = matchAllergens(input.declaredAllergenCodes(), profile.allergens());

        String ruleSetVersion = ruleEngine.activeRuleSetVersion();
        String verdict;
        List<Reason> reasons;

        Set<GoalCode> goals = profile.goals();
        if (goals == null || goals.isEmpty()) {
            verdict = VERDICT_INSUFFICIENT_DATA;
            reasons = List.of(new Reason("NO_GOALS_SET", null, null, null, null, null));
        } else {
            String ageBand = profile.ageBand().name();
            Map<String, Reason> reasonsByCode = new LinkedHashMap<>();
            String mostSevere = null;

            for (GoalCode goal : goals) {
                RuleOutcome outcome = ruleEngine.evaluateGoal(ruleSetVersion, goal.name(), input, ageBand);
                String goalVerdict = outcome.verdict();
                if (!VERDICT_INSUFFICIENT_DATA.equals(goalVerdict)
                        && (mostSevere == null || VERDICT_SEVERITY.get(goalVerdict) > VERDICT_SEVERITY.get(mostSevere))) {
                    mostSevere = goalVerdict;
                }
                for (Reason reason : outcome.reasons()) {
                    reasonsByCode.putIfAbsent(reason.code(), reason);
                }
            }

            verdict = mostSevere != null ? mostSevere : VERDICT_INSUFFICIENT_DATA;
            reasons = List.copyOf(reasonsByCode.values());
        }

        return new PersonalFitView(memberId, "ASSESSED", verdict, reasons, allergenMatches,
                ruleSetVersion, clock.instant());
    }

    private List<String> matchAllergens(List<String> declaredAllergenCodes, Set<AllergenCode> profileAllergens) {
        if (declaredAllergenCodes == null || declaredAllergenCodes.isEmpty()
                || profileAllergens == null || profileAllergens.isEmpty()) {
            return List.of();
        }

        Set<String> profileCodes = new LinkedHashSet<>();
        for (AllergenCode code : profileAllergens) {
            profileCodes.add(code.name());
        }

        List<String> matches = new ArrayList<>();
        for (String declaredCode : declaredAllergenCodes) {
            if (profileCodes.contains(declaredCode) && !matches.contains(declaredCode)) {
                matches.add(declaredCode);
            }
        }
        return List.copyOf(matches);
    }

    private PersonalFitView notAuthorized(UUID memberId) {
        return statusOnly(memberId, "NOT_AUTHORIZED");
    }

    private PersonalFitView statusOnly(UUID memberId, String status) {
        return new PersonalFitView(memberId, status, null, List.of(), List.of(), null, clock.instant());
    }
}
