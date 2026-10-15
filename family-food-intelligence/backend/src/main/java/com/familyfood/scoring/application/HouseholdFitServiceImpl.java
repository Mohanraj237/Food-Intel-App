package com.familyfood.scoring.application;

import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates {@link PersonalFitView}s across a household, scoped to exactly what the calling
 * account is authorized to see (D-002, D-023, D-024 point 4).
 *
 * <p>Two facts are derived from {@link HouseholdAccess#listActiveMembers(UUID)} — the only
 * sanctioned way to enumerate a household's membership — rather than from any method
 * published by {@code household} for a caller-scoped check, because no such method is part
 * of the published contract:
 *
 * <ul>
 *   <li>whether the caller is themselves a live member of this household at all (if not,
 *       {@link ErrorCode#NOT_HOUSEHOLD_MEMBER}, mirroring {@code HouseholdService}'s own
 *       {@code requireActiveMember} check byte-for-byte in meaning, though not in
 *       implementation — that method is private to {@code household});
 *   <li>which other live members, if any, the caller is additionally authorized to see: only
 *       {@code CHILD} members the caller currently guards. Never another {@code ADULT}
 *       member, not even the household owner asking about their own household — that is the
 *       D-002 scope this class exists to enforce structurally, not a gap to work around.
 * </ul>
 */
@Service
public class HouseholdFitServiceImpl implements HouseholdFitService {

    private static final String CHILD_MEMBER_TYPE = "CHILD";

    private final HouseholdAccess householdAccess;
    private final PersonalFitService personalFitService;
    private final Clock clock;

    public HouseholdFitServiceImpl(HouseholdAccess householdAccess,
                                   PersonalFitService personalFitService,
                                   Clock clock) {
        this.householdAccess = householdAccess;
        this.personalFitService = personalFitService;
        this.clock = clock;
    }

    @Override
    public HouseholdFitView evaluate(UUID callerAccountId, UUID householdId, ProductAssessmentInput input) {
        return evaluateMany(callerAccountId, householdId, List.of(input)).get(0);
    }

    @Override
    public List<HouseholdFitView> evaluateMany(UUID callerAccountId, UUID householdId,
                                               List<ProductAssessmentInput> inputs) {
        List<MemberInfo> liveMembers = householdAccess.listActiveMembers(householdId);

        MemberInfo self = liveMembers.stream()
                .filter(member -> callerAccountId.equals(member.userAccountId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_HOUSEHOLD_MEMBER));

        List<MemberInfo> visible = liveMembers.stream()
                .filter(member -> isAuthorizedForCaller(callerAccountId, self, member))
                // listActiveMembers makes no ordering guarantee; sorting by member id gives a
                // stable, reproducible order so that two calls with identical inputs produce
                // byte-for-byte identical HouseholdFitView content (aside from computedAt).
                .sorted(Comparator.comparing(MemberInfo::id))
                .toList();

        // One pass per member over every product: authorization, consent and profile once each.
        List<List<PersonalFitView>> byMember = visible.stream()
                .map(member -> personalFitService.evaluateMany(callerAccountId, member.id(), inputs))
                .toList();

        java.util.ArrayList<HouseholdFitView> result = new java.util.ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            final int index = i;
            List<PersonalFitView> memberViews = byMember.stream().map(views -> views.get(index)).toList();
            result.add(new HouseholdFitView(householdId, inputs.get(i).productVersionId(), memberViews, clock.instant()));
        }
        return List.copyOf(result);
    }

    /**
     * The caller always sees their own membership. Beyond that, only a {@code CHILD} member
     * the caller currently guards — never another {@code ADULT} member, which is exactly the
     * D-002 restriction D-024 point 4 makes structural for household fit.
     */
    private boolean isAuthorizedForCaller(UUID callerAccountId, MemberInfo self, MemberInfo candidate) {
        if (candidate.id().equals(self.id())) {
            return true;
        }
        return CHILD_MEMBER_TYPE.equals(candidate.memberType())
                && householdAccess.isGuardianOf(callerAccountId, candidate.id());
    }
}
