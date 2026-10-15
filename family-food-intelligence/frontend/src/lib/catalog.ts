import { request } from './api';
import { formatAmount, formatMeasure, isPlausible } from './nutrition';

/**
 * The catalogue contract.
 *
 * These types mirror the Phase 3 API exactly. They are hand-written for now and
 * become generated from the backend's OpenAPI document once it stabilises, so a
 * contract drift breaks the typecheck rather than production.
 */

export interface PackSize {
  value: number | null;
  unit: string | null;
}

export interface KeyFact {
  nutrientCode: string;
  displayName: string;
  amountPerServing: number | null;
  amountPer100: number | null;
  unit: string;
}

export interface ProductSummary {
  id: string;
  brandName: string;
  name: string;
  packSize: PackSize | null;
  categoryCode: string | null;
  categoryName: string | null;
  imageUrl: string | null;
  dataQuality: string | null;
  servingSizeG: number | null;
  servingDeclaredText: string | null;
  keyFacts: KeyFact[];
  /** Search v2 (D-061). Present on summaries; absent on the product detail, which carries them on `currentVersion`. */
  dietFlag?: 'VEG' | 'NON_VEG' | 'VEGAN' | 'UNKNOWN';
  dietFlagEstimated?: boolean;
  novaGroup?: number | null;
  /** Allergen codes the label declares (CONTAINS or MAY_CONTAIN). Never a free-from claim. */
  allergenCodes?: string[];
  /** The persisted product-level score, filled by search only; absent when the product is not scored. */
  assessment?: AssessmentBadge;
}

export interface AssessmentBadge {
  status: 'SCORED' | 'INSUFFICIENT_DATA';
  score: number | null;
  stars: number | null;
  grade: string | null;
}

export interface NutrientRow {
  nutrientCode: string;
  displayName: string;
  amountPer100: number | null;
  amountPerServing: number | null;
  unit: string;
  basisPer100: string | null;
  isEstimated: boolean;
  confidence: number | null;
  source: { type: string; ref: string | null; capturedAt: string } | null;
}

export interface ProductDetail extends ProductSummary {
  currentVersion: {
    id: string;
    versionNo: number;
    effectiveFrom: string;
    servingSizeG: number | null;
    servingDeclaredText: string | null;
    servingPlausible: boolean | null;
    dataQuality: string;
    capturedAt: string;
    verifiedAt: string | null;
    captureMethod: string;
    /** NOVA processing group 1–4, or null when unclassified (V15, D-059). */
    novaGroup: number | null;
    /** Which source the NOVA group came from (e.g. OPEN_FOOD_FACTS) — an estimate unless a reviewer set it. */
    novaSource: string | null;
    /** VEG · NON_VEG · VEGAN · UNKNOWN; `dietFlagEstimated` means it came from the ingredient list, not the pack's mark. */
    dietFlag: 'VEG' | 'NON_VEG' | 'VEGAN' | 'UNKNOWN';
    dietFlagEstimated: boolean;
  };
  nutrients: NutrientRow[];
  ingredients: Array<{
    rawText: string;
    position: number;
    canonicalName: string | null;
    isAdditive: boolean;
    insNumber: string | null;
    /** INS number linked to the additive reference table, when the label token carried a recognised one. */
    additiveCode: string | null;
    /** Codex name and functional class only — never a safety statement. */
    additiveName: string | null;
    additiveFunction: string | null;
  }>;
  allergens: Array<{ code: string; displayName: string; declarationType: string }>;
  barcodes: Array<{ gtin: string; gtinType: string | null; isPrimary: boolean }>;
  hasOpenConflict: boolean;
}

export type SearchSort =
  | 'RELEVANCE'
  | 'SCORE_DESC'
  | 'SUGAR_ASC'
  | 'ADDED_SUGAR_ASC'
  | 'SODIUM_ASC'
  | 'SAT_FAT_ASC'
  | 'PROTEIN_DESC'
  | 'FIBRE_DESC'
  | 'QUALITY';

