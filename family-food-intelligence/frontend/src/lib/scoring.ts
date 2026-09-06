import { request } from './api';

/**
 * The scoring contract. Mirrors the backend records in
 * backend/.../scoring/api/*.java exactly (see the note on catalog.ts for why these are
 * hand-written rather than generated).
 *
 * `status` fields are the point: a result is never silently missing, it always says which of
 * a closed set of honest reasons applies (no rule set active yet, no profile, consent not
 * granted, consent withdrawn, not authorized to view). A `verdict` only exists when
 * `status === 'ASSESSED'`.
 */

export interface Reason {
  code: string;
  nutrientCode: string | null;
  basis: 'PER_SERVING' | 'PER_100G' | null;
  value: number | null;
  threshold: number | null;
  standardRef: string | null;
}

export interface ProductHealth {
  productVersionId: string;
  ruleSetVersion: string;
  status: 'SCORED' | 'NOT_YET_SCORED';
  score: number | null;
  grade: string | null;
  reasons: Reason[];
  computedAt: string;
}

export type PersonalFitStatus =
  | 'ASSESSED'
  | 'NO_PROFILE'
  | 'CONSENT_REQUIRED'
  | 'CONSENT_WITHDRAWN'
  | 'NOT_AUTHORIZED';

export interface PersonalFit {
  memberId: string;
  status: PersonalFitStatus;
  verdict: 'EVERYDAY' | 'OCCASIONAL' | 'LIMIT' | 'DOESNT_FIT' | 'INSUFFICIENT_DATA' | null;
  reasons: Reason[];
  allergenMatches: string[];
  ruleSetVersion: string | null;
  computedAt: string;
}

export interface HouseholdFit {
  householdId: string;
  productVersionId: string;
  /** Only the members the caller is authorized to see — self, plus any child they guard.
   *  Another adult's fit is simply absent, never a placeholder (D-002). */
  members: PersonalFit[];
  computedAt: string;
}

/** Public, anonymous — a health assessment is a fact about the product, not about a person. */
export function getProductHealth(productId: string): Promise<ProductHealth> {
  return request<ProductHealth>(`/products/${encodeURIComponent(productId)}/assessment`, {
    anonymous: true,
  });
}

/** Requires sign-in. 403 if the caller is not a live member of householdId. */
export function getHouseholdFit(householdId: string, productId: string): Promise<HouseholdFit> {
  return request<HouseholdFit>(
    `/households/${encodeURIComponent(householdId)}/products/${encodeURIComponent(productId)}/fit`,
  );
}
