import { request } from './api';
import type { KeyFact, ProductSummary } from './catalog';

/**
 * Mirrors backend/.../recommendation/api/*.java. Every alternative publishes both `betterOn`
 * and `worseOn` (D-013); an empty list is an honest result. Product-level reasons (rating,
 * nutrient band, processing level) carry no `memberId`; member reasons (allergens, verdicts)
 * appear only on the household-scoped call (D-060).
 */
export interface AlternativeReason {
  /**
   * SAFER_ALLERGENS · NEW_ALLERGEN_RISK · BETTER_VERDICT · WORSE_VERDICT · HIGHER_RATING ·
   * LOWER_RATING · LOWER_<NUTRIENT> · HIGHER_<NUTRIENT> · LESS_PROCESSED · MORE_PROCESSED
   */
  code: string;
  memberId?: string | null;
  allergenCode?: string | null;
  verdict?: string | null;
  comparedVerdict?: string | null;
  nutrientCode?: string | null;
  candidateValue?: number | null;
  currentValue?: number | null;
}

export interface Alternative {
  productId: string;
  name: string;
  brandName: string;
  categoryCode: string | null;
  imageUrl?: string | null;
  keyFacts: KeyFact[];
  stars?: number | null;
  score?: number | null;
  novaGroup?: number | null;
  betterOn: AlternativeReason[];
  worseOn: AlternativeReason[];
}

export interface AlternativesResult {
  productId: string;
  householdId?: string | null;
  alternatives: Alternative[];
  computedAt: string;
}

export function getAlternatives(householdId: string, productId: string): Promise<AlternativesResult> {
  return request<AlternativesResult>(
    `/households/${encodeURIComponent(householdId)}/products/${encodeURIComponent(productId)}/alternatives`,
  );
}

/** Public, product-level only. */
export function getProductAlternatives(productId: string): Promise<AlternativesResult> {
  return request<AlternativesResult>(`/products/${encodeURIComponent(productId)}/alternatives`, { anonymous: true });
}

/** The groups a "better options" list is shown in; an alternative can sit in more than one. */
export type AlternativeGroup = 'betterHouseholdFit' | 'lowerSugar' | 'higherProtein' | 'lowerSodium' | 'lessProcessed' | 'higherRated';

export function groupsOf(alternative: Alternative): AlternativeGroup[] {
  const codes = new Set(alternative.betterOn.map((r) => r.code));
  const groups: AlternativeGroup[] = [];
  if (codes.has('SAFER_ALLERGENS') || codes.has('BETTER_VERDICT')) groups.push('betterHouseholdFit');
  if (codes.has('LOWER_TOTAL_SUGAR_G') || codes.has('LOWER_ADDED_SUGAR_G')) groups.push('lowerSugar');
  if (codes.has('HIGHER_PROTEIN_G')) groups.push('higherProtein');
  if (codes.has('LOWER_SODIUM_MG')) groups.push('lowerSodium');
  if (codes.has('LESS_PROCESSED')) groups.push('lessProcessed');
  if (codes.has('HIGHER_RATING')) groups.push('higherRated');
  return groups;
}

// ------------------------------------------------------------------------------ compare

export interface CompareMemberFit {
  memberId: string;
  status: string;
  verdict: string | null;
  allergenMatches: string[];
}

export interface CompareItem {
  product: ProductSummary;
  rating: {
    status: 'SCORED' | 'NOT_YET_SCORED' | 'INSUFFICIENT_DATA';
    score: number | null;
    stars: number | null;
    grade: string | null;
    ratingScheme: string | null;
    foodType: string | null;
  };
  novaGroup: number | null;
  dietFlag: 'VEG' | 'NON_VEG' | 'VEGAN' | 'UNKNOWN';
  dietFlagEstimated: boolean;
  nutrients: Array<{ code: string; displayName: string; per100: number; unit: string; basis: string | null; estimated: boolean }>;
  allergens: Array<{ code: string; declarationType: string }>;
  ingredients: string[];
  members: CompareMemberFit[];
}

export interface CompareResult {
  householdId?: string | null;
  items: CompareItem[];
  bestOverall: {
    productId?: string | null;
    outcome: 'WINNER' | 'NO_WINNER_TIE' | 'NO_WINNER_INSUFFICIENT_DATA' | 'NO_WINNER_ALL_CONFLICT';
    reasons: string[];
    excludedForAllergens: string[];
  };
}

/** Public without a household; with one, requires sign-in and returns only the members the caller may see. */
export function getCompare(ids: string[], householdId: string | null): Promise<CompareResult> {
  const params = new URLSearchParams({ ids: ids.join(',') });
  if (householdId) params.set('householdId', householdId);
  return request<CompareResult>(`/compare?${params.toString()}`, { anonymous: householdId === null });
}

// ------------------------------------------------------------------------------ categories

export interface CategoryIntelligence {
  category: { code: string; name: string; path: string; sortOrder: number };
  counts: { products: number; rated: number };
  rails: {
    popular: ProductSummary[];
    betterChoices: ProductSummary[];
    lowestSugar: ProductSummary[];
    highestProtein: ProductSummary[];
    lowestSodium: ProductSummary[];
    leastProcessed: ProductSummary[];
  };
}

export function getCategoryIntelligence(code: string): Promise<CategoryIntelligence> {
  return request<CategoryIntelligence>(`/categories/${encodeURIComponent(code)}/intelligence`, { anonymous: true });
}

export interface CategoryPick {
  product: ProductSummary;
  stars: number | null;
  members: CompareMemberFit[];
}

export function getCategoryPicks(householdId: string, code: string): Promise<CategoryPick[]> {
  return request<CategoryPick[]>(
    `/households/${encodeURIComponent(householdId)}/categories/${encodeURIComponent(code)}/picks`,
  );
}
