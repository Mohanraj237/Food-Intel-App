import type { GoalProgress } from '@/lib/goals';
import type {
  DayPoint,
  FamilySummary,
  HabitDay,
  HouseholdOverview,
  MetricAggregation,
  MetricDay,
  MetricWeek,
  OverviewMember,
} from '@/lib/dashboard';
import type { MetricCategory } from '@/lib/wellness';

/**
 * A household overview shaped like the Iyer demo family as Raghav sees it: himself, a child he
 * guards, a parent he's caregiver for, a sibling who shares two categories, and two people who
 * share nothing. Values are hand-picked so every test can state its expected number exactly.
 */

export const DATE = '2026-09-13';
export const WEEK = ['2026-09-07', '2026-09-08', '2026-09-09', '2026-09-10', '2026-09-11', '2026-09-12', '2026-09-13'];
export const ZONE = 'Asia/Kolkata';

/** 09:40 IST on the overview's day. */
export const AT_0940 = '2026-09-13T04:10:00Z';
/** 08:10 IST. */
export const AT_0810 = '2026-09-13T02:40:00Z';
/** 07:05 IST. */
export const AT_0705 = '2026-09-13T01:35:00Z';

export function week(
  code: string,
  category: MetricCategory,
  unit: string,
  aggregation: MetricAggregation,
  values: readonly (number | null)[],
  counts?: readonly number[],
): MetricWeek {
  const days: DayPoint[] = WEEK.map((date, i) => {
    const value = values[i] ?? null;
    return { date, value, count: counts?.[i] ?? (value === null ? 0 : 1) };
  });
  return { code, category, unit, aggregation, days };
}

export function day(
  code: string,
  category: MetricCategory,
  unit: string,
  aggregation: MetricAggregation,
  value: number,
  at: string,
  observationCount = 1,
): MetricDay {
  return { code, category, unit, aggregation, value, observationCount, lastRecordedAt: at };
}

export function goal(metricCode: string, unit: string, target: number, actual: number | null, overrides: Partial<GoalProgress> = {}): GoalProgress {
  return {
    goalId: `goal-${metricCode}`,
    subjectMemberId: 'm-raghav',
    metricCode,
    unit,
    direction: 'AT_LEAST',
    period: 'DAILY',
    aggregation: 'SUM',
    target,
    actual,
    dataAvailable: actual !== null,
    windowFrom: '2026-09-12T18:30:00Z',
    windowTo: '2026-09-13T18:29:59Z',
    ...overrides,
  };
}

function habitWeek(done: readonly number[], due = 1): HabitDay[] {
  return WEEK.map((date, i) => ({ date, active: 1, due, done: done[i] ?? 0 }));
}

const ALL_TRACKING = ['ACTIVITY', 'BODY', 'CLINICAL', 'HABITS', 'HYDRATION', 'NUTRITION', 'SLEEP', 'WELLBEING'];

export function member(overrides: Partial<OverviewMember> & Pick<OverviewMember, 'memberId' | 'displayLabel'>): OverviewMember {
  return {
    memberType: 'ADULT',
    isYou: false,
    relationship: 'SHARED',
    experience: 'ADULT',
    visibleScopes: [],
    today: [],
    habits: null,
    trackingConsentActive: null,
    canLogNutrition: false,
    manageScopes: [],
    week: [],
    goals: [],
    habitsWeek: null,
    ...overrides,
  };
}

export const raghav = member({
  memberId: 'm-raghav',
  displayLabel: 'Raghav',
  isYou: true,
  relationship: 'SELF',
  visibleScopes: ALL_TRACKING,
  manageScopes: ALL_TRACKING,
  trackingConsentActive: true,
  canLogNutrition: true,
  today: [
    day('WATER_ML', 'HYDRATION', 'ml', 'SUM', 2200, AT_0940, 3),
    day('STEPS', 'ACTIVITY', 'steps', 'SUM', 7200, AT_0810),
    day('SLEEP_MINUTES', 'SLEEP', 'min', 'SUM', 400, AT_0705),
    day('ENERGY_KCAL', 'NUTRITION', 'kcal', 'SUM', 344, AT_0810),
    day('WEIGHT_KG', 'BODY', 'kg', 'LATEST', 72.4, AT_0705),
  ],
  week: [
    week('WEIGHT_KG', 'BODY', 'kg', 'LATEST', [73.2, 73, 72.9, 72.8, 72.6, 72.5, 72.4]),
    week('STEPS', 'ACTIVITY', 'steps', 'SUM', [9240, 8900, 8560, 8220, 7880, 7540, 7200]),
    week('SLEEP_MINUTES', 'SLEEP', 'min', 'SUM', [430, 425, 420, 415, 410, 405, 400]),
    // Day 4 (10 Sep) was never logged: a gap, not a zero.
    week('WATER_ML', 'HYDRATION', 'ml', 'SUM', [2000, 2100, 1900, null, 2300, 2400, 2200]),
    week('ENERGY_KCAL', 'NUTRITION', 'kcal', 'SUM', [360, 350, 340, 330, 320, 344, 344]),
    week('MOOD_SCORE', 'WELLBEING', 'score', 'LATEST', [4, 4, 3, null, 4, 5, null]),
  ],
  goals: [goal('WATER_ML', 'ml', 2500, 2200), goal('STEPS', 'steps', 8000, 7200)],
  habits: { active: 1, dueToday: 1, doneToday: 1 },
  habitsWeek: habitWeek([1, 1, 1, 0, 1, 1, 1]),
});

