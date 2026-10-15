import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { goalProgress } from '@/lib/goals';
import { listMetricDefinitions, metricHistory, type MetricCategory, type Observation } from '@/lib/wellness';
import { formatAmount } from '@/lib/nutrition';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { useLogSheet, type LogKind } from '@/design-system/LogSheet';
import { Button } from '@/design-system/Button';
import { Chip } from '@/design-system/Rails';
import { HistoryChart, type HistoryPoint } from '@/design-system/HistoryChart';
import { ErrorState, Skeleton } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import { dayInZone, isoDaysBefore, lastDaysInZone, shortDayLabel, todayInZone } from '@/features/goals/calendarDay';
import { metricLabelKey } from '@/features/goals/goalOptions';
import { metricName } from '@/lib/metricDisplay';
import { useTrackedSubject } from '@/features/goals/useTrackedSubject';
import './Trackers.css';

/**
 * `/trackers/:category` and, via an optional `:memberId` route param,
 * `/family/:memberId/trackers/:category` (plan §18 1.4) — the trend behind one of Today's tiles.
 * `:category` is a metric *code* (`WATER_ML`, and so on), matching the tile links this screen is
 * reached from; the route segment name comes from the plan's own sitemap wording, not from this
 * screen treating it as a `MetricCategory`.
 */

const RANGE_DAYS = [7, 30, 90] as const;
type RangeDays = (typeof RANGE_DAYS)[number];

/** SUM metrics draw bars (a day's total); LATEST metrics draw dots (a reading). The server does
 *  not carry this on `MetricDefinition` today — only `GoalProgress.aggregation` does, and only
 *  for a metric with an active goal — so this is a presentation classification by category,
 *  documented as such rather than guessed per metric. Flagged in this workstream's summary as a
 *  candidate for a real `aggregation` field on `MetricDefinition` so no client has to do this. */
function aggregationForCategory(category: MetricCategory): 'SUM' | 'LATEST' {
  return category === 'BODY' || category === 'CLINICAL' ? 'LATEST' : 'SUM';
}

const CATEGORY_TO_LOG_KIND: Partial<Record<MetricCategory, LogKind>> = {
  HYDRATION: 'water',
  ACTIVITY: 'activity',
  SLEEP: 'sleep',
  WELLBEING: 'mood',
  BODY: 'body',
  CLINICAL: 'readings',
};

export function TrackerHistoryScreen() {
  const { memberId: routeMemberId, category: metricCode } = useParams<{ memberId?: string; category: string }>();
  const signedIn = useIsSignedIn();
  const subject = useTrackedSubject(routeMemberId);
  const { open: openLogSheet } = useLogSheet();
  const [range, setRange] = useState<RangeDays>(7);

  const definitions = useQuery({
    queryKey: ['wellness', 'metrics'],
    queryFn: listMetricDefinitions,
    staleTime: 60 * 60 * 1000,
  });
  const definition = definitions.data?.find((d) => d.code === metricCode);
  const labelKey = metricCode ? metricLabelKey(metricCode) : null;
  const metricLabel = labelKey ? t(labelKey) : metricCode ? metricName(metricCode) : '';

  useDocumentTitle(metricLabel);

  if (!signedIn) {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <p className="trackerpage__note">{t('goal.signInPrompt')}</p>
      </div>
    );
  }

  if (subject.status === 'loading' || definitions.isPending) {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (subject.status === 'error') {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <ErrorState error={subject.error} onRetry={subject.retry} />
      </div>
    );
  }
  if (definitions.isError) {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <ErrorState error={definitions.error} onRetry={() => void definitions.refetch()} />
      </div>
    );
  }

  if (subject.status === 'not-shared') {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <p className="trackerpage__note">
          {subject.displayLabel
            ? t('tracker.notSharedNamed' as MessageKey, { name: subject.displayLabel })
            : t('tracker.notShared' as MessageKey)}
        </p>
        <Link to="/family" className="btn btn--secondary btn--md">{t('nav.family')}</Link>
      </div>
    );
  }

  if (!definition || !metricCode) {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <p className="trackerpage__note">{t('tracker.unknownMetric' as MessageKey)}</p>
      </div>
    );
  }

  if (!subject.visibleScopes.includes(definition.category)) {
    return (
      <div className="page trackerpage">
        <PageHeader title={metricLabel} />
        <p className="trackerpage__note">
          {t('tracker.notSharedNamed' as MessageKey, { name: subject.displayLabel })}
        </p>
        <Link to="/family" className="btn btn--secondary btn--md">{t('nav.family')}</Link>
      </div>
    );
  }

  const logKind = CATEGORY_TO_LOG_KIND[definition.category];

  return (
    <div className="page trackerpage">
      <PageHeader
        title={metricLabel}
        {...(!subject.isSelf ? { lede: t('tracker.forMember' as MessageKey, { name: subject.displayLabel }) } : {})}
        backTo={subject.isSelf ? '/today' : `/family/${subject.memberId}`}
        backLabel={subject.isSelf ? t('nav.today') : subject.displayLabel}
      />

      <div className="trackerpage__range" role="group" aria-label={t('tracker.rangeLabel' as MessageKey)}>
        {RANGE_DAYS.map((d) => (
          <Chip key={d} active={d === range} onClick={() => setRange(d)}>
            {t('tracker.rangeDays' as MessageKey, { days: d })}
          </Chip>
        ))}
      </div>

      <TrackerChart
        memberId={subject.memberId}
        isSelf={subject.isSelf}
        timeZone={subject.timeZone}
        metricCode={metricCode}
        unit={definition.unit}
        category={definition.category}
        metricLabel={metricLabel}
        range={range}
      />

      {subject.isSelf && (
        <div className="trackerpage__cta">
          {logKind ? (
            <Button variant="secondary" size="sm" onClick={() => openLogSheet({ kind: logKind })}>
              {t('tracker.logAction' as MessageKey, { metric: metricLabel })}
            </Button>
          ) : (
            <Link to="/meals/new" className="btn btn--secondary btn--sm">
              {t('meal.heading')}
            </Link>
          )}
        </div>
      )}
    </div>
  );
}

