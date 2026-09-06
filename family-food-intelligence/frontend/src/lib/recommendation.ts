import { request } from './api';
import type { KeyFact } from './catalog';

/**
 * The recommendation contract (D-013). Mirrors
 * backend/.../recommendation/api/*.java exactly.
 *
 * Every alternative carries both `betterOn` and `worseOn` — never only the
 * flattering side. An empty `alternatives` list is a first-class, honest
 * result: it means no same-category candidate existed, or nothing offered a
 * real, data-backed improvement (see D-033 — this never invents a
 * nutrient-direction judgment like "lower sugar is better").
 */

export interface AlternativeReason {
  code: 'SAFER_ALLERGENS' | 'NEW_ALLERGEN_RISK' | 'BETTER_VERDICT' | 'WORSE_VERDICT';
  memberId: string;
  allergenCode: string | null;
  verdict: string | null;
  comparedVerdict: string | null;
}

export interface Alternative {
  productId: string;
  name: string;
  brandName: string;
  categoryCode: string | null;
  keyFacts: KeyFact[];
  betterOn: AlternativeReason[];
  worseOn: AlternativeReason[];
}

export interface AlternativesResult {
  productId: string;
  householdId: string;
  alternatives: Alternative[];
  computedAt: string;
}

/** Requires sign-in. 403 if the caller is not a live member of householdId. */
export function getAlternatives(householdId: string, productId: string): Promise<AlternativesResult> {
  return request<AlternativesResult>(
    `/households/${encodeURIComponent(householdId)}/products/${encodeURIComponent(productId)}/alternatives`,
  );
}
