import { Link, useParams, useSearchParams } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { useFamilyOverview, type MetricWeek, type OverviewMember } from '@/lib/dashboard';
import { formatMetricValue, metricName } from '@/lib/metricDisplay';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { useLogSheet, type LogKind } from '@/design-system/LogSheet';
import { ProgressTile } from '@/design-system/ProgressTile';
import { RelationshipChip } from '@/design-system/RelationshipChip';
import { HistoryChart } from '@/design-system/HistoryChart';
import { Avatar } from '@/design-system/Avatar';
import { Icon } from '@/design-system/Icon';
import { ErrorState, Skeleton } from '@/design-system/States';
import { NotFoundScreen } from '@/features/shell/NotFoundScreen';
import { GoalMeter } from '@/features/goals/GoalMeter';
import { shortDayLabel, todayInZone } from '@/features/goals/calendarDay';
import {
  CARD_ORDER,
  bloodPressureText,
  bloodPressureToday,
  canLogFor,
  familyDateLabel,
  habitDayText,
  habitStrip,
  habitsLine,
  headlineWeek,
  isBloodPressure,
  logKindForCategory,
  mealsCount,
  rowLabel,
  stripFor,
  type DayContext,
} from '@/features/household/familyMetrics';
import type { MetricCategory } from '@/lib/wellness';
import './MemberView.css';

const DATE_PARAM = /^\d{4}-\d{2}-\d{2}$/;

/** Categories whose every logged metric gets its own weekly chart; the rest chart their headline. */
const CHART_EVERY_METRIC: ReadonlySet<MetricCategory> = new Set(['ACTIVITY', 'BODY', 'CLINICAL', 'WELLBEING']);

/**
 * `/family/:memberId` (plan §18 Journey 3.1, spec §4.4) — one family member's own view: their day,
 * their week a chart per metric, their goals and habits, and exactly what you can see and log.
 *
 * Reads the same single `GET /households/{id}/overview` call as the hub (a cache hit coming from
 * it) — the week and goal progress now travel in that response, so this screen shows trends and
 * goal numbers without a per-member fetch, the fan-out G-01 closed. `?date=` follows the hub's
 * day navigator.
 */
export function MemberView() {
  const { memberId } = useParams<{ memberId: string }>();
  const [params] = useSearchParams();
  const rawDate = params.get('date');
  const date = rawDate && DATE_PARAM.test(rawDate) ? rawDate : undefined;
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });
  const householdId = me.data?.household?.id ?? null;
  const overview = useFamilyOverview(householdId, date);
  const logSheet = useLogSheet();

  const member = overview.data?.members.find((m) => m.memberId === memberId) ?? null;
  useDocumentTitle(member?.displayLabel ?? null);

  // `RequireHousehold` (App.tsx) does not itself require a session — only redirects on
  // `mode = NONE` once one exists — so a signed-out visitor can still reach this route.
  if (!signedIn) {
    return (
      <div className="page member-view">
        <p>{t('family.signInPrompt')}</p>
        <Link to="/you" className="btn btn--primary btn--md">
          {t('action.signIn')}
        </Link>
      </div>
    );
  }

  if (me.isPending || (householdId !== null && overview.isPending)) {
    return (
      <div className="page member-view">
        <Skeleton lines={5} label={t('loading.default')} />
      </div>
    );
  }

  if (me.isError) {
    return (
      <div className="page member-view">
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      </div>
    );
  }

  if (householdId === null) {
    return <NotFoundScreen />;
  }

  const backHref = date ? `/family?date=${date}` : '/family';

  if (overview.isError) {
    return (
      <div className="page member-view">
        <Link to={backHref} className="btn btn--ghost btn--sm">
          {t('action.back')}
        </Link>
        <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />
      </div>
    );
  }

  // Not in this household's overview at all (a stale link, a bad id) and the identity-only
  // `NONE` case (nothing to show, and the hub never links to one of these) both land here.
  if (!member || member.relationship === 'NONE' || !overview.data) {
    return <NotFoundScreen />;
  }

  // `exactOptionalPropertyTypes` (tsconfig) treats `{ kind: undefined }` as different from
  // `kind` being absent, and `LogSheetOpenOptions` (frozen, §2) only declares the latter.
  function openLogSheet(kind: LogKind | null, subjectId: string) {
    logSheet.open(kind ? { kind, subjectId } : { subjectId });
  }

  const data = overview.data;
  const ctx: DayContext = {
    date: data.date,
    timeZone: data.timeZone,
    viewingToday: data.date >= todayInZone(data.timeZone),
  };

  return <MemberViewContent member={member} ctx={ctx} backHref={backHref} onLog={openLogSheet} />;
}

