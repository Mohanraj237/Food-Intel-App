import { t, type MessageKey } from '@/lib/i18n';
import { formatMetricValue, formatTimeInZone, metricName } from '@/lib/metricDisplay';
import type { MetricCategory } from '@/lib/wellness';
import type {
  FamilySummary,
  HabitDay,
  HabitsDay,
  HouseholdOverview,
  MetricDay,
  MetricWeek,
  OverviewMember,
} from '@/lib/dashboard';
import type { GoalProgress } from '@/lib/goals';
import type { Relationship } from '@/lib/access';
import type { LogKind } from '@/design-system/LogSheet';
import type { WeekStripPoint } from '@/design-system/WeekStrip';
import { shortDayLabel } from '@/features/goals/calendarDay';

/**
 * Shared, read-only derivations from the overview (plan §18 Journey 3), used by the Family hub
 * (`FamilyWellness.tsx`) and a member's own view (`features/family/MemberView.tsx`) so the two
 * screens describe the same server payload the same way instead of drifting. Nothing here
 * fetches anything — every function takes data the caller already has from a single
 * `GET /households/{id}/overview` call (plan §11's "one source per screen" rule).
 *
 * Rules every function keeps, because the screens built on them must:
 * - **Missing is not zero.** A day nobody logged is a `null` point and a gap on a strip.
 * - **Readings are shown, never judged** (D-037): value, unit and time; no goal line, no average.
 * - **A child's nutrition is a meal count**, never a kcal headline (UXD-08).
 * - **Nothing ranks people.** The feed orders by *when* something was logged, never by value, and
 *   the family summary has no per-person figure at all (D-051).
 */

const CATEGORY_LABEL_KEYS: Record<MetricCategory, MessageKey> = {
  NUTRITION: 'category.NUTRITION',
  HYDRATION: 'category.HYDRATION',
  ACTIVITY: 'category.ACTIVITY',
  SLEEP: 'category.SLEEP',
  BODY: 'category.BODY',
  CLINICAL: 'category.CLINICAL',
  WELLBEING: 'category.WELLBEING',
};

/** The order a person thinks about their day: what they ate first, then everything else. */
export const CATEGORY_ORDER: readonly MetricCategory[] = [
  'NUTRITION',
  'HYDRATION',
  'ACTIVITY',
  'SLEEP',
  'BODY',
  'CLINICAL',
  'WELLBEING',
];

/** Card, feed and member-page order (spec §4.4): Meals · Water · Activity · Sleep · Mood · Body · Readings. */
export const CARD_ORDER: readonly MetricCategory[] = [
  'NUTRITION',
  'HYDRATION',
  'ACTIVITY',
  'SLEEP',
  'WELLBEING',
  'BODY',
  'CLINICAL',
];

/** At most this many metric rows on a card (spec §4.4); the rest are named in an "Also:" line. */
export const MAX_CARD_ROWS = 4;

const ROW_LABEL_KEYS: Record<string, MessageKey> = {
  NUTRITION: 'familyHub.row.NUTRITION',
  HYDRATION: 'familyHub.row.HYDRATION',
  ACTIVITY: 'familyHub.row.ACTIVITY',
  SLEEP: 'familyHub.row.SLEEP',
  WELLBEING: 'familyHub.row.WELLBEING',
  BODY: 'familyHub.row.BODY',
  CLINICAL: 'familyHub.row.CLINICAL',
  HABITS: 'familyHub.row.HABITS',
};

/**
 * The metric a row headlines for each category, most useful first. The finer breakdown lives one
 * tap away on the member's page.
 */
const HEADLINE_CODES: Record<MetricCategory, readonly string[]> = {
  NUTRITION: ['ENERGY_KCAL'],
  HYDRATION: ['WATER_ML'],
  ACTIVITY: ['STEPS', 'ACTIVE_MINUTES', 'WORKOUT_MINUTES', 'DISTANCE_KM'],
  SLEEP: ['SLEEP_MINUTES'],
  WELLBEING: ['MOOD_SCORE', 'STRESS_SCORE'],
  BODY: ['WEIGHT_KG', 'WAIST_CM', 'BODY_FAT_PCT', 'BMI', 'HEIGHT_CM'],
  CLINICAL: ['SYSTOLIC_MMHG', 'DIASTOLIC_MMHG', 'RESTING_HEART_RATE_BPM', 'BLOOD_GLUCOSE_MGDL', 'SPO2_PCT', 'BODY_TEMPERATURE_C'],
};

