import { request } from './api';

/**
 * The household contract.
 *
 * Mirrors `HouseholdController` and its DTOs exactly (see
 * `backend/src/main/java/com/familyfood/household/api/*.java`). `GET
 * /households` lists every household the signed-in caller currently belongs
 * to, oldest membership first. A person almost always belongs to zero or one
 * household; the rare multi-household case has no "switch household" UI, so
 * `useMyHousehold` (`lib/useMyHousehold.ts`) deterministically treats the
 * first (oldest) entry as the account's household.
 */

export interface Household {
  id: string;
  name: string;
  createdByUserId: string;
  createdAt: string;
}

/** `userAccountId` is `null` for a CHILD member — that is structural, not an omission. */
export interface HouseholdMember {
  id: string;
  householdId: string;
  userAccountId: string | null;
  memberType: 'ADULT' | 'CHILD';
  role: 'OWNER' | 'ADULT' | 'DEPENDENT';
  displayLabel: string;
  joinedAt: string;
}

/**
 * The raw invitation token, returned exactly once by the server — it is never
 * stored server-side and can never be retrieved again after this response.
 */
export interface InvitationIssued {
  invitationId: string;
  householdId: string;
  token: string;
  expiresAt: string;
}

export type GuardianRelationship = 'PARENT' | 'LEGAL_GUARDIAN';

/** One row of `GET /households` — the caller's own membership in a household they belong to. */
export interface HouseholdSummary {
  householdId: string;
  householdName: string;
  memberId: string;
  role: 'OWNER' | 'ADULT' | 'DEPENDENT';
  displayLabel: string;
  joinedAt: string;
}

// ------------------------------------------------------------------- requests

/** Every household the signed-in caller currently belongs to, oldest membership first. */
export function listMyHouseholds(): Promise<HouseholdSummary[]> {
  return request<HouseholdSummary[]>('/households');
}

export function createHousehold(name: string, ownerDisplayLabel: string): Promise<Household> {
  return request<Household>('/households', {
    method: 'POST',
    body: { name, ownerDisplayLabel },
  });
}

export function listMembers(householdId: string): Promise<HouseholdMember[]> {
  return request<HouseholdMember[]>(`/households/${householdId}/members`);
}

/** Creates only an invitation (D-002) — no member row exists until it is accepted. */
export function inviteAdult(householdId: string, email: string): Promise<InvitationIssued> {
  return request<InvitationIssued>(`/households/${householdId}/members`, {
    method: 'POST',
    body: { memberType: 'ADULT', email },
  });
}

/** Creates a dependent member and a guardian link from the caller, immediately. */
export function addChild(
  householdId: string,
  displayLabel: string,
  relationship: GuardianRelationship,
  authorityBasis: string,
): Promise<HouseholdMember> {
  return request<HouseholdMember>(`/households/${householdId}/members`, {
    method: 'POST',
    body: { memberType: 'CHILD', displayLabel, relationship, authorityBasis },
  });
}

/** The OWNER may remove any other member. Any member may remove themselves. */
export function removeMember(householdId: string, memberId: string): Promise<void> {
  return request<void>(`/households/${householdId}/members/${memberId}`, {
    method: 'DELETE',
  });
}

/**
 * Erases a child's data (CH-008/FR-010) — distinct from {@link removeMember}, which only
 * ends the membership. Only an active guardian of this specific child may call this; the
 * backend is the sole authority on that (see `DataRightsController`).
 */
export function eraseChildMember(householdId: string, memberId: string): Promise<void> {
  return request<void>(`/households/${householdId}/members/${memberId}/erase`, {
    method: 'POST',
  });
}

export function acceptInvitation(token: string, displayLabel: string): Promise<HouseholdMember> {
  return request<HouseholdMember>('/households/invitations/accept', {
    method: 'POST',
    body: { token, displayLabel },
  });
}

export function revokeInvitation(householdId: string, invitationId: string): Promise<void> {
  return request<void>(`/households/${householdId}/invitations/${invitationId}/revoke`, {
    method: 'POST',
  });
}
