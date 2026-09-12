import { request } from './api';
import type { MetricCategory } from './wellness';

/**
 * The insights contract. Mirrors `backend/.../insights/`.
 *
 * Read-only by design: there is no create or dismiss endpoint, because insights are
 * recomputed from observations on every request and never stored. That is why a corrected
 * meal silently corrects yesterday's statement instead of leaving a stale one behind.
 *
 * `availability` is the field that matters most to a renderer. `NO_DATA` and `PARTIAL` are
 * normal, expected outcomes with their own copy — not errors, and not something to hide
 * behind a spinner.
 */

export type InsightType =
  | 'NUTRITION_TREND'
  | 'GOAL_PROGRESS'
  | 'HABIT_CONSISTENCY'
  | 'MEAL_PATTERN'
  | 'ACTIVITY_TREND'
  | 'HYDRATION_TREND'
  | 'DATA_COVERAGE';

export type InsightWindow = 'TODAY' | 'WEEK' | 'MONTH';

export type InsightAvailability = 'SUFFICIENT' | 'PARTIAL' | 'NO_DATA';

/** One number the statement was computed from, so a reader can check the arithmetic. */
export interface InsightEvidence {
  label: string;
  value: number;
  unit: string;
  category: MetricCategory;
  observationDays: number;
}

export interface Insight {
  type: InsightType;
  subjectMemberId: string;
  window: InsightWindow;
  availability: InsightAvailability;
  headline: string;
  detail: string | null;
  evidence: InsightEvidence[];
  createdAt: string;
}

export function insightsForMember(
  memberId: string,
  window: InsightWindow = 'WEEK',
): Promise<Insight[]> {
  return request<Insight[]>(
    `/insights/members/${encodeURIComponent(memberId)}?window=${window}`,
  );
}