const TRACKING_SCOPES = ['BODY', 'ACTIVITY', 'SLEEP', 'HYDRATION', 'NUTRITION', 'CLINICAL', 'WELLBEING', 'HABITS'];

/** Mirrors `LogSheet.tsx`'s own (unexported) reverse mapping — kept in sync by hand since that
 *  file is Foundation's to edit, not this workstream's. `NUTRITION` has no entry: a meal is
 *  logged at `/meals/new`, never through a metric tile (plan §18 1.2's "meal → /meals/new"). */
const CATEGORY_TO_LOG_KIND: Partial<Record<MetricCategory, LogKind>> = {
  HYDRATION: 'water',
  ACTIVITY: 'activity',
  SLEEP: 'sleep',
  WELLBEING: 'mood',
  BODY: 'body',
  CLINICAL: 'readings',
};

export function categoryLabel(category: MetricCategory): string {
  return t(CATEGORY_LABEL_KEYS[category]);
}

/** The short label a card row, the feed and "You can log:" use: Meals, Water, … Readings, Habits. */
export function rowLabel(scope: string): string {
  const key = ROW_LABEL_KEYS[scope];
  return key ? t(key) : scope;
}

export function logKindForCategory(category: MetricCategory): LogKind | null {
  return CATEGORY_TO_LOG_KIND[category] ?? null;
}

export interface MetricLine {
  label: string;
  value: string;
}

/** "2 of 3 done today" — counts only, never a percentage or a streak (D-036/D-040). `null` when
 *  there is nothing to say (HABITS not visible, or no active habit). */
export function habitsLine(habits: HabitsDay | null, viewingToday = true): MetricLine | null {
  if (!habits || habits.active === 0) return null;
  // "1 of 0 done today" is what "{done} of {due}" produces for a weekday habit ticked on a
  // Saturday: nothing was expected, something was done anyway. The numbers are correct and
  // the sentence is nonsense, so when fewer are due than were done the count stands on its own
  // rather than being framed against a denominator it has outgrown.
  const value =
    !framesAgainstDue(habits.doneToday, habits.dueToday)
      ? t(viewingToday ? 'familyHub.habitsDoneOnly' : 'familyHub.habitsDoneOnlyOn', { done: habits.doneToday })
      : t(viewingToday ? 'familyHub.habitsValue' : 'familyHub.habitsValueOn', {
          done: habits.doneToday,
          due: habits.dueToday,
        });
  return { label: t('familyHub.habitsLabel'), value };
}

/** "{done} of {due}" reads true only while something was due and no more was done than that. */
function framesAgainstDue(done: number, due: number): boolean {
  return due > 0 && done <= due;
}

/** One day of habits, day-neutral: "1 of 2 done", or "2 done" when that framing would not be true. */
export function habitDayText(done: number, due: number): string {
  return framesAgainstDue(done, due)
    ? t('familyHub.habitsValueOn', { done, due })
    : t('familyHub.habitsDoneOnlyOn', { done });
}

/**
 * "You, children you guard, adults who made you a caregiver" (plan §7) — the only relationships
 * a Log action is ever offered for. `lib/dashboard.ts`'s own `loggableSubjects(overview)` answers
 * the same question over a whole overview at once; this is the narrower per-card test.
 */
export function canLogFor(relationship: Relationship): boolean {
  return relationship === 'SELF' || relationship === 'GUARDIAN' || relationship === 'CAREGIVER';
}

/**
 * "Sat, 12 Sep" for the household-local calendar date an overview/today response reports.
 * Parsed as a UTC midnight, the same trick `lib/access.ts`'s `dayHeading` uses for a bare
 * `YYYY-MM-DD` key, so no device time zone can push the label onto a neighbouring day.
 */
export function familyDateLabel(dateKey: string, locale = 'en-IN'): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(`${dateKey}T00:00:00Z`));
}

/** "you, Aarav and Meera". */
export function listOf(items: readonly string[], locale = 'en-IN'): string {
  return new Intl.ListFormat(locale, { style: 'long', type: 'conjunction' }).format(items);
}

