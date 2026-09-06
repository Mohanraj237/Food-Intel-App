import { request } from './api';
import type { ProductSummary } from './catalog';

/**
 * The activity contract (FR-012, D-005). Mirrors
 * backend/.../activity/api/*.java exactly. Requires sign-in; every event and every
 * "recent" listing is scoped to the caller's own account only (D-002's self-only pattern).
 */

export type ActivityEventType =
  | 'VIEWED'
  | 'SCANNED'
  | 'COMPARED'
  | 'BOUGHT'
  | 'BOUGHT_ALTERNATIVE'
  | 'BOUGHT_NEITHER'
  | 'SKIPPED';

export interface RecordActivityInput {
  productId: string;
  alternativeProductId?: string | null;
  eventType: ActivityEventType;
}

export interface RecentActivityEntry {
  id: string;
  eventType: ActivityEventType;
  product: ProductSummary | null;
  alternativeProduct: ProductSummary | null;
  occurredAt: string;
}

export function recordActivity(input: RecordActivityInput): Promise<{ id: string }> {
  return request<{ id: string }>('/activity', {
    method: 'POST',
    body: input,
  });
}

export function getRecentActivity(): Promise<RecentActivityEntry[]> {
  return request<RecentActivityEntry[]>('/activity/recent');
}
