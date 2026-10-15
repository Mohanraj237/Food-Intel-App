import { request } from './api';

/**
 * The wellness contract (D-034 Phase 1). Mirrors `backend/.../wellness/api/*.java`.
 *
 * Two things here are load-bearing and easy to get wrong from the client side:
 *
 * - **The unit is never sent.** It is fixed per metric by the server
 *   (`MetricCode.canonicalUnit()`), so a form collects a number and a metric, never a unit.
 *   Sending one would be ignored; believing the client owns it leads to mixed-unit history.
 * - **`adultOnly` comes from the server**, and the UI uses it to decide which metrics to
 *   offer for a given subject. It is a convenience, not a control: the server refuses a
 *   disallowed write regardless of what the form showed (D-036).
 */

export type MetricCategory =
  | 'BODY'
  | 'ACTIVITY'
  | 'SLEEP'
  | 'HYDRATION'
  | 'NUTRITION'
  | 'CLINICAL'
  | 'WELLBEING';

export type ObservationSource = 'MANUAL' | 'DERIVED' | 'IMPORT';

export interface MetricDefinition {
  code: string;
  category: MetricCategory;
  unit: string;
  /** True when this metric may not be recorded for a child subject (D-036). */
  adultOnly: boolean;
}

export interface Observation {
  id: string;
  subjectMemberId: string;
  metricCode: string;
  metricCategory: MetricCategory;
  value: number;
  unit: string;
  effectiveAt: string;
  recordedAt: string;
  source: ObservationSource;
}

export function listMetricDefinitions(): Promise<MetricDefinition[]> {
  return request<MetricDefinition[]>('/wellness/metrics');
}

export function recordObservation(
  memberId: string,
  input: { metricCode: string; value: number; effectiveAt?: string },
): Promise<Observation> {
  return request<Observation>(`/wellness/members/${encodeURIComponent(memberId)}/observations`, {
    method: 'POST',
    body: input,
  });
}

export function listObservations(
  memberId: string,
  params: { from?: string; to?: string } = {},
): Promise<Observation[]> {
  const search = new URLSearchParams();
  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  const query = search.toString();
  return request<Observation[]>(
    `/wellness/members/${encodeURIComponent(memberId)}/observations${query ? `?${query}` : ''}`,
  );
}

export function metricHistory(
  memberId: string,
  metricCode: string,
  params: { from?: string; to?: string } = {},
): Promise<Observation[]> {
  const search = new URLSearchParams();
  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  const query = search.toString();
  return request<Observation[]>(
    `/wellness/members/${encodeURIComponent(memberId)}/observations/${encodeURIComponent(metricCode)}${query ? `?${query}` : ''}`,
  );
}

/**
 * Which categories of this member's data the caller may see.
 *
 * Worth calling before rendering anything about another member: it lets a screen show
 * "Priya shares activity with you" instead of discovering the boundary by way of a 403,
 * which is the difference between a designed state and an error screen.
 */
export function visibleCategories(memberId: string): Promise<MetricCategory[]> {
  return request<MetricCategory[]>(`/wellness/members/${encodeURIComponent(memberId)}/visibility`);
}

