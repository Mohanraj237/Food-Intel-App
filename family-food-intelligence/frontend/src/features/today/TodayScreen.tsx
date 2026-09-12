import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMe } from '@/lib/useMe';
import { listObservations, type Observation } from '@/lib/wellness';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { formatAmount } from '@/lib/nutrition';
import { ErrorState, Skeleton } from '@/design-system/States';
import { QuickLog } from './QuickLog';
import { InsightList } from './InsightList';
import './TodayScreen.css';

/**
 * Individual Mode — "How am I doing today?"
 *
 * <p>Everything on this screen is computed from observations that were actually recorded.
 * There is no composite wellness score (D-040, deferred with reasons), no ring to close, and
 * no target the product invented on the user's behalf. Where nothing has been logged, the
 * screen says so rather than rendering a zero, because a zero is a measurement and "nothing
 * recorded" is not.
 *
 * <p>The ordering answers the question in the heading: what happened today, then what to do
 * about it (log something), then what it means over time (insights).
 */

/** The totals worth showing for a day, in the order a person would ask about them. */
const TODAY_METRICS: Array<{ code: string; labelKey: MessageKey }> = [
  { code: 'ENERGY_KCAL', labelKey: 'today.energy' },
  { code: 'PROTEIN_G', labelKey: 'today.protein' },
  { code: 'FIBRE_G', labelKey: 'today.fibre' },
  { code: 'SODIUM_MG', labelKey: 'today.sodium' },
  { code: 'WATER_ML', labelKey: 'today.water' },
  { code: 'STEPS', labelKey: 'today.steps' },
];

function startOfToday(): string {
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  return now.toISOString();
}

export function TodayScreen() {
  useDocumentTitleKey('today.heading');
  const signedIn = useIsSignedIn();
  const me = useMe();

  const from = startOfToday();
  const observations = useQuery({
    queryKey: ['wellness', 'observations', me.memberId, from],
    queryFn: () => listObservations(me.memberId as string, { from }),
    enabled: me.memberId !== null,
  });

  if (!signedIn) {
    return (
      <div className="page today">
        <h1>{t('today.heading')}</h1>
        <p className="today__lede">{t('today.signInPrompt')}</p>
        <Link to="/settings" className="btn btn--primary btn--md">
          {t('action.signIn')}
        </Link>
      </div>
    );
  }

  if (me.isPending) {
    return (
      <div className="page today">
        <h1>{t('today.heading')}</h1>
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  // Individual Mode is a household of one (D-035), so "no household yet" is a setup step
  // rather than a different product. Say that plainly instead of showing an empty dashboard.
  if (me.memberId === null) {
    return (
      <div className="page today">
        <h1>{t('today.heading')}</h1>
        <p className="today__lede">{t('today.needsHousehold')}</p>
        <Link to="/family" className="btn btn--primary btn--md">
          {t('household.create')}
        </Link>
      </div>
    );
  }

  const isChild = me.member?.memberType === 'CHILD';
  const totals = sumByMetric(observations.data ?? []);

  return (
    <div className="page today">
      <h1>{t('today.heading')}</h1>
      <p className="today__lede">{t('today.lede')}</p>

      {/* 1 — what has been recorded today */}
      <section className="today__section" aria-labelledby="today-totals">
        <h2 id="today-totals" className="today__h2">{t('today.totalsHeading')}</h2>

        {observations.isPending && <Skeleton lines={2} label={t('loading.default')} />}
        {observations.isError && (
          <ErrorState error={observations.error} onRetry={() => void observations.refetch()} />
        )}

        {observations.isSuccess && (
          <ul className="today__totals" role="list">
            {TODAY_METRICS.map(({ code, labelKey }) => {
              const entry = totals.get(code);
              return (
                <li key={code} className="today__total">
                  <span className="today__total-label">{t(labelKey)}</span>
                  {entry ? (
                    <span className="today__total-value">
                      {formatAmount(entry.value)} <span className="today__total-unit">{entry.unit}</span>
                    </span>
                  ) : (
                    // Not "0". A zero is a measurement; this is an absence.
                    <span className="today__total-none">{t('today.notLogged')}</span>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* 2 — the action that changes the answer above */}
      <section className="today__section" aria-labelledby="today-log">
        <h2 id="today-log" className="today__h2">{t('today.logHeading')}</h2>
        <QuickLog memberId={me.memberId} isChild={isChild} />
        <p className="today__meal-link">
          {t('today.mealPrompt')}{' '}
          <Link to="/meals/new">{t('today.logMeal')}</Link>
        </p>
      </section>

      {/* 3 — what it adds up to over time */}
      <section className="today__section" aria-labelledby="today-insights">
        <h2 id="today-insights" className="today__h2">{t('today.insightsHeading')}</h2>
        <InsightList memberId={me.memberId} window="WEEK" />
      </section>

      {me.needsProfile && (
        <p className="today__profile-note">
          {t('today.profileNote')} <Link to="/family">{t('today.profileLink')}</Link>
        </p>
      )}
    </div>
  );
}

/**
 * Day totals per metric.
 *
 * Sums rather than takes the latest, because a day's water is several logs and a day's steps
 * may be too. A metric absent from the map means nothing was recorded — the caller must not
 * substitute zero.
 */
function sumByMetric(observations: Observation[]): Map<string, { value: number; unit: string }> {
  const totals = new Map<string, { value: number; unit: string }>();
  for (const o of observations) {
    const existing = totals.get(o.metricCode);
    totals.set(o.metricCode, {
      value: (existing?.value ?? 0) + o.value,
      unit: o.unit,
    });
  }
  return totals;
}
