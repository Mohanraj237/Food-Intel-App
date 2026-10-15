import { ApiError, request } from '@/lib/api';
import { getProfile, type AgeBand, type Profile, type ProfileRequest, type Sex } from '@/lib/profile';
import type { ActivityLevel } from '@/lib/me';

/**
 * The three profile answers onboarding asks for, written through the existing profile
 * endpoint.
 *
 * `lib/profile.ts` predates `activityLevel` (added to the backend's ProfileRequest/Response in
 * V14) and is owned elsewhere, so the field is added here, in a thin wrapper, rather than by
 * editing that file.
 *
 * A profile PUT is a replace, not a patch: every field not sent is cleared. So the request is
 * always built from the current profile, with only these three answers changed — onboarding
 * must never wipe allergens or a dietary preference someone already recorded.
 */

export interface ProfileBasics {
  ageBand: AgeBand;
  sex: Sex | null;
  activityLevel: ActivityLevel | null;
}

/** What the server returns since V14. `activityLevel` is absent on an older response. */
export type ProfileWithActivity = Profile & { activityLevel?: ActivityLevel | null };

export type ProfileWithActivityRequest = ProfileRequest & { activityLevel: ActivityLevel | null };

/** The full replacement body: the current profile (if any) with the basics applied. */
export function buildProfileRequest(
  existing: ProfileWithActivity | null,
  basics: ProfileBasics,
): ProfileWithActivityRequest {
  return {
    ageBand: basics.ageBand,
    sex: basics.sex,
    dietaryPreference: existing?.dietaryPreference ?? null,
    goals: existing ? [...existing.goals] : [],
    allergens: existing ? [...existing.allergens] : [],
    conditions: existing ? [...existing.conditions] : [],
    activityLevel: basics.activityLevel,
  };
}

/** The current profile, or `null` when there is none yet (a 404 is that state, not an error). */
export async function fetchExistingProfile(
  householdId: string,
  memberId: string,
): Promise<ProfileWithActivity | null> {
  try {
    return (await getProfile(householdId, memberId)) as ProfileWithActivity;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export function putProfileWithActivity(
  householdId: string,
  memberId: string,
  body: ProfileWithActivityRequest,
): Promise<ProfileWithActivity> {
  return request<ProfileWithActivity>(
    `/households/${encodeURIComponent(householdId)}/members/${encodeURIComponent(memberId)}/profile`,
    { method: 'PUT', body },
  );
}
