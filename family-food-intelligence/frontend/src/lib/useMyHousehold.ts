import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { listMyHouseholds, type HouseholdSummary } from './household';

/** The query key `useMyHousehold` reads and writes — invalidate this after any
 *  mutation that changes the caller's membership (create, join, leave, be removed). */
export const MY_HOUSEHOLDS_QUERY_KEY = ['households', 'mine'] as const;

/**
 * `householdId`:
 *
 * A person almost always belongs to zero or one household. The rare case of
 * more than one has no "switch household" UI, so this deterministically takes
 * the first (oldest, per the backend's ordering) entry rather than building a
 * switcher. `null` while the list is still loading or on error, same as when
 * it resolves empty — callers that need to tell "still loading" apart from
 * "no household" should check `isPending`/`isError` directly rather than
 * relying on `householdId` alone.
 */
export type UseMyHousehold = UseQueryResult<HouseholdSummary[]> & { householdId: string | null };

/**
 * The one place that decides "which household is *the* household" for the
 * signed-in caller, backed by `GET /households` (`listMyHouseholds`). Replaces
 * the old `localStorage`-cached household id — every screen that needs "my
 * household" reads it from here instead of re-deriving it.
 *
 * `enabled` defaults to `true` — pass `false` (e.g. `!useIsSignedIn()`'s
 * negation) for a screen reachable while signed out, so it never fires this
 * authenticated request without a session. Callers gated by an already
 * signed-in parent (`ConsentPanel`, `ProductScreen`'s `FitSection`) don't need it.
 */
export function useMyHousehold(options: { enabled?: boolean } = {}): UseMyHousehold {
  const { enabled = true } = options;
  const query = useQuery({
    queryKey: MY_HOUSEHOLDS_QUERY_KEY,
    queryFn: listMyHouseholds,
    enabled,
  });

  return {
    ...query,
    householdId: query.data?.[0]?.householdId ?? null,
  };
}
