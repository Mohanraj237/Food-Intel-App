import { useQuery } from '@tanstack/react-query';
import { listMembers, type HouseholdMember } from './household';
import { getProfile, type Profile } from './profile';
import { useMyHousehold } from './useMyHousehold';
import { useAccountId } from './useSession';

/**
 * "Which household member am I?"
 *
 * Individual Mode needs a *member* id, not an account id, because every health record in the
 * product hangs off `household_member` (D-035 — Individual Mode is a household of one, not a
 * second person model). This resolves the one from the other in the single place that should
 * know how, rather than each screen re-deriving it from a members list.
 *
 * Returns `memberId: null` while loading, on error, and for a signed-in account that has no
 * household yet — callers that need to tell those apart read `isPending`/`isError`, the same
 * contract `useMyHousehold` already uses.
 */
export interface UseMe {
  memberId: string | null;
  member: HouseholdMember | null;
  householdId: string | null;
  /** `null` when the profile has not been created yet — a normal state, not an error. */
  profile: Profile | null;
  /** True when the member exists but has no profile; the age gate cannot be evaluated. */
  needsProfile: boolean;
  isPending: boolean;
  isError: boolean;
}

export function useMe(): UseMe {
  const accountId = useAccountId();
  const household = useMyHousehold({ enabled: accountId !== null });
  const householdId = household.householdId;

  const members = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: householdId !== null,
  });

  const me = (members.data ?? []).find((m) => m.userAccountId === accountId) ?? null;

  const profile = useQuery({
    queryKey: ['profile', householdId, me?.id],
    queryFn: () => getProfile(householdId as string, me?.id as string),
    enabled: householdId !== null && me !== null,
    // A 404 means "no profile yet", which is a state this hook reports rather than retries.
    retry: false,
  });

  return {
    memberId: me?.id ?? null,
    member: me,
    householdId,
    profile: profile.data ?? null,
    needsProfile: me !== null && profile.isError,
    isPending:
      accountId !== null &&
      (household.isPending || (householdId !== null && members.isPending)),
    isError: household.isError || members.isError,
  };
}
