import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { request } from './api';
import type { AgeBand } from './profile';
import type { MetricCategory } from './wellness';
import type { GoalDirection } from './goals';
import { useIsSignedIn } from './useSession';

/**
 * "Who am I in this product?" — `GET /api/v1/me` (D-043, D-046).
 *
 * One call that answers what every signed-in screen otherwise re-derives from the household
 * list, the member list and a profile fetch: the caller's primary household (the oldest active
 * membership), their own member, whether a profile exists, the experience tier and the
 * capability flags that follow from it, and whether health tracking consent is active.
 *
 * The tier and capabilities are server-authoritative (D-046). A screen renders them; it never
 * infers them from an age band itself.
 *
 * `lib/useMe.ts` (the older, multi-request hook) is left as it is: screens can move to
 * {@link useMeQuery} when their owners choose.
 */

/** NONE: no household yet — onboarding. INDIVIDUAL: a household of one. FAMILY: two or more. */
export type MeMode = 'NONE' | 'INDIVIDUAL' | 'FAMILY';

export type ExperienceTier = 'CHILD' | 'ADULT' | 'OLDER_ADULT' | 'UNKNOWN';

/** Self-reported, optional (V14). Never used to compute a calorie target (D-047). */
export type ActivityLevel = 'SEDENTARY' | 'LIGHTLY_ACTIVE' | 'MODERATELY_ACTIVE' | 'VERY_ACTIVE';

export const ACTIVITY_LEVELS: readonly ActivityLevel[] = [
  'SEDENTARY',
  'LIGHTLY_ACTIVE',
  'MODERATELY_ACTIVE',
  'VERY_ACTIVE',
];

/**
 * Mirrors `experience/application/Capabilities.java`. Only capabilities that exist today are
 * listed — there is deliberately no flag for streaks, challenges or notifications.
 */
export interface Capabilities {
  /** False until an age band is known (tier UNKNOWN). */
  canTrack: boolean;
  /** Metric codes this member may have recorded (D-036). */
  trackableMetrics: string[];
  goalCategories: MetricCategory[];
  goalDirections: GoalDirection[];
  habits: boolean;
  /** OLDER_ADULT: reminders, medicines and appointments lead the home. */
  careFirstHome: boolean;
  /** OLDER_ADULT: a larger type size is offered, not imposed. */
  largerTextSuggested: boolean;
  /** OLDER_ADULT: asking a family member for help is surfaced. */
  caregiverInviteProminent: boolean;
}

export interface MeHousehold {
  id: string;
  name: string;
  individual: boolean;
  /** IANA zone, e.g. "Asia/Kolkata". A "day" in this product is a day in this zone. */
  timeZone: string;
  memberCount: number;
}

export interface MeMember {
  id: string;
  displayLabel: string;
  memberType: 'ADULT' | 'CHILD';
  role: 'OWNER' | 'ADULT' | 'DEPENDENT';
}

export interface MeProfile {
  exists: boolean;
  ageBand: AgeBand | null;
  activityLevel: ActivityLevel | null;
}

export interface MeView {
  accountId: string;
  mode: MeMode;
  /** `null` exactly when `mode` is NONE. */
  household: MeHousehold | null;
  /** `null` exactly when `mode` is NONE. */
  member: MeMember | null;
  profile: MeProfile;
  experience: ExperienceTier;
  capabilities: Capabilities;
  /** The caller's own tracking purpose (HEALTH_TRACKING for an adult) and whether it is ACTIVE. */
  trackingConsent: { purposeCode: string; active: boolean };
  /** Households beyond the primary one. There is no switcher; this only lets a screen say so. */
  otherHouseholdCount: number;
}

/** `GET /members/{id}/experience`. The tier is coarse (CHILD/ADULT) unless the caller is the
 *  member, their guardian, or a caregiver they chose. */
export interface MemberExperience {
  memberId: string;
  tier: ExperienceTier;
  capabilities: Capabilities;
}

// --------------------------------------------------------------------- requests

/** Invalidate after anything that changes membership, profile presence or tracking consent. */
export const ME_QUERY_KEY = ['me'] as const;

export function getMe(): Promise<MeView> {
  return request<MeView>('/me');
}

/**
 * "Just me": provisions a household of one (D-043). Idempotent — 201 with a new household, or
 * 200 with the caller's existing one; never a second household. Either way the response is the
 * caller's up-to-date {@link MeView}.
 */
export function provisionIndividual(displayLabel: string): Promise<MeView> {
  return request<MeView>('/me/individual', { method: 'POST', body: { displayLabel } });
}

export function getMemberExperience(memberId: string): Promise<MemberExperience> {
  return request<MemberExperience>(`/members/${encodeURIComponent(memberId)}/experience`);
}

/**
 * The caller's {@link MeView}, cached under {@link ME_QUERY_KEY}. Never fires while signed out,
 * so it is safe on a screen reachable without a session. Pass `enabled: false` to hold it back
 * for other reasons.
 */
export function useMeQuery(options: { enabled?: boolean } = {}): UseQueryResult<MeView> {
  const signedIn = useIsSignedIn();
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
    enabled: signedIn && (options.enabled ?? true),
  });
}