/** A name mid-sentence: "No water logged…", but "No BMI logged…". */
function lowerFirst(text: string): string {
  return /^[A-Z][a-z]/.test(text) ? text.charAt(0).toLowerCase() + text.slice(1) : text;
}

// ------------------------------------------------------------------ the day being viewed

export interface DayContext {
  /** `YYYY-MM-DD`: the day the overview is for. */
  date: string;
  /** The household zone (D-053); every time of day is formatted in it. */
  timeZone: string;
  /** Viewing today, rather than a past day — changes "today" to "that day" in copy. */
  viewingToday: boolean;
}

// ------------------------------------------------------------------ values

export function isBloodPressure(code: string): boolean {
  return code === 'SYSTOLIC_MMHG' || code === 'DIASTOLIC_MMHG';
}

/** "132/84 mmHg"; a lone number says which one it is (spec §6.1). */
export function bloodPressureText(top: number | null, bottom: number | null): string | null {
  if (top !== null && bottom !== null) {
    return t('familyHub.bpValue', { top: Math.round(top), bottom: Math.round(bottom) });
  }
  if (top !== null) return t('familyHub.bpTopOnly', { value: Math.round(top) });
  if (bottom !== null) return t('familyHub.bpBottomOnly', { value: Math.round(bottom) });
  return null;
}

/**
 * The day's blood pressure. The two numbers are paired only when they are the same reading —
 * the same `effectiveAt` (spec §6.1); otherwise the newer one is shown on its own, labelled.
 */
export function bloodPressureToday(today: readonly MetricDay[]): { text: string; at: string } | null {
  const top = today.find((d) => d.code === 'SYSTOLIC_MMHG');
  const bottom = today.find((d) => d.code === 'DIASTOLIC_MMHG');
  if (top && bottom && top.lastRecordedAt === bottom.lastRecordedAt) {
    return { text: bloodPressureText(top.value, bottom.value) as string, at: top.lastRecordedAt };
  }
  const newest = [top, bottom]
    .filter((d): d is MetricDay => d !== undefined)
    .sort((a, b) => Date.parse(b.lastRecordedAt) - Date.parse(a.lastRecordedAt))[0];
  if (!newest) return null;
  const text = newest.code === 'SYSTOLIC_MMHG' ? bloodPressureText(newest.value, null) : bloodPressureText(null, newest.value);
  return { text: text as string, at: newest.lastRecordedAt };
}

/** "3 meals" — a child's nutrition headline is a count, never kcal (UXD-08). */
export function mealsCount(count: number): string {
  return count === 1 ? t('familyHub.mealsCountOne') : t('familyHub.mealsCount', { count });
}

function isChildNutrition(member: Pick<OverviewMember, 'memberType'>, category: MetricCategory): boolean {
  return member.memberType === 'CHILD' && category === 'NUTRITION';
}

/** The metric a row headlines for `category`, if anything in it was logged this week. */
export function headlineWeek(member: Pick<OverviewMember, 'week'>, category: MetricCategory): MetricWeek | undefined {
  for (const code of HEADLINE_CODES[category]) {
    const week = member.week.find((w) => w.code === code);
    if (week) return week;
  }
  return member.week.find((w) => w.category === category);
}

// ------------------------------------------------------------------ strips and sentences

export interface StripModel {
  points: WeekStripPoint[];
  aggregation: 'SUM' | 'LATEST';
  goalValue: number | null;
  title: string;
  summary: string;
}

function pointLabel(date: string, value: string | null): string {
  const day = shortDayLabel(date);
  return value === null ? t('weekStrip.pointNotLogged', { date: day }) : t('weekStrip.point', { date: day, value });
}

/** "+0.4 kg" / "−0.8 kg" — plain arithmetic, no colour or arrow (UXD-10). */
function signedChange(code: string, delta: number, unit: string): string {
  if (Math.abs(delta) < 0.05) return formatMetricValue(code, 0, unit);
  return `${delta > 0 ? '+' : '−'}${formatMetricValue(code, Math.abs(delta), unit)}`;
}

