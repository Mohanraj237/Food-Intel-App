import { request } from './api';

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
 * Order matters and is not arbitrary: added sugar and sodium are the two drivers
 * behind most `LIMIT` results for the metabolic-health cohort this product is
 * built for, so they lead. Energy is the fallback only because it is the fact
 * most reliably present, not because it is the most useful.
 *
 * Prefers the per-serving figure, which is the quantity a person actually eats,
 * and says which basis it used rather than leaving it ambiguous.
 */
const HEADLINE_PRIORITY = [
  'ADDED_SUGAR_G',
  'SODIUM_MG',
  'SAT_FAT_G',
  'TOTAL_SUGAR_G',
  'PROTEIN_G',
  'FIBRE_G',
  'ENERGY_KCAL',
] as const;

export function headlineFact(
  product: ProductSummary,
): { label: string; value: string } | null {
  for (const code of HEADLINE_PRIORITY) {
    const fact = product.keyFacts.find((f) => f.nutrientCode === code);
    if (!fact) continue;

    const perServing = fact.amountPerServing;
    const per100 = fact.amountPer100;

    if (perServing != null) {
      return {
        value: `${formatAmount(perServing)}${fact.unit}`,
        label: `${fact.displayName.toLowerCase()} per serving`,
      };
    }
    if (per100 != null) {
      return {
        value: `${formatAmount(per100)}${fact.unit}`,
        label: `${fact.displayName.toLowerCase()} per 100${
          product.packSize?.unit === 'ml' ? 'ml' : 'g'
        }`,
      };
    }
  }
  return null;
}

export function formatAmount(value: number): string {
  if (value >= 100) return String(Math.round(value));
  if (Number.isInteger(value)) return String(value);
  return value.toFixed(1);
}

/** True when the label declares an allergen at all — the card shows a marker. */
export function hasDeclaredAllergen(product: ProductDetail): boolean {
  return product.allergens.some((a) => a.declarationType === 'CONTAINS');
}