function TrackerChart({
  memberId,
  isSelf,
  timeZone,
  metricCode,
  unit,
  category,
  metricLabel,
  range,
}: {
  memberId: string;
  isSelf: boolean;
  timeZone: string;
  metricCode: string;
  unit: string;
  category: MetricCategory;
  metricLabel: string;
  range: RangeDays;
}) {
  const today = useMemo(() => todayInZone(timeZone), [timeZone]);
  const from = isoDaysBefore(today, range - 1);

  const history = useQuery({
    queryKey: ['wellness', 'history', memberId, metricCode, from, today],
    queryFn: () => metricHistory(memberId, metricCode, { from, to: today }),
  });

  const goal = useQuery({
    queryKey: ['goals', 'progress', memberId],
    queryFn: () => goalProgress(memberId),
    enabled: isSelf,
  });

  if (history.isPending) {
    return <Skeleton lines={3} label={t('loading.default')} />;
  }
  if (history.isError) {
    return <ErrorState error={history.error} onRetry={() => void history.refetch()} />;
  }

  const aggregation = aggregationForCategory(category);
  const days = lastDaysInZone(today, range);
  const points = buildPoints(days, history.data, aggregation, timeZone);
  const loggedDays = points.filter((p) => p.value !== null).length;

  const activeGoal = goal.data?.find((g) => g.metricCode === metricCode && g.aggregation === 'SUM');
  const goalValue = aggregation === 'SUM' ? activeGoal?.target ?? null : null;

  const summary =
    loggedDays === 0
      ? t('tracker.chartSummaryEmpty' as MessageKey, { metric: metricLabel, days: range })
      : aggregation === 'LATEST'
        ? t('tracker.chartSummaryLatest' as MessageKey, {
            metric: metricLabel,
            value: formatAmount(lastValue(points) ?? 0),
            unit,
          })
        : t('tracker.chartSummarySum' as MessageKey, { metric: metricLabel, logged: loggedDays, days: range });

  return (
    <HistoryChart
      points={points}
      unit={unit}
      aggregation={aggregation}
      goalValue={goalValue}
      title={t('tracker.chartTitle' as MessageKey, { metric: metricLabel })}
      summary={summary}
      emptyText={t('tracker.emptyRange' as MessageKey, { metric: metricLabel, days: range })}
    />
  );
}

/** One point per day, oldest first — a `SUM` day totals its observations; a `LATEST` day keeps
 *  the last one recorded. A day with nothing is `null`, never a zero (plan §18 1.4). */
function buildPoints(
  days: readonly string[],
  observations: readonly Observation[],
  aggregation: 'SUM' | 'LATEST',
  timeZone: string,
): HistoryPoint[] {
  const byDay = new Map<string, Observation[]>();
  for (const obs of observations) {
    const day = dayInZone(obs.effectiveAt, timeZone);
    const list = byDay.get(day) ?? [];
    list.push(obs);
    byDay.set(day, list);
  }

  return days.map((date) => {
    const dayObs = byDay.get(date);
    let value: number | null = null;
    if (dayObs && dayObs.length > 0) {
      if (aggregation === 'SUM') {
        value = dayObs.reduce((sum, o) => sum + o.value, 0);
      } else {
        value = [...dayObs].sort((a, b) => a.recordedAt.localeCompare(b.recordedAt)).at(-1)?.value ?? null;
      }
    }
    return { date, value, label: shortDayLabel(date) };
  });
}

function lastValue(points: readonly HistoryPoint[]): number | null {
  for (let i = points.length - 1; i >= 0; i -= 1) {
    if (points[i]?.value !== null) return points[i]?.value ?? null;
  }
  return null;
}