function MemberViewContent({
  member,
  ctx,
  backHref,
  onLog,
}: {
  member: OverviewMember;
  ctx: DayContext;
  backHref: string;
  onLog: (kind: LogKind | null, subjectId: string) => void;
}) {
  const canLog = canLogFor(member.relationship);
  const child = member.memberType === 'CHILD';
  const visibleCategories = CARD_ORDER.filter((category) => member.visibleScopes.includes(category));
  const habits = habitsLine(member.habits, ctx.viewingToday);
  const nothingToShow = visibleCategories.length === 0 && !habits;
  const seeScopes = [...CARD_ORDER, 'HABITS'].filter((scope) => member.visibleScopes.includes(scope));
  const hidesSomething = member.relationship === 'SHARED' || member.relationship === 'CAREGIVER';

  return (
    <div className="page member-view">
      <Link to={backHref} className="page-head__back">
        <Icon name="arrow-left" size={18} />
        {t('nav.family')}
      </Link>

      <header className="member-view__head">
        <Avatar name={member.displayLabel} size="lg" tone={member.isYou ? 1 : undefined} child={child} />
        <span className="member-view__who">
          <h1 className="member-view__name">{member.displayLabel}</h1>
          <RelationshipChip relationship={member.relationship} />
        </span>
        {canLog && (
          <button
            type="button"
            className="btn btn--primary btn--sm member-view__log"
            onClick={() => onLog(null, member.memberId)}
          >
            <Icon name="plus" size={16} />
            {t('memberView.logFor', { name: member.displayLabel })}
          </button>
        )}
      </header>

      {!member.isYou && seeScopes.length > 0 && (
        <div className="member-view__scopes">
          <span className="member-view__scopes-label">{t('memberView.canSee')}</span>
          <ul className="member-view__scope-list" role="list">
            {seeScopes.map((scope) => (
              <li key={scope} className="member-view__scope">
                {member.manageScopes.includes(scope)
                  ? t('memberView.canLogSuffix', { scope: rowLabel(scope) })
                  : rowLabel(scope)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {member.trackingConsentActive === false && (
        <p className="member-view__note" role="note">
          {t('memberView.trackingOff', { name: member.displayLabel })}
        </p>
      )}

      {nothingToShow && <p className="member-view__note">{t('familyMemberCard.notShared')}</p>}

      {canLog ? (
        <ul className="member-view__tiles" role="list">
          {visibleCategories.map((category) => {
            const kind = logKindForCategory(category);
            const tile = todayTile(member, category);
            // See the `exactOptionalPropertyTypes` note above `openLogSheet` — `valueText` is
            // only ever spread in when there's a real value, never passed as `undefined`.
            const valueProp = tile ? { valueText: tile.valueText } : {};
            return (
              <ProgressTile
                key={category}
                metricLabel={rowLabel(category)}
                historyHref={`/family/${member.memberId}/trackers/${tile?.code ?? defaultCode(member, category)}`}
                state={tile ? 'logged' : 'not-logged'}
                {...valueProp}
                onLog={() => {
                  if (kind) onLog(kind, member.memberId);
                }}
              />
            );
          })}
        </ul>
      ) : (
        <ul className="member-view__lines" role="list">
          {visibleCategories.map((category) => {
            const tile = todayTile(member, category);
            return (
              <li key={category} className="member-view__line">
                <span className="member-view__line-label">{rowLabel(category)}</span>
                <span className={tile ? 'member-view__line-value' : 'member-view__line-value member-view__line-value--muted'}>
                  {tile?.valueText ?? (ctx.viewingToday ? t('familyHub.notLoggedToday') : t('familyHub.notLoggedThatDay'))}
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {visibleCategories.length > 0 && <WeekSection member={member} ctx={ctx} categories={visibleCategories} child={child} />}

      {member.goals.length > 0 && (
        <section className="member-view__section" aria-labelledby="member-goals-heading">
          <div className="member-view__section-head">
            <h2 id="member-goals-heading" className="member-view__section-title">
              {t('memberView.goalsHeading')}
            </h2>
            {canLog && <Link to={`/family/${member.memberId}/goals`}>{t('goal.seeAll')}</Link>}
          </div>
          <ul className="goals member-view__goals-list" role="list">
            {member.goals.map((goal) => (
              <li key={goal.goalId} className="goal">
                <GoalMeter progress={goal} child={child} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {habits && (
        <section className="member-view__section" aria-labelledby="member-habits-heading">
          <div className="member-view__section-head">
            <h2 id="member-habits-heading" className="member-view__section-title">
              {habits.label}
            </h2>
            <Link to={`/family/${member.memberId}/habits`}>{t('memberView.open')}</Link>
          </div>
          <p className="member-view__habits-value">{habits.value}</p>
          {member.habitsWeek && <HabitsChart member={member} />}
        </section>
      )}

      {member.goals.length === 0 && canLog && (
        <p className="member-view__goals">
          <Link to={`/family/${member.memberId}/goals`}>{t('memberView.goalsLink')}</Link>
        </p>
      )}

      {hidesSomething && (
        <p className="member-view__footer">{t('memberView.notSharedFooter', { name: member.displayLabel })}</p>
      )}
    </div>
  );
}

/** Today's headline for a category: its value and the metric code its history lives under. */
function todayTile(member: OverviewMember, category: MetricCategory): { valueText: string; code: string } | null {
  if (category === 'CLINICAL') {
    const bp = bloodPressureToday(member.today);
    if (bp) return { valueText: bp.text, code: 'SYSTOLIC_MMHG' };
  }
  const week = headlineWeek(member, category);
  const day =
    (week && member.today.find((d) => d.code === week.code)) ?? member.today.find((d) => d.category === category);
  if (!day) return null;
  const valueText =
    member.memberType === 'CHILD' && category === 'NUTRITION'
      ? mealsCount(day.observationCount)
      : formatMetricValue(day.code, day.value, day.unit);
  return { valueText, code: day.code };
}

/** The tracker a not-yet-logged tile opens: this week's headline, else the category's usual metric. */
function defaultCode(member: OverviewMember, category: MetricCategory): string {
  const fallback: Record<MetricCategory, string> = {
    NUTRITION: 'ENERGY_KCAL',
    HYDRATION: 'WATER_ML',
    ACTIVITY: 'STEPS',
    SLEEP: 'SLEEP_MINUTES',
    WELLBEING: 'MOOD_SCORE',
    BODY: 'WEIGHT_KG',
    CLINICAL: 'SYSTOLIC_MMHG',
  };
  return headlineWeek(member, category)?.code ?? fallback[category];
}

/** The weeks to chart for a category: its headline, or every logged metric where each one matters. */
function weeksToChart(member: OverviewMember, category: MetricCategory, child: boolean): MetricWeek[] {
  const headline = headlineWeek(member, category);
  if (!headline) return [];
  if (child && category === 'NUTRITION') return [headline];
  const all = CHART_EVERY_METRIC.has(category) ? member.week.filter((w) => w.category === category) : [headline];
  // Blood pressure is one chart of both numbers, keyed on the top one.
  return all.filter((w) => !(w.code === 'DIASTOLIC_MMHG' && all.some((o) => o.code === 'SYSTOLIC_MMHG')));
}

function WeekSection({
  member,
  ctx,
  categories,
  child,
}: {
  member: OverviewMember;
  ctx: DayContext;
  categories: readonly MetricCategory[];
  child: boolean;
}) {
  const charts = categories.flatMap((category) => weeksToChart(member, category, child).map((week) => ({ category, week })));

  return (
    <section className="member-view__section" aria-labelledby="member-week-heading">
      <div className="member-view__section-head">
        <h2 id="member-week-heading" className="member-view__section-title">
          {t('memberView.weekHeading')}
        </h2>
        <span className="member-view__section-date">{familyDateLabel(ctx.date)}</span>
      </div>
      <p className="member-view__lede">{t('memberView.weekLede')}</p>

      {charts.length === 0 ? (
        <p className="member-view__note">{t('memberView.nothingThisWeek')}</p>
      ) : (
        <ul className="member-view__charts" role="list">
          {charts.map(({ category, week }) => (
            <li key={week.code} className="member-view__chart">
              <WeekChart member={member} week={week} category={category} child={child} />
            </li>
          ))}
        </ul>
      )}

      {/* One static note wherever readings appear — the same words whatever the values (spec §6.6). */}
      {charts.some(({ category }) => category === 'CLINICAL') && (
        <p className="member-view__readings-note">
          {member.isYou ? t('readings.note') : t('readings.noteForMember', { name: member.displayLabel })}
        </p>
      )}
    </section>
  );
}

function WeekChart({
  member,
  week,
  category,
  child,
}: {
  member: OverviewMember;
  week: MetricWeek;
  category: MetricCategory;
  child: boolean;
}) {
  const goal = category === 'CLINICAL' ? null : (member.goals.find((g) => g.metricCode === week.code) ?? null);
  const strip = stripFor(member, week, goal);
  const bp = isBloodPressure(week.code);
  const childMeals = child && category === 'NUTRITION';
  const name = bp ? metricName('BLOOD_PRESSURE') : childMeals ? rowLabel('NUTRITION') : metricName(week.code);

  const points = strip.points.map((p) => ({
    date: p.date,
    value: p.value,
    label: shortDayLabel(p.date),
    ...(p.pair !== undefined ? { pair: p.pair } : {}),
  }));

  return (
    <article className="member-view__chart-card">
      <header className="member-view__chart-head">
        <h3 className="member-view__chart-title">{name}</h3>
        <Link to={`/family/${member.memberId}/trackers/${week.code}`} className="member-view__chart-link">
          {t('memberView.seeLonger')} <span aria-hidden="true">›</span>
        </Link>
      </header>
      <p className="member-view__chart-summary">{strip.summary}</p>
      <HistoryChart
        points={points}
        unit={week.unit}
        aggregation={strip.aggregation}
        goalValue={strip.goalValue}
        title={strip.title}
        summary={strip.summary}
        formatValue={(point) =>
          bp
            ? (bloodPressureText(point.value, point.pair ?? null) ?? '')
            : childMeals
              ? mealsCount(point.value ?? 0)
              : formatMetricValue(week.code, point.value ?? 0, week.unit)
        }
      />
    </article>
  );
}

function HabitsChart({ member }: { member: OverviewMember }) {
  const strip = habitStrip(member.habitsWeek ?? []);
  return (
    <>
      <p className="member-view__chart-summary">{strip.summary}</p>
      <HistoryChart
        points={strip.points.map((p) => ({ date: p.date, value: p.value, label: shortDayLabel(p.date) }))}
        unit=""
        aggregation="SUM"
        title={strip.title}
        summary={strip.summary}
        formatValue={(point) => {
          const day = member.habitsWeek?.find((d) => d.date === point.date);
          return day ? habitDayText(day.done, day.due) : '';
        }}
      />
    </>
  );
}
