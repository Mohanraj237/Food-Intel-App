import { keepPreviousData, useQuery, type UseQueryResult } from '@tanstack/react-query';
import { request } from './api';
import type { Relationship } from './access';
import type { ExperienceTier } from './me';
import type { GoalProgress } from './goals';
import type { MetricCategory } from './wellness';

/**
 * The dashboard contract: one member's day, and the household overview (W1, D-043/D-044/D-053).
 *
 * Mirrors `backend/.../dashboard/application/{TodayView, FamilyOverviewView, MetricDay}.java`
 * and `habits/application/HabitService.DaySummary` field for field.
 *
 * Read-only compositions assembled on the server from observations, goals, habits and meals,
 * already filtered by the access policy. Two rules a renderer must keep:
 *
 * - **Missing is not zero.** `metrics`/`today` list only metrics with at least one observation
 *   that day. A metric that is absent was not logged; nothing in this file ever substitutes 0.
 * - **Not shared is a designed state.** A category outside `visibleScopes` renders as "not
 *   shared with you", never as blank and never as "not logged" — see {@link metricDayState}.
 *
 * There are no cross-member totals and no ranking in the overview, by design (D-040, D-051).
 */

export type MetricAggregation = 'SUM' | 'LATEST';

/**
 * One metric's day: summed (water, steps) or the newest reading (weight). Only produced for a
 * metric with at least one observation that day, so `value` is always a real reading.
 */
export interface MetricDay {
  code: string;
  category: MetricCategory;
  unit: string;
  aggregation: MetricAggregation;
  value: number;
  observationCount: number;
  /** When the newest reading applies to (`effectiveAt`). */
  lastRecordedAt: string;
}

/** `HabitService.DaySummary` — counts only: no streak, score or percentage exists to render. */
export interface HabitsDay {
  active: number;
  dueToday: number;
  doneToday: number;
}

/** `GET /members/{id}/today?date=` (`TodayView`; nulls are written, not omitted). */
export interface MemberToday {
  memberId: string;
  displayLabel: string;
  /** `YYYY-MM-DD`, the household-local day summarised. */
  date: string;
  /** IANA zone whose midnight bounds the day (D-053). */
  timeZone: string;
  /** SELF, GUARDIAN, CAREGIVER or SHARED — NONE is refused with a 403. */
  relationship: Relationship;
  /** Scope names (`AccessScope`) the caller may see; a string list on the wire. */
  visibleScopes: string[];
  /** One row per metric logged that day, in visible categories only. */
  metrics: MetricDay[];
  /** Live goals in visible categories; empty when no metric category is visible. */
  goals: GoalProgress[];
  /** `null` unless HABITS is visible. */
  habits: HabitsDay | null;
  /** The caller's own meal shares awaiting confirmation (last 14 days); 0 for anyone else's day. */
  mealsAwaitingConfirmation: number;
  /** Told only to the member and a child's guardian; `null` otherwise. */
  trackingConsentActive: boolean | null;
}

/**
 * One day of a {@link MetricWeek} (`MetricWeek.DayPoint`). `value` is `null` when nothing was
 * logged that day — a gap to draw as a gap, never a zero.
 */
export interface DayPoint {
  /** `YYYY-MM-DD`, household-local. */
  date: string;
  value: number | null;
  /** Readings that day; for nutrition, how many meal shares. */
  count: number;
}

/** One metric across the overview's week, reduced a day at a time exactly as {@link MetricDay}. */
export interface MetricWeek {
  code: string;
  category: MetricCategory;
  unit: string;
  aggregation: MetricAggregation;
  /** Every day of the week, oldest first, logged or not. */
  days: DayPoint[];
}

/** `HabitService.HabitDay` — counts only: no streak, score or percentage exists to render. */
export interface HabitDay {
  date: string;
  active: number;
  due: number;
  done: number;
}

/** `FamilySummary.FamilyDay`. `total` is `null` when nobody logged that day — never 0. */
export interface FamilyDay {
  date: string;
  total: number | null;
  contributors: number;
}