/** A phrase the server read out of the query (D-061) — rendered as a chip from locale templates. */
export interface InterpretedTerm {
  type: 'CATEGORY' | 'MAX_NUTRIENT' | 'MIN_NUTRIENT' | 'SORT' | 'DIET' | 'EXCLUDE_ALLERGEN' | 'MAX_NOVA';
  code: string | null;
  value: number | null;
  unit: string | null;
  source: string;
}

export interface SearchRelated {
  kind: 'BRAND' | 'PRODUCT';
  brandName: string | null;
  sameBrand: ProductSummary[];
  categoryCode: string | null;
  betterInCategory: ProductSummary[];
}

export interface SearchResponse {
  items: ProductSummary[];
  total: number;
  page: number;
  size: number;
  /** Search v2 (D-061) — absent from an older backend. */
  sort?: SearchSort;
  residualQuery?: string;
  interpreted?: InterpretedTerm[];
  excludedForMissingData?: number;
  related?: SearchRelated;
  didYouMean?: string;
}

/**
 * Search filters, all per 100 g / 100 ml in the catalogue's units (grams; sodium in mg;
 * energy in kcal). Every value is the person's own choice — never a threshold we set.
 */
export interface SearchFilters {
  category?: string;
  maxAddedSugar?: number;
  maxTotalSugar?: number;
  maxSodium?: number;
  maxSatFat?: number;
  maxEnergy?: number;
  minProtein?: number;
  minFibre?: number;
  excludeAllergens?: string[];
  dietary?: 'VEG' | 'VEGAN';
  maxNova?: number;
}

export const NUMERIC_FILTER_KEYS = [
  'maxTotalSugar',
  'maxAddedSugar',
  'maxSodium',
  'maxSatFat',
  'maxEnergy',
  'minProtein',
  'minFibre',
] as const;
export type NumericFilterKey = (typeof NUMERIC_FILTER_KEYS)[number];

export interface Suggestion {
  kind: 'BRAND' | 'CATEGORY' | 'PRODUCT';
  label: string;
  /** Brand name to search, category code, or product id. */
  target: string;
  secondary: string | null;
}

export interface Category {
  code: string;
  name: string;
  path: string;
  sortOrder: number;
  typicalServingMinG: number | null;
  typicalServingMaxG: number | null;
}

/* ------------------------------------------------------------------ requests */

export function searchProducts(
  params: {
    q?: string;
    page?: number;
    size?: number;
    sort?: SearchSort;
  } & SearchFilters,
): Promise<SearchResponse> {
  const search = new URLSearchParams();
  if (params.q) search.set('q', params.q);
  if (params.category) search.set('category', params.category);
  for (const key of NUMERIC_FILTER_KEYS) {
    const value = params[key];
    if (value != null && Number.isFinite(value)) search.set(key, String(value));
  }
  if (params.excludeAllergens && params.excludeAllergens.length > 0) {
    search.set('excludeAllergens', params.excludeAllergens.join(','));
  }
  if (params.dietary) search.set('dietary', params.dietary);
  if (params.maxNova != null) search.set('maxNova', String(params.maxNova));
  if (params.sort && params.sort !== 'RELEVANCE') search.set('sort', params.sort);
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  return request<SearchResponse>(`/search?${search.toString()}`, { anonymous: true });
}

/** Search-box suggestions for what has been typed (2+ characters). Public. */
export function getSuggestions(q: string): Promise<Suggestion[]> {
  return request<Suggestion[]>(`/search/suggest?q=${encodeURIComponent(q)}`, { anonymous: true });
}

export function getProduct(id: string): Promise<ProductDetail> {
  return request<ProductDetail>(`/products/${encodeURIComponent(id)}`, { anonymous: true });
}

export function getProductByBarcode(gtin: string): Promise<ProductDetail> {
  return request<ProductDetail>(`/products/barcode/${encodeURIComponent(gtin)}`, {
    anonymous: true,
  });
}

export function getCategories(): Promise<Category[]> {
  return request<Category[]>('/reference/categories', { anonymous: true });
}

/* ------------------------------------------------------------------- helpers */