/** The §6.4 summary sentence for one metric's week — also the strip's `<desc>`. */
export function weekSentence(week: MetricWeek): string {
  const metric = metricName(week.code);
  const days = week.days.length;
  const logged = week.days.filter((d) => d.value !== null);
  const first = logged[0];
  const latest = logged[logged.length - 1];
  if (!first || !latest || latest.value === null || first.value === null) {
    return t('weekSummary.none', { metric: lowerFirst(metric), days });
  }

  if (week.aggregation === 'SUM') {
    // Averaged over the days that were logged, never over seven: dividing by an unlogged day
    // would turn "not logged" into a zero (spec §6.2).
    const average = logged.reduce((sum, d) => sum + (d.value ?? 0), 0) / logged.length;
    return t('weekSummary.sum', {
      metric,
      logged: logged.length,
      days,
      average: formatMetricValue(week.code, average, week.unit),
    });
  }

  const count = week.days.reduce((sum, d) => sum + d.count, 0);
  const value = formatMetricValue(week.code, latest.value, week.unit);
  const date = shortDayLabel(latest.date);
  if (week.category === 'BODY') {
    if (logged.length < 2) {
      return count === 1
        ? t('weekSummary.latestBodyOne', { metric, value, date })
        : t('weekSummary.latestReadings', { metric, count, value, date });
    }
    return t('weekSummary.latestBody', {
      metric,
      count,
      value,
      date,
      firstDate: shortDayLabel(first.date),
      change: signedChange(week.code, latest.value - first.value, week.unit),
    });
  }
  if (week.category === 'CLINICAL') {
    // Count and latest only — no average, no change, no comparison (spec §6.2, D-037).
    return count === 1
      ? t('weekSummary.latestReadingsOne', { metric, value, date })
      : t('weekSummary.latestReadings', { metric, count, value, date });
  }
  return count === 1
    ? t('weekSummary.latestEntriesOne', { metric, value, date })
    : t('weekSummary.latestEntries', { metric, count, value, date });
}

/** Blood pressure's week: both numbers of each day on one strip, dots only (D-037). */
function bloodPressureStrip(member: Pick<OverviewMember, 'week'>): StripModel {
  const top = member.week.find((w) => w.code === 'SYSTOLIC_MMHG');
  const bottom = member.week.find((w) => w.code === 'DIASTOLIC_MMHG');
  const days = (top ?? bottom)?.days ?? [];
  const points = days.map((day, i) => {
    const topValue = top?.days[i]?.value ?? null;
    const bottomValue = bottom?.days[i]?.value ?? null;
    return { date: day.date, value: topValue, pair: bottomValue, label: pointLabel(day.date, bloodPressureText(topValue, bottomValue)) };
  });
  const readings = (top ?? bottom)?.days.reduce((sum, d) => sum + d.count, 0) ?? 0;
  const latest = [...points].reverse().find((p) => p.value !== null || p.pair !== null);
  const metric = metricName('BLOOD_PRESSURE');
  const latestText = latest ? bloodPressureText(latest.value, latest.pair) : null;
  const summary =
    latest && latestText
      ? readings === 1
        ? t('weekSummary.latestReadingsOne', { metric, value: latestText, date: shortDayLabel(latest.date) })
        : t('weekSummary.latestReadings', { metric, count: readings, value: latestText, date: shortDayLabel(latest.date) })
      : t('weekSummary.none', { metric: lowerFirst(metric), days: days.length });
  return { points, aggregation: 'LATEST', goalValue: null, title: t('familyHub.stripTitle', { metric }), summary };
}

/** A child's meals as a count a day — bars of how many meals, never of kcal (UXD-08). */
function mealsStrip(week: MetricWeek): StripModel {
  const points = week.days.map((d) => ({
    date: d.date,
    value: d.count > 0 ? d.count : null,
    label: pointLabel(d.date, d.count > 0 ? mealsCount(d.count) : null),
  }));
  const logged = points.filter((p) => p.value !== null).length;
  return {
    points,
    aggregation: 'SUM',
    goalValue: null,
    title: t('familyHub.stripTitle', { metric: rowLabel('NUTRITION') }),
    summary: t('weekSummary.meals', { logged, days: points.length }),
  };
}

