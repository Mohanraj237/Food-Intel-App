import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { insightsForMember, type InsightWindow } from '@/lib/insights';
import { toRecommendationCards } from '@/lib/recommendations';
import { RecommendationCard } from '@/design-system/RecommendationCard';
import { Skeleton } from '@/design-system/States';
import './InsightList.css';

/**
 * The "what should I do next" teaser (plan §18 4.1) — rebuilt onto `RecommendationCard` fed by
 * the new `lib/recommendations.ts` composition layer, replacing the previous raw-server-prose
 * rendering (workstreams doc's C4 checklist). The external contract (`memberId`, `window`) is
 * kept exactly as it was: this file stays at `features/today/InsightList.tsx` specifically so
 * `TodayScreen.tsx` (C1-owned, out of this workstream's reach) keeps working unchanged, per the
 * carve-out in workstreams doc §0.1 — only what's inside this file changed.
 *
 * `limit` defaults to 2, matching Today's own "teaser capped at 2 cards" spec (plan §18 1.1) —
 * `TodayScreen.tsx`'s existing `<InsightList memberId={memberId} window="WEEK" />` call site
 * gets that cap without needing to pass anything new. The full, uncapped history lives at
 * `/next/insights` (`features/insights/InsightsHistoryScreen.tsx`), which does not use this
 * component at all — it renders every window's cards directly, since a "see 2 more" teaser makes
 * no sense on the page that *is* the "see more."
 */
export function InsightList({
  memberId,
  window = 'WEEK',
  limit = 2,
  href = '/next/insights',
}: {
  memberId: string;
  window?: InsightWindow;
  limit?: number;
  /** The teaser's "see more" destination; pass `null` to render no link at all. */
  href?: string | null;
}) {
  const insights = useQuery({
    queryKey: ['insights', memberId, window],
    queryFn: () => insightsForMember(memberId, window),
  });

  if (insights.isPending) {
    return <Skeleton lines={2} label={t('loading.default')} />;
  }

  if (insights.isError) {
    // Deliberately quiet. An insight panel failing is not worth an alarming error box in the
    // middle of a page whose other sections are fine.
    return <p className="insight__unavailable">{t('insight.unavailable')}</p>;
  }

  if (insights.data.length === 0) {
    return <p className="insight__unavailable">{t('insight.none')}</p>;
  }

  const cards = toRecommendationCards(insights.data, { href: href ?? undefined }).slice(0, limit);

  return (
    <ul className="insight-feed" role="list">
      {/* `key` is destructured out rather than spread: React reserves it, and passing it
          inside a spread props object is both a warning and a no-op on the component. */}
      {cards.map(({ key, ...cardProps }, index) => (
        <li key={`${key}#${index}`}>
          <RecommendationCard {...cardProps} />
        </li>
      ))}
    </ul>
  );
}
