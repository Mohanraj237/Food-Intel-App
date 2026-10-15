import type { HouseholdMember } from '@/lib/household';
import type { AccessGrant, AccessLevel, SharingScope } from '@/lib/access';
import { getLocale } from '@/lib/i18n';

/**
 * Pure rules for the "Share with someone" flow.
 *
 * These only decide what is worth *offering*. The server is the authority on what is allowed
 * (D-044): an audience must be an active ADULT member of the same household with their own
 * account, never yourself, never a child. Offering exactly that set here means a person is not
 * shown a choice that could only ever be refused.
 */

function byLabel(a: HouseholdMember, b: HouseholdMember): number {
  return a.displayLabel.localeCompare(b.displayLabel, getLocale(), { sensitivity: 'base' }) || a.id.localeCompare(b.id);
}

/** Adults in the household, other than you, who have their own account. */
export function eligibleAudiences(members: readonly HouseholdMember[], myMemberId: string): HouseholdMember[] {
  return members
    .filter((m) => m.id !== myMemberId && m.memberType === 'ADULT' && m.userAccountId !== null)
    .sort(byLabel);
}

/** Children in the household. Never an audience; listed only so the screen can say why. */
export function childMembers(members: readonly HouseholdMember[]): HouseholdMember[] {
  return members.filter((m) => m.memberType === 'CHILD').sort(byLabel);
}

/** What you already share with one person, scope → level (live grants only). */
export function sharedWith(grants: readonly AccessGrant[], audienceMemberId: string): Map<string, AccessLevel> {
  const shared = new Map<string, AccessLevel>();
  for (const grant of grants) {
    if (grant.audienceMemberId === audienceMemberId && (grant.revokedAt ?? null) === null) {
      shared.set(grant.scope, grant.level);
    }
  }
  return shared;
}

export interface ScopeChoice {
  scope: SharingScope;
  /** Already shared with this person at some level — changed from the list, not re-shared here. */
  alreadyShared: boolean;
}

/** The offered scopes, each marked with whether this person already has it. */
export function scopeChoices(
  offered: readonly SharingScope[],
  grants: readonly AccessGrant[],
  audienceMemberId: string,
): ScopeChoice[] {
  const shared = sharedWith(grants, audienceMemberId);
  return offered.map((scope) => ({ scope, alreadyShared: shared.has(scope) }));
}

export type ShareStep = 'PERSON' | 'SCOPES' | 'LEVEL' | 'REVIEW';

/** The steps of one run of the flow. Starting from a person's card skips choosing the person. */
export function shareSteps(personPreselected: boolean): ShareStep[] {
  return personPreselected ? ['SCOPES', 'LEVEL', 'REVIEW'] : ['PERSON', 'SCOPES', 'LEVEL', 'REVIEW'];
}

/** Why the current step cannot move on, or `null` when it can. */
export function shareStepBlocker(
  step: ShareStep,
  state: { audienceId: string | null; scopes: ReadonlySet<string> },
): 'PERSON_REQUIRED' | 'SCOPES_REQUIRED' | null {
  if (step === 'PERSON' && state.audienceId === null) return 'PERSON_REQUIRED';
  if (step === 'SCOPES' && state.scopes.size === 0) return 'SCOPES_REQUIRED';
  return null;
}