export interface FamilyMetric {
  code: string;
  unit: string;
  /** How many included people's category the caller can see — the "of m" in "n of m people". */
  eligiblePeople: number;
  days: FamilyDay[];
}

/** Meals as a count of people — never a kcal total (UXD-08). Every `total` is `null`. */
export interface FamilyCoverage {
  eligiblePeople: number;
  days: FamilyDay[];
}

/**
 * The household at a glance (D-054), built on the server from the caller's own cards only — so it
 * never holds a number the caller couldn't already read on someone's card. It has no per-person
 * breakdown and no ordering: nothing here can be rendered as a ranking (D-051).
 */
export interface FamilySummary {
  includedMemberIds: string[];
  /** Water, steps and sleep, in that order, each only where someone included shares it. */
  metrics: FamilyMetric[];
  meals: FamilyCoverage | null;
  habits: HabitDay[] | null;
}

/** One card of the overview (`FamilyOverviewView.MemberCard`; nulls are written). */
export interface OverviewMember {
  memberId: string;
  displayLabel: string;
  memberType: 'ADULT' | 'CHILD';
  isYou: boolean;
  relationship: Relationship;
  /** Full tier for you, a child you guard, or someone who chose you as caregiver; else CHILD/ADULT. */
  experience: ExperienceTier;
  /** Empty for a NONE member: that is how "not shared with you" differs from "nothing logged". */
  visibleScopes: string[];
  /** One row per metric logged today in a visible category. */
  today: MetricDay[];
  habits: HabitsDay | null;
  trackingConsentActive: boolean | null;
  /** A meal the caller logs would go straight into this member's record (D-041). */
  canLogNutrition: boolean;
  /** Scopes the caller may log for this member — "You can log: Water, Activity". */
  manageScopes: string[];
  /** One row per metric logged at least once in the week, in a visible category. */
  week: MetricWeek[];
  /** Live goals in visible categories, measured from the same read as `week`. */
  goals: GoalProgress[];
  /** The week's habits day by day; `null` unless HABITS is visible. */
  habitsWeek: HabitDay[] | null;
}

/** `GET /households/{id}/overview?date=` — you first, then children you guard, then by joinedAt. */
export interface HouseholdOverview {
  householdId: string;
  householdName: string;
  individual: boolean;
  timeZone: string;
  date: string;
  /** The seven household-local days ending on `date`, oldest first. */
  week: string[];
  /** The caller's own meal shares not yet in their log (last two weeks). */
  mealsAwaitingConfirmation: number;
  /** `null` when fewer than two people's records are visible to the caller. */
  summary: FamilySummary | null;
  members: OverviewMember[];
}

// --------------------------------------------------------------------- requests

export const DASHBOARD_QUERY_KEYS = {
  memberToday: (memberId: string, date: string | null) => ['dashboard', 'today', memberId, date] as const,
  overview: (householdId: string, date: string | null) => ['dashboard', 'overview', householdId, date] as const,
  /** Prefix: invalidate after any write that changes a day (a log, a goal, a habit, a meal). */
  all: ['dashboard'] as const,
};

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function dateQuery(date: string | undefined): string {
  if (date === undefined) return '';
  if (!DATE_PATTERN.test(date)) {
    // A malformed date would be a 400 from the server; failing here names the actual bug.
    throw new Error(`date must be YYYY-MM-DD, got "${date}"`);
  }
  return `?date=${encodeURIComponent(date)}`;
}

/**
 * One member's day. `date` defaults (server-side) to today in the household's zone; a future
 * date is a 400. A 403 means the caller may see nothing of this member — render that as a
 * designed "not shared with you" state, not as an error.
 */
export function getMemberToday(memberId: string, date?: string): Promise<MemberToday> {
  return request<MemberToday>(`/members/${encodeURIComponent(memberId)}/today${dateQuery(date)}`);
}