/**
 * The strip for one metric row. A dashed goal line only for a live *daily* SUM goal — a weekly
 * goal has no place on daily bars, and a reading never gets one (spec §4.10).
 */
export function stripFor(member: Pick<OverviewMember, 'week' | 'memberType'>, week: MetricWeek, goal: GoalProgress | null): StripModel {
  if (isBloodPressure(week.code)) return bloodPressureStrip(member);
  if (isChildNutrition(member, week.category)) return mealsStrip(week);
  const points = week.days.map((d) => ({
    date: d.date,
    value: d.value,
    label: pointLabel(d.date, d.value === null ? null : formatMetricValue(week.code, d.value, week.unit)),
  }));
  const goalLine =
    goal && goal.period === 'DAILY' && goal.aggregation === 'SUM' && week.aggregation === 'SUM' && week.category !== 'CLINICAL'
      ? goal.target
      : null;
  return {
    points,
    aggregation: week.aggregation,
    goalValue: goalLine,
    title: t('familyHub.stripTitle', { metric: metricName(week.code) }),
    summary: weekSentence(week),
  };
}

/** Habits done each day — a count, never a streak (D-036). A day with nothing due and nothing done is a gap. */
export function habitStrip(days: readonly HabitDay[], summaryKey: MessageKey = 'weekSummary.habits'): StripModel {
  const points = days.map((d) => {
    const nothing = d.due === 0 && d.done === 0;
    return { date: d.date, value: nothing ? null : d.done, label: pointLabel(d.date, nothing ? null : habitDayText(d.done, d.due)) };
  });
  const done = days.reduce((sum, d) => sum + d.done, 0);
  const due = days.reduce((sum, d) => sum + d.due, 0);
  return {
    points,
    aggregation: 'SUM',
    goalValue: null,
    title: t('familyHub.habitsStripTitle'),
    summary: t(summaryKey, { done, due, days: days.length }),
  };
}

// ------------------------------------------------------------------ a member's card

export interface CardRowModel {
  key: string;
  category: MetricCategory;
  label: string;
  valueText: string;
  /** Something was logged on the day being viewed. */
  logged: boolean;
  /** A reading's time of day ("8:10 am"); totals don't have one. */
  timeText: string | null;
  strip: StripModel;
  goal: GoalProgress | null;
}

function rowFor(member: OverviewMember, category: MetricCategory, ctx: DayContext): CardRowModel {
  const week = headlineWeek(member, category) as MetricWeek;
  let valueText = ctx.viewingToday ? t('familyHub.notLoggedToday') : t('familyHub.notLoggedThatDay');
  let logged = false;
  let timeText: string | null = null;

  if (isBloodPressure(week.code)) {
    const bp = bloodPressureToday(member.today);
    if (bp) {
      valueText = bp.text;
      logged = true;
      timeText = formatTimeInZone(bp.at, ctx.timeZone);
    }
  } else {
    const day = member.today.find((d) => d.code === week.code);
    if (day) {
      valueText = isChildNutrition(member, category)
        ? mealsCount(day.observationCount)
        : formatMetricValue(day.code, day.value, day.unit);
      logged = true;
      if (day.aggregation === 'LATEST') timeText = formatTimeInZone(day.lastRecordedAt, ctx.timeZone);
    }
  }

  // No goal on a reading, ever (D-037); a child has no nutrition goals to show (D-047).
  const goal =
    category === 'CLINICAL' || isChildNutrition(member, category)
      ? null
      : (member.goals.find((g) => g.metricCode === week.code) ?? null);

  return {
    key: category,
    category,
    label: rowLabel(category),
    valueText,
    logged,
    timeText,
    strip: stripFor(member, week, goal),
    goal,
  };
}

/**
 * Up to {@link MAX_CARD_ROWS} rows, one per visible category with anything logged this week, in
 * {@link CARD_ORDER}; the rest are named in `overflow` so nothing is silently dropped.
 */
export function cardRowsFor(member: OverviewMember, ctx: DayContext): { rows: CardRowModel[]; overflow: string[] } {
  const categories = CARD_ORDER.filter(
    (category) => member.visibleScopes.includes(category) && headlineWeek(member, category) !== undefined,
  );
  return {
    rows: categories.slice(0, MAX_CARD_ROWS).map((category) => rowFor(member, category, ctx)),
    overflow: categories.slice(MAX_CARD_ROWS).map(rowLabel),
  };
}

