import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, getLocale, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery, ME_QUERY_KEY } from '@/lib/me';
import { listPolicies, grant as grantConsent } from '@/lib/consent';
import { dateKeyInZone, previousDateKey } from '@/lib/access';
import { DASHBOARD_QUERY_KEYS, useTodaySummary, metricDayState, type MetricDay } from '@/lib/dashboard';
import { listMemberMeals, type Meal, type MealType } from '@/lib/nutritionLog';
import type { MetricCategory } from '@/lib/wellness';
import { formatAmount } from '@/lib/nutrition';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { useLogSheet, type LogKind } from '@/design-system/LogSheet';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { Button } from '@/design-system/Button';
import { ProgressTile } from '@/design-system/ProgressTile';
import { ConsentMoment } from '@/design-system/ConsentMoment';
import { RecommendationCard } from '@/design-system/RecommendationCard';
import { PageHeader } from '@/design-system/PageHeader';
import { Icon, type IconName } from '@/design-system/Icon';
import { DayNavigator } from './DayNavigator';
import { GoalHabitSummary } from './GoalHabitSummary';
import { dayInstantRange, nextDateKey } from './timezone';
import { InsightList } from './InsightList';
import './TodayScreen.css';

/**
 * "How am I doing today?" — one `GET /members/{id}/today` read; every number traces to a
 * server field, never a client sum, and never the device clock's idea of a day.
 */

const DATE_KEY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

interface TileSpec {
  id: string;
  code: string;
  category: MetricCategory;
  labelKey: MessageKey;
  historyHref: string;
  logKind: LogKind | null;
  icon: IconName;
}

const TILES: readonly TileSpec[] = [
  { id: 'meal', code: 'ENERGY_KCAL', category: 'NUTRITION', labelKey: 'today.mealsLabel', historyHref: '/meals', logKind: null, icon: 'meal' },
  { id: 'water', code: 'WATER_ML', category: 'HYDRATION', labelKey: 'today.water', historyHref: '/trackers/WATER_ML', logKind: 'water', icon: 'water' },
  { id: 'activity', code: 'STEPS', category: 'ACTIVITY', labelKey: 'today.activityLabel', historyHref: '/trackers/STEPS', logKind: 'activity', icon: 'steps' },
  { id: 'sleep', code: 'SLEEP_MINUTES', category: 'SLEEP', labelKey: 'today.sleepLabel', historyHref: '/trackers/SLEEP_MINUTES', logKind: 'sleep', icon: 'sleep' },
];

const MEAL_TYPE_KEYS: Record<MealType, MessageKey> = {
  BREAKFAST: 'meal.type.BREAKFAST',
  LUNCH: 'meal.type.LUNCH',
  DINNER: 'meal.type.DINNER',
  SNACK: 'meal.type.SNACK',
};

export function TodayScreen() {
  useDocumentTitleKey('today.heading');
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const online = useOnlineStatus();
  const [offlineNotice, setOfflineNotice] = useState<string | null>(null);

  if (!signedIn) {
    return (
      <div className="page today">
        <PageHeader title={t('today.heading')} lede={t('today.signInPrompt')} />
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
      <div className="page today">
        <PageHeader title={t('today.heading')} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (me.isError) {
    return (
      <div className="page today">
        <PageHeader title={t('today.heading')} />
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      </div>
    );
  }

  if (!me.data.household || !me.data.member) {
    return (
      <div className="page today">
        <PageHeader title={t('today.heading')} lede={t('today.needsHousehold')} />
        <div>
          <Link to="/welcome" className="btn btn--primary btn--md">
            {t('household.create')}
          </Link>
        </div>
      </div>
    );
  }

  const memberId = me.data.member.id;
  const timeZone = me.data.household.timeZone;
  const todayKey = dateKeyInZone(new Date(), timeZone);
  const requestedDate = searchParams.get('date');
  const activeDateKey = requestedDate && DATE_KEY_PATTERN.test(requestedDate) ? requestedDate : todayKey;

  function goToDate(next: string) {
    const params = new URLSearchParams(searchParams);
    if (next === todayKey) params.delete('date');
    else params.set('date', next);
    setSearchParams(params, { replace: true });
  }

  return (
    <TodayBody
      memberId={memberId}
      displayName={me.data.member.displayLabel}
      timeZone={timeZone}
      todayKey={todayKey}
      activeDateKey={activeDateKey}
      canTrack={me.data.capabilities.canTrack}
      largerTextSuggested={me.data.capabilities.largerTextSuggested}
      trackingPurposeCode={me.data.trackingConsent.purposeCode}
      online={online}
      offlineNotice={offlineNotice}
      onOfflineAttempt={() => setOfflineNotice(t('today.offlineLogReason'))}
      onPrevious={() => goToDate(previousDateKey(activeDateKey))}
      onNext={() => goToDate(nextDateKey(activeDateKey))}
      onConsentGranted={() => {
        void queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
        void queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEYS.all });
      }}
    />
  );
}

