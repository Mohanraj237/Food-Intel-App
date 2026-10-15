import { request } from './api';

/**
 * The scoring contract. Mirrors the backend records in
 * backend/.../scoring/api/*.java exactly (see the note on catalog.ts for why these are
 * hand-written rather than generated).
 *
 * `status` fields are the point: a result is never silently missing, it always says which of
 * a closed set of honest reasons applies (no rule set active yet, not enough label data, no
 * profile, consent not granted, consent withdrawn, not authorized to view). A `verdict` only
 * exists when `status === 'ASSESSED'`; a `score` only when `status === 'SCORED'`.
 */

export interface Reason {
  /**
   * NO_ACTIVE_RULES · MISSING_NUTRIENT_DATA · NUTRIENT_EXCEEDS_THRESHOLD · NO_GOALS_SET, and
   * from engine v2 (D-059): POINTS_BAND · ASSUMED_ZERO · DERIVED_FACT_THRESHOLD ·
   * SCORE_NOT_APPLICABLE.
   */
  code: string;
  nutrientCode: string | null;
  basis: 'PER_SERVING' | 'PER_100G' | 'PER_100ML' | null;
  value: number | null;
  threshold: number | null;
  standardRef: string | null;
}

/** One factor's contribution to a v2 score — "why this number" (D-059). */
export interface ScoreDriver {
  code: string;
  /** NEGATIVE: a risk factor whose points add up; POSITIVE: a positive factor whose points subtract. */
  direction: 'NEGATIVE' | 'POSITIVE';
  nutrientCode: string;
  /** null when `assumed` */
  value: number | null;
  unit: string | null;
  basis: 'PER_100G' | 'PER_100ML';
  points: number;
  maxPoints: number;
  /** The band edge the value crossed; null when it sat in the lowest band. */
  threshold: number | null;
  standardRef: string;
  explanationKey: string;
  /** The catalogue holds this value as an estimate (e.g. fruit/veg content from the ingredient list). */
  isEstimated: boolean;
  /** Undeclared on the label and counted as 0 points. */
  assumed: boolean;
}

export interface ProcessingLevel {
  novaGroup: 1 | 2 | 3 | 4;
  isEstimated: boolean;
  source: string | null;
}

export interface Coverage {
  required: string[];
  present: string[];
  missing: string[];
}

export interface ProductHealth {
  productVersionId: string;
  ruleSetVersion: string;
  /**
   * SCORED: score/grade (and stars under the v2 method) present. NOT_YET_SCORED: the active
   * rule set has nothing that applies (D-024's honest default). INSUFFICIENT_DATA: the method
   * applies but a required label value is missing, or the method exempts this food type —
   * `coverage.missing` and `reasons` say which.
   */
  status: 'SCORED' | 'NOT_YET_SCORED' | 'INSUFFICIENT_DATA';
  score: number | null;
  grade: string | null;
  /** 0.5–5, only under the v2 points method. */
  stars: number | null;
  /** FSSAI_INR_DRAFT_2022_DERIVED for the v2 points method; WEIGHTED_MEAN_V1 for the v1 shape. */
  ratingScheme: string | null;
  foodType: 'SOLID' | 'LIQUID' | 'FAT_OIL' | null;
  drivers: ScoreDriver[];
  processingLevel: ProcessingLevel | null;
  coverage: Coverage;
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

export interface BatchFit {
  householdId: string;
  items: Array<{ productId: string; productVersionId: string; members: PersonalFit[] }>;
  notFound: string[];
}

/** Requires sign-in. Up to 50 products; same member scope as `getHouseholdFit`. */
export function getBatchFit(householdId: string, productIds: string[]): Promise<BatchFit> {
  return request<BatchFit>(`/households/${encodeURIComponent(householdId)}/products/fit:batch`, {
    method: 'POST',
    body: { productIds },
  });
}

/** Requires sign-in. 403 if the caller is not a live member of householdId. */
export function getHouseholdFit(householdId: string, productId: string): Promise<HouseholdFit> {
  return request<HouseholdFit>(
    `/households/${encodeURIComponent(householdId)}/products/${encodeURIComponent(productId)}/fit`,
  );
}