export interface HabitsRowModel {
  label: string;
  valueText: string;
  strip: StripModel | null;
}

/** The habits row — always shown when there are habits, never pushed off by the metric rows. */
export function habitsRowFor(member: OverviewMember, ctx: DayContext): HabitsRowModel | null {
  const line = habitsLine(member.habits, ctx.viewingToday);
  if (!line) return null;
  return { label: line.label, valueText: line.value, strip: member.habitsWeek ? habitStrip(member.habitsWeek) : null };
}

/** "Updated 9:40 am" — when the newest thing on this card was logged, for the day being viewed. */
export function updatedTextFor(member: OverviewMember, ctx: DayContext): string | null {
  const newest = member.today.reduce<string | null>(
    (latest, d) => (latest === null || Date.parse(d.lastRecordedAt) > Date.parse(latest) ? d.lastRecordedAt : latest),
    null,
  );
  return newest ? t('familyHub.updatedAt', { time: formatTimeInZone(newest, ctx.timeZone) }) : null;
}

/** The one line under a card's rows that says why you see what you see (spec §4.4, P3). */
export function cardFooterFor(member: OverviewMember): string | null {
  if (member.trackingConsentActive === false) {
    if (member.relationship === 'GUARDIAN') return t('familyHub.trackingOffGuardian', { name: member.displayLabel });
    if (member.isYou) return t('familyHub.trackingOffSelf');
  }
  if (member.relationship === 'CAREGIVER') {
    const scopes = [...CARD_ORDER, 'HABITS'].filter((scope) => member.manageScopes.includes(scope)).map(rowLabel);
    if (scopes.length > 0) return t('familyHub.canLog', { scopes: listOf(scopes) });
  }
  if (
    (member.relationship === 'SHARED' || member.relationship === 'CAREGIVER') &&
    TRACKING_SCOPES.some((scope) => !member.visibleScopes.includes(scope))
  ) {
    return t('familyHub.otherRecordsNotShared');
  }
  return null;
}

// ------------------------------------------------------------------ the updates feed

export interface FeedRowModel {
  key: string;
  memberId: string;
  name: string;
  label: string;
  valueText: string;
  /** Epoch millis of the newest entry this row stands for. */
  at: number;
  timeText: string;
}

function feedEntry(member: OverviewMember, category: MetricCategory): { text: string; at: string } | null {
  if (category === 'CLINICAL') {
    const bp = bloodPressureToday(member.today);
    if (bp) return bp;
  }
  const day =
    HEADLINE_CODES[category].map((code) => member.today.find((d) => d.code === code)).find((d) => d !== undefined) ??
    member.today.find((d) => d.category === category);
  if (!day) return null;
  const text = isChildNutrition(member, category) ? mealsCount(day.observationCount) : formatMetricValue(day.code, day.value, day.unit);
  return { text, at: day.lastRecordedAt };
}

/**
 * What the people you can see logged on the day, newest first — one row per person and category.
 * Ordered by *when*, never by value: the feed is a timeline, not a leaderboard (D-051).
 */
export function feedRowsFor(overview: HouseholdOverview): FeedRowModel[] {
  const rows: FeedRowModel[] = [];
  for (const member of overview.members) {
    if (member.relationship === 'NONE') continue;
    for (const category of CARD_ORDER) {
      if (!member.visibleScopes.includes(category)) continue;
      const entry = feedEntry(member, category);
      if (!entry) continue;
      rows.push({
        key: `${member.memberId}:${category}`,
        memberId: member.memberId,
        name: member.displayLabel,
        label: rowLabel(category),
        valueText: entry.text,
        at: Date.parse(entry.at),
        timeText: formatTimeInZone(entry.at, overview.timeZone),
      });
    }
  }
  // Array.prototype.sort is stable, so equal times keep the server's card order.
  return rows.sort((a, b) => b.at - a.at);
}

// ------------------------------------------------------------------ the family summary

export interface SummaryTileModel {
  key: string;
  label: string;
  valueText: string;
  subText: string;
  strip: StripModel;
}

