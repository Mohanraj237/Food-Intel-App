import type { MeView } from '@/lib/me';

/**
 * The onboarding sequence (D-043), as data, so where a person resumes is decided in one tested
 * place rather than by whichever screen they happen to land on.
 *
 *   MODE     "Just me" or "Me and my family"          (only when there is no household yet)
 *   NAME     your name, and a household name for a family
 *   PROFILE  age band, sex, activity level             (until a profile exists)
 *   CONSENT  health-tracking consent, or "not now"     (until it is active)
 *   DONE     nothing left to ask
 */
export type OnboardingStep = 'MODE' | 'NAME' | 'PROFILE' | 'CONSENT' | 'DONE';

/** Every step a person can be shown, in order. */
const ORDER: readonly OnboardingStep[] = ['MODE', 'NAME', 'PROFILE', 'CONSENT'];

/**
 * The first step this person still needs, from the server's view of them. NAME is never a
 * resume point: it only follows a choice made on MODE in this visit.
 *
 * A person who already has a household (an invitation accepted, a household made on another
 * device, "Just me" chosen before) is never asked to choose again — that is how a second
 * household would get made by accident.
 */
export function resolveOnboardingStep(me: MeView): Exclude<OnboardingStep, 'NAME'> {
  if (me.mode === 'NONE' || me.household === null || me.member === null) {
    return 'MODE';
  }
  if (!me.profile.exists || me.profile.ageBand === null) {
    return 'PROFILE';
  }
  if (!me.trackingConsent.active) {
    return 'CONSENT';
  }
  return 'DONE';
}

/**
 * The step after saving the profile. Consent is only asked when it is not already on.
 */
export function stepAfterProfile(me: Pick<MeView, 'trackingConsent'>): 'CONSENT' | 'DONE' {
  return me.trackingConsent.active ? 'DONE' : 'CONSENT';
}

/**
 * "Step 2 of 4", counted from where this visit started — a person resuming at the consent
 * step has one step, not "step 4 of 4". `null` when there is nothing worth counting (a single
 * step, or DONE).
 */
export function onboardingProgress(
  start: OnboardingStep,
  current: OnboardingStep,
): { current: number; total: number } | null {
  if (current === 'DONE' || start === 'DONE') return null;
  const startIndex = ORDER.indexOf(start);
  const currentIndex = ORDER.indexOf(current);
  if (startIndex === -1 || currentIndex === -1 || currentIndex < startIndex) return null;
  const total = ORDER.length - startIndex;
  if (total <= 1) return null;
  return { current: currentIndex - startIndex + 1, total };
}
