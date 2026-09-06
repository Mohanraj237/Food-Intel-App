import { request } from './api';

/**
 * The ops-review contract. Mirrors backend/.../capture/api/OpsReviewController.java and
 * ContributionService's queue/detail views exactly. Every endpoint here is re-checked
 * server-side against the caller's real database role on every call (D-032) — this client
 * carries no authorization logic of its own, only convenience for deciding whether to show
 * the ops nav entry (`getAccess`).
 */

export interface OpsAccess {
  isOperator: boolean;
}

export interface ContributionQueueEntry {
  id: string;
  gtin: string | null;
  productName: string;
  brandName: string | null;
  categoryCode: string | null;
  status: string;
  submittedAt: string;
}

export interface ContributionNutrientRow {
  nutrientCode: string;
  amountPer100: number | null;
  amountPerServing: number | null;
}

export interface ContributionDetail {
  id: string;
  gtin: string | null;
  brandName: string | null;
  productName: string;
  categoryCode: string | null;
  packSizeValue: number | null;
  packSizeUnit: string | null;
  servingDeclaredText: string | null;
  nutrients: ContributionNutrientRow[];
  ingredientsRawText: string | null;
  allergenCodes: string[];
  status: string;
  submittedAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
  reviewNotes: string | null;
}

/** A UI affordance only — every endpoint below re-checks this server-side regardless. */
export function getOpsAccess(): Promise<OpsAccess> {
  return request<OpsAccess>('/ops/access');
}

export function listQueue(status: 'PENDING' | 'APPROVED' | 'REJECTED' = 'PENDING'): Promise<ContributionQueueEntry[]> {
  return request<ContributionQueueEntry[]>(`/ops/contributions?status=${encodeURIComponent(status)}`);
}

export function getContributionDetail(id: string): Promise<ContributionDetail> {
  return request<ContributionDetail>(`/ops/contributions/${encodeURIComponent(id)}`);
}

export function approveContribution(id: string): Promise<{ productId: string }> {
  return request<{ productId: string }>(`/ops/contributions/${encodeURIComponent(id)}/approve`, {
    method: 'POST',
  });
}

export function rejectContribution(id: string, reason: string): Promise<void> {
  return request<void>(`/ops/contributions/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: { reason },
  });
}
