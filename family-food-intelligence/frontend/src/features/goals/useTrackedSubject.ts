import { useQuery } from '@tanstack/react-query';
import { ApiError } from '@/lib/api';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery, getMemberExperience, type Capabilities, type ExperienceTier } from '@/lib/me';
import { listMembers } from '@/lib/household';
import { useTodaySummary } from '@/lib/dashboard';
import type { Relationship } from '@/lib/access';

/**
 * Who this Goals/Habits/Trackers screen is about, shared by all three (plan §18 1.3/1.4).
 *
 * `/goals`, `/habits` and `/trackers/:category` show the caller's own record; their
 * `/family/:memberId/...` twins show one other person's, reached only from a link the Family
 * hub/member view already gated on a real relationship (never a bare picker here — plan §7 keeps
 * subject-switching to the Log sheet and `MemberView`'s "Log for {name}" button). Resolving
 * "who, and what of them may I see" is therefore the one thing all three screens need before
 * they can render anything, and doing it once here keeps each screen a single `useQuery` fan-out
 * rather than three copies of the same relationship/visibility logic.
 *
 * `GET /members/{id}/today` is the one call used for this even though the screen is not Today
 * itself: it is the existing endpoint that already answers exactly "does the caller have any
 * relationship to this member, and which categories may they see" (`relationship`,
 * `visibleScopes`), and reaching for a second bespoke call here would be the redundant fan-out
 * plan §11 warns against. A member the caller has no relationship to 403s, which is the correct
 * outcome — the caller only ever reaches this hook via a link that already established one.
 */

export type TrackedSubject =
  | { status: 'loading' }
  | { status: 'error'; error: unknown; retry: () => void }
  | { status: 'not-shared'; displayLabel: string | null }
  | {
      status: 'ready';
      memberId: string;
      isSelf: boolean;
      displayLabel: string;
      relationship: Relationship;
      visibleScopes: readonly string[];
      timeZone: string;
      tier: ExperienceTier;
      capabilities: Capabilities;
      isChild: boolean;
    };

export function useTrackedSubject(routeMemberId?: string): TrackedSubject {
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });
  const selfMemberId = me.data?.member?.id ?? null;
  const householdId = me.data?.household?.id ?? null;
  const isSelf = routeMemberId === undefined;
  const targetMemberId = isSelf ? selfMemberId : (routeMemberId as string);

  // Only used to put a name on the "not shared with you" state when the target turns out to be
  // in the caller's own household (the common case — a child, or another adult in the same
  // household who has not shared anything). The roster call itself never 403s; it is not a
  // wellness/access endpoint, and knowing a housemate's name is not the thing being gated.
  const roster = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: !isSelf && householdId !== null,
  });

  const today = useTodaySummary(targetMemberId, undefined);

  // For self, `/me` already carries the caller's own tier/capabilities (`me.data.experience`/
  // `.capabilities`) — a second `/members/{id}/experience` call for the same person would be
  // exactly the redundant fan-out plan §11 warns against, so this only ever fires for a member.
  const experience = useQuery({
    queryKey: ['experience', targetMemberId],
    queryFn: () => getMemberExperience(targetMemberId as string),
    enabled: !isSelf && targetMemberId !== null && today.isSuccess,
  });

  if (!signedIn) {
    return { status: 'error', error: null, retry: () => undefined };
  }
  if (me.isPending) {
    return { status: 'loading' };
  }
  if (me.isError) {
    return { status: 'error', error: me.error, retry: () => void me.refetch() };
  }
  if (targetMemberId === null) {
    // No household yet. `RequireHousehold` (App.tsx, frozen) already redirects this case away
    // for every route that reaches this hook, so this is a defensive fallback, not a real path.
    return { status: 'loading' };
  }

  if (today.isPending) {
    return { status: 'loading' };
  }
  if (today.isError) {
    if (today.error instanceof ApiError && today.error.status === 403) {
      const known = (roster.data ?? []).find((m) => m.id === targetMemberId);
      return { status: 'not-shared', displayLabel: known?.displayLabel ?? null };
    }
    return { status: 'error', error: today.error, retry: () => void today.refetch() };
  }

  if (!isSelf && experience.isPending) {
    return { status: 'loading' };
  }
  if (!isSelf && experience.isError) {
    return { status: 'error', error: experience.error, retry: () => void experience.refetch() };
  }

  // For self, `me.data` (guaranteed by the `me.isPending`/`me.isError` checks above) is the one
  // and only source of tier/capabilities; for a member, `experience.data` is guaranteed by the
  // two `experience` checks just above.
  const tier = isSelf ? me.data.experience : (experience.data as NonNullable<typeof experience.data>).tier;
  const capabilities = isSelf
    ? me.data.capabilities
    : (experience.data as NonNullable<typeof experience.data>).capabilities;

  return {
    status: 'ready',
    memberId: targetMemberId,
    isSelf,
    displayLabel: today.data.displayLabel,
    relationship: today.data.relationship,
    visibleScopes: today.data.visibleScopes,
    timeZone: today.data.timeZone,
    tier,
    capabilities,
    isChild: tier === 'CHILD',
  };
}
