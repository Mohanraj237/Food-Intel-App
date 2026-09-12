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
  };
  nutrients: NutrientRow[];
  ingredients: Array<{
    rawText: string;
    position: number;
    canonicalName: string | null;
    isAdditive: boolean;
    insNumber: string | null;
  }>;
  allergens: Array<{ code: string; displayName: string; declarationType: string }>;
  barcodes: Array<{ gtin: string; gtinType: string | null; isPrimary: boolean }>;
  hasOpenConflict: boolean;
}

export interface SearchResponse {
  items: ProductSummary[];
  total: number;
  page: number;
  size: number;
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

export function searchProducts(params: {
  q?: string;
  category?: string;
  page?: number;
  size?: number;
}): Promise<SearchResponse> {
  const search = new URLSearchParams();
  if (params.q) search.set('q', params.q);
  if (params.category) search.set('category', params.category);
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  return request<SearchResponse>(`/search?${search.toString()}`, { anonymous: true });
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