export function getHouseholdOverview(householdId: string, date?: string): Promise<HouseholdOverview> {
  return request<HouseholdOverview>(`/households/${encodeURIComponent(householdId)}/overview${dateQuery(date)}`);
}

// ------------------------------------------------------------------------ hooks

/**
 * One member's day under {@link DASHBOARD_QUERY_KEYS.memberToday}. Held back while `memberId`
 * is `null` (still resolving who "me" is). Omit `date` for the household's today.
 */
export function useTodaySummary(memberId: string | null, date?: string): UseQueryResult<MemberToday> {
  return useQuery({
    queryKey: DASHBOARD_QUERY_KEYS.memberToday(memberId ?? '', date ?? null),
    queryFn: () => getMemberToday(memberId as string, date),
    enabled: memberId !== null,
  });
}

/**
 * The household overview under {@link DASHBOARD_QUERY_KEYS.overview}; held back while `null`.
 * Stepping to another day keeps the previous day on screen until the new one arrives
 * (`isPlaceholderData`), so the page doesn't collapse to skeletons on every step.
 */
export function useFamilyOverview(householdId: string | null, date?: string): UseQueryResult<HouseholdOverview> {
  return useQuery({
    queryKey: DASHBOARD_QUERY_KEYS.overview(householdId ?? '', date ?? null),
    queryFn: () => getHouseholdOverview(householdId as string, date),
    enabled: householdId !== null,
    placeholderData: keepPreviousData,
  });
}

// ---------------------------------------------------------------------- helpers

/** The metric for `code`, if one was logged that day. */
export function findMetric(metrics: readonly MetricDay[], code: string): MetricDay | undefined {
  return metrics.find((metric) => metric.code === code);
}

/** True only when something was actually recorded that day. */
export function isLogged(metric: MetricDay | undefined): metric is MetricDay {
  return metric !== undefined && metric.observationCount > 0 && typeof metric.value === 'number';
}

/** The day's value, or `null` when nothing was recorded. Never 0 for "missing". */
export function metricValue(metric: MetricDay | undefined): number | null {
  return isLogged(metric) ? metric.value : null;
}

export type MetricDayState =
  | { state: 'LOGGED'; value: number; unit: string; aggregation: MetricAggregation; lastRecordedAt: string }
  | { state: 'NOT_LOGGED' }
  | { state: 'NOT_SHARED' };

/**
 * The three honest states of one metric for one person on one day: a value, "not logged", or
 * "not shared with you". A metric category maps to the access scope of the same name (D-044
 * §6.1), so visibility is a name lookup. Pass `{ visibleScopes, metrics: card.today }` for an
 * overview card.
 */
/**
 * "You, always; children you guard; adults who made you a caregiver" (plan §7) — the one
 * subject list every subject-switcher in the product means, derived off the overview the
 * caller already has rather than a raw member list (workstreams doc §4.6). `NONE`/`SHARED`
 * members are excluded: seeing someone's data is not the same as being allowed to log for
 * them, and this list is only ever used to choose who a write is *for*.
 */
export function loggableSubjects(overview: HouseholdOverview): OverviewMember[] {
  const self = overview.members.find((m) => m.isYou);
  const others = overview.members.filter(
    (m) => !m.isYou && (m.relationship === 'GUARDIAN' || m.relationship === 'CAREGIVER'),
  );
  return self ? [self, ...others] : others;
}

export function metricDayState(
  day: { visibleScopes: readonly string[]; metrics: readonly MetricDay[] },
  code: string,
  category: MetricCategory,
): MetricDayState {
  if (!day.visibleScopes.includes(category)) {
    return { state: 'NOT_SHARED' };
  }
  const metric = findMetric(day.metrics, code);
  if (!isLogged(metric)) {
    return { state: 'NOT_LOGGED' };
  }
  return {
    state: 'LOGGED',
    value: metric.value,
    unit: metric.unit,
    aggregation: metric.aggregation,
    lastRecordedAt: metric.lastRecordedAt,
  };
}