function TodayBody({
  memberId,
  displayName,
  timeZone,
  todayKey,
  activeDateKey,
  canTrack,
  largerTextSuggested,
  trackingPurposeCode,
  online,
  offlineNotice,
  onOfflineAttempt,
  onPrevious,
  onNext,
  onConsentGranted,
}: {
  memberId: string;
  displayName: string;
  timeZone: string;
  todayKey: string;
  activeDateKey: string;
  canTrack: boolean;
  largerTextSuggested: boolean;
  trackingPurposeCode: string;
  online: boolean;
  offlineNotice: string | null;
  onOfflineAttempt: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onConsentGranted: () => void;
}) {
  const logSheet = useLogSheet();
  const navigate = useNavigate();
  const dayRange = dayInstantRange(activeDateKey, timeZone);
  const viewingToday = activeDateKey === todayKey;

  const today = useTodaySummary(memberId, activeDateKey);
  const meals = useQuery({
    queryKey: ['meals', 'member', memberId, dayRange.from, dayRange.to],
    queryFn: () => listMemberMeals(memberId, dayRange),
  });

  function onTileLog(kind: LogKind | null) {
    if (!online) {
      onOfflineAttempt();
      return;
    }
    if (kind === null) {
      navigate('/meals/new');
      return;
    }
    logSheet.open({ kind, subjectId: memberId });
  }

  return (
    <div className="page today">
      <PageHeader
        eyebrow={viewingToday ? t('today.eyebrowToday', { name: displayName }) : t('today.eyebrowPast', { name: displayName })}
        title={t('today.heading')}
        actions={<DayNavigator dateKey={activeDateKey} todayKey={todayKey} onPrevious={onPrevious} onNext={onNext} />}
      />

      {!online && (
        <p className="today__offline-banner" role="status">
          <Icon name="alert" size={16} />
          {t('today.offlineBanner')}
        </p>
      )}
      {offlineNotice && (
        <p className="today__offline-banner" role="status">
          <Icon name="alert" size={16} />
          {offlineNotice}
        </p>
      )}

      {today.isPending && (
        <ul className="today__tiles" role="list">
          {TILES.map((tile) => (
            <ProgressTile
              key={tile.id}
              metricLabel={t(tile.labelKey)}
              historyHref={tile.historyHref}
              onLog={() => undefined}
              state="loading"
            />
          ))}
        </ul>
      )}

      {today.isError && <ErrorState error={today.error} onRetry={() => void today.refetch()} />}

      {today.isSuccess && (
        <>
          {today.data.mealsAwaitingConfirmation > 0 && (
            <RecommendationCard
              whatHappened={
                today.data.mealsAwaitingConfirmation === 1
                  ? t('today.mealsAwaitingOne')
                  : t('today.mealsAwaitingMany', { count: today.data.mealsAwaitingConfirmation })
              }
              href="/meals?filter=awaiting"
              linkLabel={t('today.reviewMeals')}
            />
          )}

          <section className="today__section" aria-labelledby="today-tiles">
            <h2 id="today-tiles" className="sr-only">
              {t('today.totalsHeading')}
            </h2>

            {!canTrack ? (
              <EmptyState
                icon="user"
                title={t('today.profileNeededTitle')}
                action={
                  <Link to="/you/profile" className="btn btn--primary btn--sm">
                    {t('today.profileNeededAction')}
                  </Link>
                }
              />
            ) : today.data.trackingConsentActive === false ? (
              <TrackingConsentMoment memberId={memberId} purposeCode={trackingPurposeCode} onGranted={onConsentGranted} />
            ) : (
              <ul className="today__tiles" role="list">
                {TILES.map((tile) => (
                  <TileEntry key={tile.id} tile={tile} day={today.data} onLog={() => onTileLog(tile.logKind)} />
                ))}
              </ul>
            )}
          </section>

          <section className="today__section" aria-labelledby="today-meals">
            <div className="today__section-head">
              <h2 id="today-meals" className="today__h2">
                {t('today.mealsHeading')}
              </h2>
              <Link to="/meals/new" className="today__section-action">
                <Icon name="plus" size={16} />
                {t('today.logMeal')}
              </Link>
            </div>
            {meals.isPending && <Skeleton lines={2} label={t('loading.default')} />}
            {meals.isError && <ErrorState error={meals.error} onRetry={() => void meals.refetch()} />}
            {meals.isSuccess && meals.data.length === 0 && (
              <EmptyState
                compact
                icon="meal"
                title={t('today.noMeals')}
                action={
                  <Link to="/meals/new" className="btn btn--secondary btn--sm">
                    {t('today.logMeal')}
                  </Link>
                }
              />
            )}
            {meals.isSuccess && meals.data.length > 0 && (
              <ul className="today__meals" role="list">
                {sortByTime(meals.data).map((meal) => (
                  <MealRow key={meal.id} meal={meal} memberId={memberId} />
                ))}
              </ul>
            )}
          </section>

          <section className="today__section" aria-labelledby="today-goals">
            <div className="today__section-head">
              <h2 id="today-goals" className="today__h2">
                {t('goal.manage')}
              </h2>
              <Link to="/goals" className="today__section-action">
                {t('goal.seeAll')}
                <Icon name="chevron-right" size={16} />
              </Link>
            </div>
            <div className="today__card">
              <GoalHabitSummary goals={today.data.goals} habits={today.data.habits} goalsHref="/goals" habitsHref="/habits" />
            </div>
          </section>

          <section className="today__section" aria-labelledby="today-insights">
            <div className="today__section-head">
              <h2 id="today-insights" className="today__h2">
                {t('today.insightsHeading')}
              </h2>
              <Link to="/insights" className="today__section-action">
                {t('insight.seeAll')}
                <Icon name="chevron-right" size={16} />
              </Link>
            </div>
            <InsightList memberId={memberId} window="WEEK" href="/insights" />
          </section>

          {largerTextSuggested && (
            <p className="today__profile-note">
              {t('settings.textSizeSuggested')} <Link to="/you/appearance">{t('you.appearance')}</Link>
            </p>
          )}

          <p className="today__honesty">
            <Icon name="info" size={14} />
            {t('today.lede')}
          </p>
        </>
      )}
    </div>
  );
}