function peopleText(logged: number, people: number): string {
  return people === 1 ? t('familySummary.forPerson', { logged }) : t('familySummary.forPeople', { logged, people });
}

const SUMMARY_LABEL_KEYS: Record<string, MessageKey> = {
  WATER_ML: 'familySummary.water',
  STEPS: 'familySummary.steps',
  SLEEP_MINUTES: 'familySummary.sleep',
};

/**
 * One tile per summed metric, plus habits. Water and steps are family totals; sleep is an average
 * per person who logged it (a family "38 hours of sleep" means nothing). Every tile states whose
 * numbers it holds — "for 3 of 4 people" — so a total is never mistaken for everyone's.
 */
export function summaryTilesFor(summary: FamilySummary, overview: HouseholdOverview, ctx: DayContext): SummaryTileModel[] {
  const tiles: SummaryTileModel[] = summary.metrics.map((metric) => {
    const averaged = metric.code === 'SLEEP_MINUTES';
    const perDay = metric.days.map((d) => ({
      date: d.date,
      contributors: d.contributors,
      value: d.total === null || d.contributors === 0 ? null : averaged ? d.total / d.contributors : d.total,
    }));
    const day = perDay.find((d) => d.date === ctx.date) ?? perDay[perDay.length - 1];
    const format = (value: number) => formatMetricValue(metric.code, value, metric.unit);
    const loggedDays = perDay.filter((d) => d.value !== null).length;
    const name = metricName(metric.code);
    return {
      key: metric.code,
      label: t(SUMMARY_LABEL_KEYS[metric.code] ?? 'familySummary.water'),
      valueText:
        day && day.value !== null
          ? format(day.value)
          : t(ctx.viewingToday ? 'familySummary.notLoggedYet' : 'familySummary.notLoggedThatDay'),
      subText: peopleText(day?.contributors ?? 0, metric.eligiblePeople),
      strip: {
        points: perDay.map((d) => ({
          date: d.date,
          value: d.value,
          label: pointLabel(d.date, d.value === null ? null : `${format(d.value)} · ${peopleText(d.contributors, metric.eligiblePeople)}`),
        })),
        aggregation: 'SUM',
        goalValue: null,
        title: t('familySummary.stripTitle', { metric: lowerFirst(name) }),
        summary: averaged
          ? t('familySummary.avgSentence', { logged: loggedDays, days: perDay.length })
          : t('familySummary.sumSentence', { metric: name, logged: loggedDays, days: perDay.length }),
      },
    };
  });

  if (summary.habits) {
    const day = summary.habits.find((d) => d.date === ctx.date) ?? summary.habits[summary.habits.length - 1];
    const people = overview.members.filter(
      (m) => m.habitsWeek !== null && summary.includedMemberIds.includes(m.memberId),
    ).length;
    tiles.push({
      key: 'HABITS',
      label: t('familySummary.habits'),
      valueText: !day
        ? t('familySummary.notLoggedYet')
        : framesAgainstDue(day.done, day.due)
          ? t('familySummary.habitsValue', { done: day.done, due: day.due })
          : t('familySummary.habitsDoneOnly', { done: day.done }),
      subText: t('familySummary.habitsPeople', { people }),
      strip: habitStrip(summary.habits, 'familySummary.habitsSentence'),
    });
  }
  return tiles;
}

/** "Meals logged for 2 of 3 people" — people, never kcal (UXD-08). */
export function mealsLineFor(summary: FamilySummary, ctx: DayContext): string | null {
  if (!summary.meals) return null;
  const day = summary.meals.days.find((d) => d.date === ctx.date);
  const logged = day?.contributors ?? 0;
  return summary.meals.eligiblePeople === 1
    ? t('familySummary.mealsLineOne', { logged })
    : t('familySummary.mealsLine', { logged, people: summary.meals.eligiblePeople });
}

/** "you, Aarav, Krishnan, Meera and Vikram" — whose records the summary is built from. */
export function includedNamesFor(summary: FamilySummary, overview: HouseholdOverview): string {
  const names = summary.includedMemberIds
    .map((id) => overview.members.find((m) => m.memberId === id))
    .filter((m): m is OverviewMember => m !== undefined)
    .map((m) => (m.isYou ? t('familySummary.you') : m.displayLabel));
  return listOf(names);
}