export const aarav = member({
  memberId: 'm-aarav',
  displayLabel: 'Aarav',
  memberType: 'CHILD',
  relationship: 'GUARDIAN',
  experience: 'CHILD',
  visibleScopes: ALL_TRACKING,
  manageScopes: ALL_TRACKING,
  trackingConsentActive: true,
  canLogNutrition: true,
  today: [
    day('ENERGY_KCAL', 'NUTRITION', 'kcal', 'SUM', 172, AT_0810, 2),
    day('WATER_ML', 'HYDRATION', 'ml', 'SUM', 1100, AT_0940),
  ],
  week: [
    week('WATER_ML', 'HYDRATION', 'ml', 'SUM', [1000, 1050, 1100, 1150, 1200, 1250, 1100]),
    week('ENERGY_KCAL', 'NUTRITION', 'kcal', 'SUM', [86, 86, null, 86, 86, 86, 172], [1, 1, 0, 1, 1, 1, 2]),
  ],
  goals: [goal('WATER_ML', 'ml', 1200, 1100, { subjectMemberId: 'm-aarav' })],
});

export const krishnan = member({
  memberId: 'm-krishnan',
  displayLabel: 'Krishnan',
  relationship: 'CAREGIVER',
  experience: 'OLDER_ADULT',
  visibleScopes: ['ACTIVITY', 'CLINICAL', 'HYDRATION'],
  manageScopes: ['ACTIVITY', 'HYDRATION'],
  today: [
    day('SYSTOLIC_MMHG', 'CLINICAL', 'mmHg', 'LATEST', 132, AT_0810),
    day('DIASTOLIC_MMHG', 'CLINICAL', 'mmHg', 'LATEST', 84, AT_0810),
    day('WATER_ML', 'HYDRATION', 'ml', 'SUM', 1400, AT_0705),
  ],
  week: [
    week('STEPS', 'ACTIVITY', 'steps', 'SUM', [2700, 2650, 2600, 2550, 2500, 2450, null]),
    week('WATER_ML', 'HYDRATION', 'ml', 'SUM', [1500, 1450, null, 1400, 1450, 1500, 1400]),
    week('SYSTOLIC_MMHG', 'CLINICAL', 'mmHg', 'LATEST', [138, 137, 136, 135, 134, 133, 132]),
    week('DIASTOLIC_MMHG', 'CLINICAL', 'mmHg', 'LATEST', [88, 87, 87, 86, 85, 85, 84]),
  ],
  // A goal on a reading's category never shows on the card (D-037); this one is on water.
  goals: [goal('WATER_ML', 'ml', 1800, 1400, { subjectMemberId: 'm-krishnan' })],
});

export const meera = member({
  memberId: 'm-meera',
  displayLabel: 'Meera',
  relationship: 'SHARED',
  visibleScopes: ['ACTIVITY', 'NUTRITION'],
  today: [day('STEPS', 'ACTIVITY', 'steps', 'SUM', 6100, AT_0705)],
  week: [week('STEPS', 'ACTIVITY', 'steps', 'SUM', [6700, 6600, 6500, 6400, 6300, 6200, 6100])],
});

export const arjun = member({ memberId: 'm-arjun', displayLabel: 'Arjun', relationship: 'NONE' });
export const ananya = member({ memberId: 'm-ananya', displayLabel: 'Ananya', memberType: 'CHILD', relationship: 'NONE', experience: 'CHILD' });

export const summary: FamilySummary = {
  includedMemberIds: ['m-raghav', 'm-aarav', 'm-krishnan', 'm-meera'],
  metrics: [
    {
      code: 'WATER_ML',
      unit: 'ml',
      eligiblePeople: 3,
      days: WEEK.map((date, i) => ({ date, total: i === 6 ? 4700 : 4400, contributors: 3 })),
    },
    {
      code: 'STEPS',
      unit: 'steps',
      eligiblePeople: 4,
      days: WEEK.map((date, i) => ({ date, total: i === 6 ? 13300 : 20000, contributors: i === 6 ? 2 : 3 })),
    },
    {
      code: 'SLEEP_MINUTES',
      unit: 'min',
      eligiblePeople: 2,
      // 400 + 560 over two people = an average of 480 min (8 h); nobody logged on 10 Sep.
      days: WEEK.map((date, i) => ({ date, total: i === 3 ? null : 960, contributors: i === 3 ? 0 : 2 })),
    },
  ],
  meals: { eligiblePeople: 3, days: WEEK.map((date, i) => ({ date, total: null, contributors: i === 6 ? 2 : 3 })) },
  habits: WEEK.map((date) => ({ date, active: 2, due: 2, done: 1 })),
};

export function overview(overrides: Partial<HouseholdOverview> = {}): HouseholdOverview {
  return {
    householdId: 'h-iyer',
    householdName: 'Iyer family',
    individual: false,
    timeZone: ZONE,
    date: DATE,
    week: WEEK,
    mealsAwaitingConfirmation: 0,
    summary,
    members: [raghav, aarav, krishnan, arjun, meera, ananya],
    ...overrides,
  };
}
