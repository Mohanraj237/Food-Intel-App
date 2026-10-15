import { useMemo } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { ApiError } from '@/lib/api';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { getProfile, type AllergenCode, type GoalCode, type Profile } from '@/lib/profile';
import { listGrants, listPolicies, type ConsentPolicy } from '@/lib/consent';

/**
 * Everything "how is my household set up?" needs, in one place.
 *
 * This aggregates only endpoints that already exist — members, per-member
 * profiles, the consent ledger — and derives the rest. Nothing here invents a
 * metric. Health records (meals, activity, body measurements) are deliberately
 * not read here; `FamilyWellness` shows them, per member, only where the viewer
 * is permitted to see them (D-038).
 *
 * The question it answers is the one this product can answer honestly: *can we
 * give each person a real result yet, and what is each of them watching?*
 */

/** Why a member cannot receive a personal result yet. */
export type BlockerKind = 'NO_PROFILE' | 'CONSENT_PENDING' | 'CONSENT_OFF' | 'PRIVATE';

export interface MemberReadiness {
  member: HouseholdMember;
  /** `null` when we are not permitted to read it (another adult's own data). */
  profile: Profile | null;
  /** `true` once this member would receive a personal result. */
  ready: boolean;
  blocker: BlockerKind | null;
  /** `true` when the server refused the read — their data, not ours to show. */
  isPrivate: boolean;
}

export interface HouseholdOverview {
  isPending: boolean;
  isError: boolean;
  members: MemberReadiness[];
  readyCount: number;
  totalCount: number;
  /** Goals across the household, most-shared first. Excludes private members. */
  sharedGoals: Array<{ code: GoalCode; count: number }>;
  /** The union of every declared allergen — the household shopping constraint. */
  householdAllergens: AllergenCode[];
  /** Members with nothing recorded yet, so the roster can lead with them. */
  needsAttention: MemberReadiness[];
}

/** Purposes a guardian must grant for a child, per the policy flags (not hardcoded). */
function childPurposeCodes(policies: ConsentPolicy[]): string[] {
  return policies.filter((p) => !p.essential && p.requiresVerifiable).map((p) => p.purposeCode);
}

/** The purpose an adult grants for themselves to receive a personal result. */
function selfPurposeCodes(policies: ConsentPolicy[]): string[] {
  return policies
    .filter((p) => !p.essential && !p.requiresVerifiable && p.purposeCode === 'PERSONAL_FIT')
    .map((p) => p.purposeCode);
}

export function useHouseholdOverview(householdId: string): HouseholdOverview {
  const membersQuery = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId),
  });

  const policiesQuery = useQuery({
    queryKey: ['consent', 'policies'],
    queryFn: listPolicies,
    staleTime: 30 * 60 * 1000,
  });

  const members = membersQuery.data ?? [];

  // A 403 here is a correct answer, not a failure: an adult's profile is theirs
  // alone (D-002). `retry: false` stops us hammering an endpoint that is
  // deliberately refusing, and the refusal becomes a "Private" state below.
  const profileQueries = useQueries({
    queries: members.map((member) => ({
      queryKey: ['profile', householdId, member.id] as const,
      queryFn: () => getProfile(householdId, member.id),
      retry: false,
    })),
  });

  const grantQueries = useQueries({
    queries: members.map((member) => ({
      queryKey: ['consent', 'grants', member.id] as const,
      queryFn: () => listGrants(member.id),
      retry: false,
    })),
  });

  // Flattened before the memo on purpose: `useQueries` returns a fresh array of
  // fresh objects every render, so depending on it directly would recompute
  // always, and stringifying its statuses to dodge that is the kind of trick
  // that quietly rots. These are plain values with stable identity.
  const profileStates = profileQueries.map((q) => ({
    data: q.data ?? null,
    status: q.error instanceof ApiError ? q.error.status : null,
    loading: q.isLoading,
  }));
  const grantStates = grantQueries.map((q) => ({
    data: q.data ?? null,
    loading: q.isLoading,
  }));
  const profileKey = profileStates.map((s) => `${s.data?.id ?? ''}:${s.status}:${s.loading}`).join('|');
  const grantKey = grantStates
    .map((s) => `${(s.data ?? []).map((g) => `${g.purposeCode}${g.status}`).join('')}:${s.loading}`)
    .join('|');

  return useMemo<HouseholdOverview>(() => {
    const policies = policiesQuery.data ?? [];
    const childPurposes = childPurposeCodes(policies);
    const selfPurposes = selfPurposeCodes(policies);

    const readiness: MemberReadiness[] = members.map((member, index) => {
      const profileState = profileStates[index];
      const grantState = grantStates[index];

      // A 403 is a correct answer, not a failure: an adult's profile is theirs
      // alone (D-002), so it becomes a "Private" state rather than an error.
      if (profileState?.status === 403) {
        return { member, profile: null, ready: false, blocker: 'PRIVATE', isPrivate: true };
      }

      const missing = profileState?.status === 404;
      const profile = profileState?.data ?? null;
      const activeGrants = new Set(
        (grantState?.data ?? []).filter((g) => g.status === 'ACTIVE').map((g) => g.purposeCode),
      );

      const isChild = member.memberType === 'CHILD';
      const required = isChild ? childPurposes : selfPurposes;
      const consentGranted =
        required.length > 0 && required.every((code) => activeGrants.has(code));

      // Consent first: it is the gate that has to clear before a profile may
      // even be recorded for a child, so reporting "no profile" ahead of it
      // would send a guardian to the wrong screen.
      let blocker: BlockerKind | null = null;
      if (!consentGranted) {
        blocker = (grantState?.data ?? []).some((g) => g.status === 'WITHDRAWN')
          ? 'CONSENT_OFF'
          : 'CONSENT_PENDING';
      } else if (profile === null || missing) {
        blocker = 'NO_PROFILE';
      }

      return { member, profile, ready: blocker === null, blocker, isPrivate: false };
    });

    const goalCounts = new Map<GoalCode, number>();
    const allergens = new Set<AllergenCode>();
    for (const entry of readiness) {
      if (!entry.profile) continue;
      for (const goal of entry.profile.goals) {
        goalCounts.set(goal, (goalCounts.get(goal) ?? 0) + 1);
      }
      for (const allergen of entry.profile.allergens) allergens.add(allergen);
    }

    const sharedGoals = Array.from(goalCounts.entries())
      .map(([code, count]) => ({ code, count }))
      .sort((a, b) => b.count - a.count || a.code.localeCompare(b.code));

    return {
      // Profile and grant reads settle after members; treat the whole thing as
      // pending until they have, so the summary never renders a half-count.
      isPending:
        membersQuery.isPending ||
        policiesQuery.isPending ||
        profileStates.some((s) => s.loading) ||
        grantStates.some((s) => s.loading),
      isError: membersQuery.isError || policiesQuery.isError,
      members: readiness,
      readyCount: readiness.filter((r) => r.ready).length,
      totalCount: readiness.length,
      sharedGoals,
      householdAllergens: Array.from(allergens).sort(),
      needsAttention: readiness.filter((r) => !r.ready && !r.isPrivate),
    };
    // `profileKey`/`grantKey` stand in for the flattened arrays above, which are
    // new objects each render; they change exactly when the underlying data does.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    members,
    policiesQuery.data,
    membersQuery.isPending,
    membersQuery.isError,
    policiesQuery.isPending,
    policiesQuery.isError,
    profileKey,
    grantKey,
  ]);
}
