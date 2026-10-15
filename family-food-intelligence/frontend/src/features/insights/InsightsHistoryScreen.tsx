import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { insightsForMember, type InsightWindow } from '@/lib/insights';
import { toRecommendationCards } from '@/lib/recommendations';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { RecommendationCard } from '@/design-system/RecommendationCard';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import './InsightsHistory.css';

/**
 * `/insights` — every noticing about this person's own records, by window. The same cards
 * Today's teaser shows, uncapped.
 */
const WINDOWS: readonly InsightWindow[] = ['TODAY', 'WEEK', 'MONTH'];

const WINDOW_LABEL_KEYS: Record<InsightWindow, MessageKey> = {
  TODAY: 'insight.window.TODAY',
  WEEK: 'insight.window.WEEK',
  MONTH: 'insight.window.MONTH',
};

export function InsightsHistoryScreen() {
  useDocumentTitleKey('insight.historyHeading');
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });
  const memberId = me.data?.member?.id ?? null;

  if (!signedIn) {
    return (
      <div className="page layout-list insights-history">
        <PageHeader title={t('insight.historyHeading')} lede={t('goal.signInPrompt')} />
        <div>
          <Link to="/you" className="btn btn--primary btn--md">
            {t('action.signIn')}
          </Link>
        </div>
      </div>
    );
  }

  if (me.isPending) {
    return (
      <div className="page layout-list insights-history">
        <PageHeader title={t('insight.historyHeading')} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (me.isError) {
    return (
      <div className="page layout-list insights-history">
        <PageHeader title={t('insight.historyHeading')} />
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      </div>
    );
  }

  if (memberId === null) {
    return (
      <div className="page layout-list insights-history">
        <PageHeader title={t('insight.historyHeading')} lede={t('today.needsHousehold')} />
      </div>
    );
  }

  return (
    <div className="page layout-list insights-history">
      <PageHeader title={t('insight.historyHeading')} lede={t('insight.historyLede')} backTo="/today" backLabel={t('nav.today')} />

      {WINDOWS.map((window) => (
        <WindowSection key={window} memberId={memberId} window={window} />
      ))}

      <p className="insights-history__note">{t('insight.historyLongTermNote')}</p>
    </div>
  );
}

function WindowSection({ memberId, window }: { memberId: string; window: InsightWindow }) {
  const insights = useQuery({
    queryKey: ['insights', memberId, window],
    queryFn: () => insightsForMember(memberId, window),
  });

  return (
    <section className="insights-history__section" aria-labelledby={`insights-history-${window}`}>
      <h2 id={`insights-history-${window}`} className="insights-history__h2">
        {t(WINDOW_LABEL_KEYS[window])}
      </h2>

      {insights.isPending && <Skeleton lines={2} label={t('loading.default')} />}
      {insights.isError && <ErrorState error={insights.error} onRetry={() => void insights.refetch()} />}
      {insights.isSuccess && insights.data.length === 0 && (
        <EmptyState compact icon="insight" title={t('insight.none')} body={t('insight.noneBody')} />
      )}
      {insights.isSuccess && insights.data.length > 0 && (
        <ul className="insight-feed" role="list">
          {toRecommendationCards(insights.data).map(({ key, ...cardProps }, index) => (
            <li key={`${key}#${index}`}>
              <RecommendationCard {...cardProps} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
