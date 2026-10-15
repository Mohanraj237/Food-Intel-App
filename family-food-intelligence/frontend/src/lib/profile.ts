import { request } from './api';

/**
 * The profile contract.
 *
 * Mirrors `ProfileController`, `ProfileRequest` and `ProfileResponse` exactly
 * (see `backend/src/main/java/com/familyfood/profile/api/*.java` and the
 * closed enums under `backend/src/main/java/com/familyfood/profile/domain/`).
 * Every enum here is a closed vocabulary on the backend — there is no code
 * path, here or there, that accepts a free-text goal or allergen.
 *
 * `GET` returns 404 (`RESOURCE_NOT_FOUND`) when the member simply has no
 * profile yet. That is not an error state — see `getProfile` callers, which
 * should treat a 404 as "render an empty form", not `ErrorState`.
 */

export type AgeBand =
  | 'A18_30'
  | 'A31_45'
  | 'A46_60'
  | 'A60_PLUS'
  | 'C1_3'
  | 'C4_6'
  | 'C7_9'
  | 'C10_12'
  | 'C13_17';

export const ADULT_AGE_BANDS: AgeBand[] = ['A18_30', 'A31_45', 'A46_60', 'A60_PLUS'];
export const CHILD_AGE_BANDS: AgeBand[] = ['C1_3', 'C4_6', 'C7_9', 'C10_12', 'C13_17'];

export type Sex = 'FEMALE' | 'MALE' | 'OTHER' | 'PREFER_NOT_TO_SAY';

export const SEX_OPTIONS: Sex[] = ['FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY'];

export type DietaryPreference = 'VEG' | 'NON_VEG' | 'EGGETARIAN' | 'VEGAN' | 'JAIN' | 'NO_PREFERENCE';

export const DIETARY_PREFERENCE_OPTIONS: DietaryPreference[] = [
  'VEG',
  'NON_VEG',
  'EGGETARIAN',
  'VEGAN',
  'JAIN',
  'NO_PREFERENCE',
];

export type GoalCode =
  | 'REDUCE_ADDED_SUGAR'
  | 'REDUCE_SODIUM'
  | 'REDUCE_SATURATED_FAT'
  | 'INCREASE_FIBRE'
  | 'INCREASE_PROTEIN'
  | 'AVOID_TRANS_FAT'
  | 'GENERAL_BALANCED_EATING';

export const GOAL_CODES: GoalCode[] = [
  'REDUCE_ADDED_SUGAR',
  'REDUCE_SODIUM',
  'REDUCE_SATURATED_FAT',
  'INCREASE_FIBRE',
  'INCREASE_PROTEIN',
  'AVOID_TRANS_FAT',
  'GENERAL_BALANCED_EATING',
];

export type AllergenCode =
  | 'MILK'
  | 'EGG'
  | 'FISH'
  | 'CRUSTACEAN'
  | 'TREE_NUT'
  | 'PEANUT'
  | 'WHEAT'
  | 'GLUTEN'
  | 'SOY'
  | 'SESAME'
  | 'SULPHITE'
  | 'MUSTARD';

export const ALLERGEN_CODES: AllergenCode[] = [
  'MILK',
  'EGG',
  'FISH',
  'CRUSTACEAN',
  'TREE_NUT',
  'PEANUT',
  'WHEAT',
  'GLUTEN',
  'SOY',
  'SESAME',
  'SULPHITE',
  'MUSTARD',
];

/**
 * The full desired state of a profile version — a replace, not a patch. Every
 * PUT writes a brand-new version carrying exactly these fields.
 */
export interface ProfileRequest {
  ageBand: AgeBand;
  sex: Sex | null;
  dietaryPreference: DietaryPreference | null;
  goals: GoalCode[];
  allergens: AllergenCode[];
  conditions: string[];
  /**
   * Self-reported (V14). Optional in the type only so older callers still compile — but a
   * PUT is a replace, so omitting it clears a stored value exactly as sending `null` does.
   * A caller editing an existing profile should carry the current value through.
   */
  activityLevel?: 'SEDENTARY' | 'LIGHTLY_ACTIVE' | 'MODERATELY_ACTIVE' | 'VERY_ACTIVE' | null;
}

export interface Profile {
  id: string;
  householdMemberId: string;
  version: number;
  ageBand: AgeBand;
  sex: Sex | null;
  dietaryPreference: DietaryPreference | null;
  goals: GoalCode[];
  allergens: AllergenCode[];
  conditions: string[];
  createdAt: string;
  /**
   * Self-reported (V14), never used to compute a calorie target (D-047). Absent rather than
   * `null` when unset, because the API omits null fields on this response.
   */
  activityLevel?: 'SEDENTARY' | 'LIGHTLY_ACTIVE' | 'MODERATELY_ACTIVE' | 'VERY_ACTIVE' | null;
}

/**
 * Returns the current profile version. An adult may read only their own
 * profile; a child's profile may be read by any of their guardians. Rejects
 * with a 404 `ApiError` when the member has no profile yet — callers must
 * treat that as an empty-form state, not a real error.
 */
export function getProfile(householdId: string, memberId: string): Promise<Profile> {
  return request<Profile>(`/households/${householdId}/members/${memberId}/profile`);
}

/**
 * Creates or replaces the current profile version. The previous current
 * version, if any, is superseded — nothing is overwritten in place.
 */
export function putProfile(
  householdId: string,
  memberId: string,
  body: ProfileRequest,
): Promise<Profile> {
  return request<Profile>(`/households/${householdId}/members/${memberId}/profile`, {
    method: 'PUT',
    body,
  });
}
