import { request } from './api';

/**
 * Goals and habits (Phase 2). Mirrors `backend/.../goals` and `backend/.../habits`.
 *
 * Kept in one module because they are two halves of the same question — "what is this person
 * working towards" — and a screen almost always shows both.
 *
 * Two fields a renderer must respect:
 *
 * - `GoalProgress.dataAvailable`. When false there is no `actual`, and the UI must say "not
 *   logged yet" rather than drawing a zero. "You drank 0 ml today" and "you have not logged
 *   water today" are different sentences, and only one is true.
 * - `HabitConsistency` is two counts and nothing else. There is deliberately no streak, score
 *   or percentage to render (D-036, CH-004), so a UI cannot turn a child's habit into a
 *   leaderboard even by accident.
 */

export type GoalDirection = 'AT_LEAST' | 'AT_MOST' | 'MAINTAIN';
export type GoalPeriod = 'DAILY' | 'WEEKLY';

export interface Goal {
  id: string;
  subjectMemberId: string;
  metricCode: string;
  unit: string;
  direction: GoalDirection;
  targetValue: number;
  period: GoalPeriod;
  startedOn: string;
  endedOn: string | null;
  active: boolean;
}

export interface GoalProgress {
  goalId: string;
  subjectMemberId: string;
  metricCode: string;
  unit: string;
  direction: GoalDirection;
  period: GoalPeriod;
  /** How the window was reduced to one number: summed (water) or latest reading (weight). */
  aggregation: 'SUM' | 'LATEST';
  target: number;
  /** Absent whenever `dataAvailable` is false. Never substitute zero. */
  actual?: number | null;
  dataAvailable: boolean;
  windowFrom: string;
  windowTo: string;
}

export function listGoals(memberId: string, includeEnded = false): Promise<Goal[]> {
  return request<Goal[]>(
    `/goals?memberId=${encodeURIComponent(memberId)}&includeEnded=${includeEnded}`,
  );
}

export function createGoal(input: {
  memberId: string;
  metricCode: string;
  direction: GoalDirection;
  targetValue: number;
  period: GoalPeriod;
}): Promise<Goal> {
  return request<Goal>('/goals', { method: 'POST', body: input });
}

export function endGoal(goalId: string): Promise<Goal> {
  return request<Goal>(`/goals/${encodeURIComponent(goalId)}/end`, { method: 'POST' });
}

export function goalProgress(memberId: string): Promise<GoalProgress[]> {
  return request<GoalProgress[]>(`/goals/progress?memberId=${encodeURIComponent(memberId)}`);
}

/* ------------------------------------------------------------------ habits */

export type HabitCadence = 'DAILY' | 'WEEKDAYS' | 'WEEKLY';

export interface Habit {
  id: string;
  subjectMemberId: string;
  name: string;
  cadence: HabitCadence;
  active: boolean;
  createdAt: string;
  archivedAt: string | null;
}

/** "N of M expected days" — a fact about the log, not a score. */
export interface HabitConsistency {
  habitId: string;
  from: string;
  to: string;
  completedDays: number;
  expectedDays: number;
}

export function listHabits(memberId: string, includeArchived = false): Promise<Habit[]> {
  return request<Habit[]>(
    `/habits?memberId=${encodeURIComponent(memberId)}&includeArchived=${includeArchived}`,
  );
}

export function createHabit(input: {
  memberId: string;
  name: string;
  cadence: HabitCadence;
}): Promise<Habit> {
  return request<Habit>('/habits', { method: 'POST', body: input });
}

export function updateHabit(habitId: string, input: { name: string; cadence: HabitCadence }): Promise<Habit> {
  return request<Habit>(`/habits/${encodeURIComponent(habitId)}`, { method: 'PUT', body: input });
}

export function archiveHabit(habitId: string): Promise<Habit> {
  return request<Habit>(`/habits/${encodeURIComponent(habitId)}/archive`, { method: 'POST' });
}

export function reactivateHabit(habitId: string): Promise<Habit> {
  return request<Habit>(`/habits/${encodeURIComponent(habitId)}/reactivate`, { method: 'POST' });
}

/** Idempotent: completing the same day twice is one fact. */
export function completeHabit(habitId: string, date: string): Promise<void> {
  return request<void>(
    `/habits/${encodeURIComponent(habitId)}/completions/${encodeURIComponent(date)}`,
    { method: 'PUT' },
  );
}

export function uncompleteHabit(habitId: string, date: string): Promise<void> {
  return request<void>(
    `/habits/${encodeURIComponent(habitId)}/completions/${encodeURIComponent(date)}`,
    { method: 'DELETE' },
  );
}

export function habitHistory(habitId: string, from?: string, to?: string): Promise<string[]> {
  const search = new URLSearchParams();
  if (from) search.set('from', from);
  if (to) search.set('to', to);
  const query = search.toString();
  return request<string[]>(
    `/habits/${encodeURIComponent(habitId)}/completions${query ? `?${query}` : ''}`,
  );
}

export function habitConsistency(habitId: string, from?: string, to?: string): Promise<HabitConsistency> {
  const search = new URLSearchParams();
  if (from) search.set('from', from);
  if (to) search.set('to', to);
  const query = search.toString();
  return request<HabitConsistency>(
    `/habits/${encodeURIComponent(habitId)}/consistency${query ? `?${query}` : ''}`,
  );
}

/** Today's date as the server expects it, in the user's own calendar. */
export function todayIso(): string {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}