function TileEntry({
  tile,
  day,
  onLog,
}: {
  tile: TileSpec;
  day: {
    metrics: readonly MetricDay[];
    visibleScopes: readonly string[];
    goals: readonly { metricCode: string; target: number; unit: string; direction: string }[];
  };
  onLog: () => void;
}) {
  const state = metricDayState(day, tile.code, tile.category);
  const goal = day.goals.find((g) => g.metricCode === tile.code);
  const logLabelProp = tile.id === 'meal' ? { logLabel: t('today.logMealAction') } : {};

  if (state.state !== 'LOGGED') {
    return (
      <ProgressTile
        metricLabel={t(tile.labelKey)}
        historyHref={tile.historyHref}
        state="not-logged"
        onLog={onLog}
        icon={tile.icon}
        {...(goal ? { subLine: t('today.goalHint', { target: formatMetricValue(tile.code, goal.target, goal.unit) }) } : {})}
        {...logLabelProp}
      />
    );
  }

  const actualText = formatMetricValue(tile.code, state.value, state.unit);
  const valueText = actualText;
  const subLine = goal
    ? t('today.tileOfGoal', { target: formatMetricValue(tile.code, goal.target, goal.unit) })
    : undefined;
  const progress = goal && goal.direction === 'AT_LEAST' && goal.target > 0 ? state.value / goal.target : null;

  return (
    <ProgressTile
      metricLabel={t(tile.labelKey)}
      historyHref={tile.historyHref}
      state="logged"
      valueText={valueText}
      onLog={onLog}
      icon={tile.icon}
      progress={progress}
      {...(subLine ? { subLine } : {})}
      {...logLabelProp}
    />
  );
}

