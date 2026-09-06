import { request } from './api';

/**
 * The contribution contract.
 *
 * Mirrors `POST /api/v1/contributions` exactly (a parallel backend workstream
 * is building that endpoint against this same shape). A contribution is never
 * auto-published — it lands in a review queue and a human checks it before it
 * can appear in search (see `ContributeProductForm`), so nothing on the
 * frontend decides what the product means; it only records what a person read
 * off a pack. Requires sign-in, same as `createHousehold` in `./household.ts`.
 */

export interface ContributionNutrientInput {
  nutrientCode: string;
  amountPer100: number | null;
  amountPerServing: number | null;
}

export interface ContributionRequest {
  gtin: string | null;
  productName: string;
  brandName: string | null;
  categoryCode: string | null;
  packSizeValue: number | null;
  packSizeUnit: string | null;
  servingDeclaredText: string | null;
  nutrients: ContributionNutrientInput[];
  ingredientsRawText: string | null;
  allergenCodes: string[];
}

export interface ContributionReceipt {
  id: string;
  status: string;
  submittedAt: string;
}

export function submitContribution(payload: ContributionRequest): Promise<ContributionReceipt> {
  return request<ContributionReceipt>('/contributions', {
    method: 'POST',
    body: payload,
  });
}
