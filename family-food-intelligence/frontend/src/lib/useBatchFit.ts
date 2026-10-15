import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getBatchFit, type PersonalFit } from './scoring';
import { useIsSignedIn } from './useSession';
import { useMyHousehold } from './useMyHousehold';
import { useMeQuery } from './me';
import type { Verdict } from '@/design-system/VerdictChip';

const REAL_VERDICTS = new Set(['EVERYDAY', 'OCCASIONAL', 'LIMIT', 'DOESNT_FIT']);
const MAX_PER_REQUEST = 50;

/** What a result card can show about the viewer: their own verdict, and whether anyone they may see is flagged. */
export interface CardFit {
  /** The viewer's own verdict — only a real one; "not enough data" is not put on a card. */
  verdict: Verdict | null;
  /** A declared allergen matches the viewer or a child they guard. */
  allergenFlagged: boolean;
}

/**
 * Household fit for a page of cards in one request per 50 products (D-061). Signed-out, or
 * without a household, it returns an empty map and the cards fall back to their nutrient line.
 * Held only in the query client's memory for this session; the server never caches it (D-056).
 */
export function useBatchFit(productIds: string[]): Map<string, CardFit> {
  const signedIn = useIsSignedIn();
  const household = useMyHousehold({ enabled: signedIn });
  const me = useMeQuery({ enabled: signedIn });
  const householdId = household.householdId;
  const myMemberId = me.data?.member?.id ?? null;
  const ids = useMemo(() => [...new Set(productIds)].sort(), [productIds]);

  const query = useQuery({
    queryKey: ['batch-fit', householdId, ids],
    enabled: signedIn && householdId !== null && ids.length > 0,
    staleTime: 60 * 1000,
    queryFn: async () => {
      const chunks: string[][] = [];
      for (let i = 0; i < ids.length; i += MAX_PER_REQUEST) chunks.push(ids.slice(i, i + MAX_PER_REQUEST));
      const results = await Promise.all(chunks.map((chunk) => getBatchFit(householdId as string, chunk)));
      return results.flatMap((r) => r.items);
    },
  });

  return useMemo(() => {
    const map = new Map<string, CardFit>();
    for (const item of query.data ?? []) {
      const own: PersonalFit | undefined = item.members.find((m) => m.memberId === myMemberId);
      const verdict =
        own && own.status === 'ASSESSED' && own.verdict && REAL_VERDICTS.has(own.verdict) ? (own.verdict as Verdict) : null;
      map.set(item.productId, {
        verdict,
        allergenFlagged: item.members.some((m) => m.allergenMatches.length > 0),
      });
    }
    return map;
  }, [query.data, myMemberId]);
}