/** Sleep reads as "7h 20m", water above a litre as litres; every other tile keeps its unit. */
function formatMetricValue(code: string, value: number, unit: string): string {
  if (code === 'SLEEP_MINUTES') {
    const hours = Math.floor(value / 60);
    const minutes = Math.round(value % 60);
    return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
  }
  if (unit === 'ml' && value >= 1000) {
    return t('unit.litres', { value: formatAmount(Number((value / 1000).toFixed(2))) });
  }
  return `${formatAmount(value)} ${unit}`;
}

function TrackingConsentMoment({
  memberId,
  purposeCode,
  onGranted,
}: {
  memberId: string;
  purposeCode: string;
  onGranted: () => void;
}) {
  const policies = useQuery({
    queryKey: ['consent', 'policies'],
    queryFn: listPolicies,
    staleTime: 30 * 60 * 1000,
  });
  const policy = policies.data?.find((p) => p.purposeCode === purposeCode) ?? null;

  const grantMutation = useMutation({
    mutationFn: () => grantConsent(memberId, purposeCode),
    onSuccess: onGranted,
  });
  const [collapsed, setCollapsed] = useState(false);

  if (policies.isPending) return <Skeleton lines={3} label={t('loading.default')} />;
  if (policies.isError || policy === null) {
    return <ErrorState error={policies.error} onRetry={() => void policies.refetch()} />;
  }

  if (collapsed) {
    return (
      <EmptyState
        compact
        icon="lock"
        title={t('today.consentCollapsed')}
        action={
          <Button variant="secondary" size="sm" onClick={() => setCollapsed(false)}>
            {t('consentMoment.grant')}
          </Button>
        }
      />
    );
  }

  return (
    <ConsentMoment
      title={policy.purposeName}
      bullets={[policy.purposeDescription, policy.summary]}
      onGrant={() => grantMutation.mutate()}
      onNotNow={() => setCollapsed(true)}
      granting={grantMutation.isPending}
      error={grantMutation.isError ? t('error.internal') : undefined}
    />
  );
}

const MEAL_ICONS: Record<MealType, IconName> = {
  BREAKFAST: 'sun',
  LUNCH: 'meal',
  DINNER: 'moon',
  SNACK: 'food',
};

function MealRow({ meal, memberId }: { meal: Meal; memberId: string }) {
  const mine = meal.portions.find((p) => p.householdMemberId === memberId);
  const energy = mine?.nutrition.energyKcal;
  const time = new Date(meal.consumedAt).toLocaleTimeString(getLocale(), { hour: 'numeric', minute: '2-digit' });

  return (
    <li className="today__meal">
      <Link to={`/meals/${meal.id}`} className="today__meal-main">
        <span className="today__meal-icon">
          <Icon name={MEAL_ICONS[meal.mealType]} size={18} />
        </span>
        <span className="today__meal-text">
          <span className="today__meal-name">{meal.name ?? t(MEAL_TYPE_KEYS[meal.mealType])}</span>
          <span className="today__meal-meta">
            {meal.name && <>{t(MEAL_TYPE_KEYS[meal.mealType])} · </>}
            {time}
            {energy !== null && energy !== undefined && mine?.inMemberLog && (
              <> · {t('today.yourShareEnergy', { kcal: formatAmount(energy) })}</>
            )}
          </span>
        </span>
        <Icon name="chevron-right" size={18} className="today__meal-chev" />
      </Link>
      {mine && !mine.inMemberLog && (
        <Link to={`/meals/${meal.id}`} className="today__meal-pending">
          {t('today.mealNotInLog')}
        </Link>
      )}
    </li>
  );
}

function sortByTime(meals: Meal[]): Meal[] {
  return [...meals].sort((a, b) => a.consumedAt.localeCompare(b.consumedAt));
}

function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));
  useEffect(() => {
    function goOnline() {
      setOnline(true);
    }
    function goOffline() {
      setOnline(false);
    }
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
    };
  }, []);
  return online;
}