/**
 * Picks the one number worth putting on a card.
 *
 * **Per category, not per product.** Choosing "whichever nutrient this row
 * happens to have" makes a grid of comparable items non-comparable: one noodle
 * pack showing sodium beside another showing protein cannot be ranked by eye,
 * which is the entire job of a results grid. So the axis is fixed by category —
 * sodium for the savoury categories, added sugar for the sweet and drink ones —
 * and every card in that category reports the same measure.
 *
 * The per-category choice is the nutrient that most often decides the result
 * for that kind of food, so the card shows the number a shopper would have
 * opened the product to find.
 *
 * Falls back to a general order only when the category's own nutrient is
 * genuinely absent, and returns `null` rather than reaching for an unrelated
 * figure when nothing useful is declared — an absence is said plainly by the
 * card, not filled with noise.
 */
const SODIUM_FIRST = ['SODIUM_MG', 'SAT_FAT_G', 'ADDED_SUGAR_G'] as const;
const SUGAR_FIRST = ['ADDED_SUGAR_G', 'TOTAL_SUGAR_G', 'SODIUM_MG'] as const;

const CATEGORY_HEADLINE: Record<string, readonly string[]> = {
  INSTANT_NOODLES: SODIUM_FIRST,
  NAMKEEN: SODIUM_FIRST,
  CHIPS: SODIUM_FIRST,
  SAUCES_SPREADS: SODIUM_FIRST,
  STAPLES: SODIUM_FIRST,
  EDIBLE_OIL: ['SAT_FAT_G', 'ENERGY_KCAL'],
  BISCUITS: SUGAR_FIRST,
  CHOCOLATE: SUGAR_FIRST,
  BEVERAGES: SUGAR_FIRST,
  MALT_DRINK: SUGAR_FIRST,
  BREAKFAST_CEREAL: SUGAR_FIRST,
  DAIRY: SUGAR_FIRST,
  PROTEIN_FOODS: ['PROTEIN_G', 'ADDED_SUGAR_G', 'SODIUM_MG'],
};

/** Used when a product has no category, or its category declares nothing. */
const GENERAL_PRIORITY = [
  'ADDED_SUGAR_G',
  'SODIUM_MG',
  'SAT_FAT_G',
  'TOTAL_SUGAR_G',
  'PROTEIN_G',
  'FIBRE_G',
  'ENERGY_KCAL',
] as const;

function headlinePriority(categoryCode: string | null): readonly string[] {
  const preferred = categoryCode ? CATEGORY_HEADLINE[categoryCode] : undefined;
  if (!preferred) return GENERAL_PRIORITY;
  // The category's own nutrients first, then the general order for anything
  // still undeclared, so a card is never blank when a usable figure exists.
  return [...preferred, ...GENERAL_PRIORITY.filter((c) => !preferred.includes(c))];
}

export function headlineFact(
  product: ProductSummary,
): { label: string; value: string } | null {
  const per100Unit = product.packSize?.unit === 'ml' ? 'ml' : 'g';

  for (const code of headlinePriority(product.categoryCode)) {
    const fact = product.keyFacts.find((f) => f.nutrientCode === code);
    if (!fact) continue;

    // A figure that cannot be true is treated exactly like a missing one.
    const perServing = isPlausible(fact.amountPerServing, fact.unit)
      ? fact.amountPerServing
      : null;
    const per100 = isPlausible(fact.amountPer100, fact.unit) ? fact.amountPer100 : null;

    if (perServing != null) {
      return {
        value: formatMeasure(perServing, fact.unit),
        label: `${fact.displayName.toLowerCase()} per serving`,
      };
    }
    if (per100 != null) {
      return {
        value: formatMeasure(per100, fact.unit),
        label: `${fact.displayName.toLowerCase()} per 100 ${per100Unit}`,
      };
    }
  }
  return null;
}

export { formatAmount };

/** True when the label declares an allergen at all — the card shows a marker. */
export function hasDeclaredAllergen(product: ProductDetail): boolean {
  return product.allergens.some((a) => a.declarationType === 'CONTAINS');
}
