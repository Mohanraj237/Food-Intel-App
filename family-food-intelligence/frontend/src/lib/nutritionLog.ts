import { request } from './api';

/**
 * The meal-logging contract (D-039, D-041). Mirrors `backend/.../nutrition/api/*.java`.
 *
 * Named `nutritionLog` rather than `nutrition` because `lib/nutrition.ts` already holds the
 * plausibility and formatting rules for catalogue values, and the two are different things:
 * that module decides whether a label's number can be trusted, this one records what a
 * household ate.
 *
 * The field to get right on the client is `inMemberLog`. A portion apportioned to another
 * adult is stored but not written into their record until they confirm it (D-041). A screen
 * that showed every portion as "logged" would tell the logger something false about someone
 * else's health record.
 */

export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';

export const MEAL_TYPES: MealType[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'];

/**
 * Nutrition amounts. Every field is nullable, and null means "not known" — never zero.
 * A meal of home-cooked food nobody has values for is a normal, valid entry.
 */
export interface NutritionAmounts {
  energyKcal: number | null;
  proteinG: number | null;
  carbohydrateG: number | null;
  fatG: number | null;
  fibreG: number | null;
  sugarG: number | null;
  sodiumMg: number | null;
}

export interface MealItemInput {
  /** Exactly one of productId / customName. */
  productId?: string;
  customName?: string;
  quantityG: number;
  /** Required for a custom item; ignored for a catalogue product, whose label is used. */
  nutritionPer100?: Partial<NutritionAmounts>;
}

export interface MealPortionInput {
  householdMemberId: string;
  /** Share of the whole meal: 1 = all of it, 0.5 = half. Need not sum to 1 across a meal. */
  portionFraction: number;
}

export interface MealInput {
  householdId: string;
  mealType: MealType;
  name?: string;
  consumedAt: string;
  items: MealItemInput[];
  portions: MealPortionInput[];
}

export interface MealItem {
  id: string;
  productId: string | null;
  productVersionId: string | null;
  customName: string | null;
  quantityG: number;
  nutrition: NutritionAmounts;
}

export interface MealPortion {
  id: string;
  householdMemberId: string;
  portionFraction: number;
  nutrition: NutritionAmounts;
  /** False while another adult has yet to confirm their share (D-041). */
  inMemberLog: boolean;
  /**
   * True when `nutrition` is withheld from this reader because the reader may not see this
   * member's NUTRITION data (D-044) — confirmed against `MealPortionResponse.java`. Distinct
   * from "not known": a withheld share may have real values the member simply has not shared,
   * so a reader for whom this is `true` must not render `nutrition` at all, not even as zero.
   */
  nutritionWithheld: boolean;
}

export interface Meal {
  id: string;
  householdId: string;
  mealType: MealType;
  name: string | null;
  consumedAt: string;
  totalNutrition: NutritionAmounts;
  items: MealItem[];
  portions: MealPortion[];
  /** The only member who may edit or delete this meal (D-041). */
  loggedByUserId: string;
}

export function logMeal(input: MealInput): Promise<Meal> {
  return request<Meal>('/nutrition/meals', { method: 'POST', body: input });
}

export function updateMeal(mealId: string, input: Omit<MealInput, 'householdId'>): Promise<Meal> {
  return request<Meal>(`/nutrition/meals/${encodeURIComponent(mealId)}`, { method: 'PUT', body: input });
}

export function deleteMeal(mealId: string): Promise<void> {
  return request<void>(`/nutrition/meals/${encodeURIComponent(mealId)}`, { method: 'DELETE' });
}

export function getMeal(mealId: string): Promise<Meal> {
  return request<Meal>(`/nutrition/meals/${encodeURIComponent(mealId)}`);
}

/** An adult adds their own share of a household meal to their own log (D-041). */
export function confirmMealPortion(mealId: string): Promise<Meal> {
  return request<Meal>(`/nutrition/meals/${encodeURIComponent(mealId)}/confirm`, { method: 'POST' });
}

export function listHouseholdMeals(
  householdId: string,
  params: { from?: string; to?: string } = {},
): Promise<Meal[]> {
  const search = new URLSearchParams({ householdId });
  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  return request<Meal[]>(`/nutrition/meals?${search.toString()}`);
}

export function listMemberMeals(
  memberId: string,
  params: { from?: string; to?: string } = {},
): Promise<Meal[]> {
  const search = new URLSearchParams();
  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  const query = search.toString();
  return request<Meal[]>(
    `/nutrition/members/${encodeURIComponent(memberId)}/meals${query ? `?${query}` : ''}`,
  );
}
