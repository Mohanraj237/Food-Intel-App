import { request } from './api';

/**
 * The data-rights contract. Mirrors `DataRightsController` and its DTOs exactly
 * (see `backend/.../datarights/api/*.java`).
 */

export interface AccountSummary {
  accountId: string;
  email: string;
  createdAt: string;
  status: string;
}

export interface ProfileVersionExport {
  id: string;
  householdMemberId: string;
  version: number;
  ageBand: string;
  sex: string | null;
  dietaryPreference: string | null;
  goals: string[];
  allergens: string[];
  conditions: string[];
  createdAt: string;
  createdByUserId: string;
}

export interface ConsentExport {
  id: string;
  subjectMemberId: string;
  subjectType: string;
  purposeCode: string;
  policyVersionId: string;
  status: string;
  mechanism: string;
  evidence: Record<string, unknown>;
  grantedAt: string | null;
  withdrawnAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

export interface ChildExport {
  memberId: string;
  displayLabel: string;
  joinedAt: string;
  profileVersions: ProfileVersionExport[];
  consent: ConsentExport[];
}

export interface HouseholdExport {
  householdId: string;
  householdName: string;
  memberId: string;
  role: string;
  displayLabel: string;
  joinedAt: string;
  profileVersions: ProfileVersionExport[];
  consent: ConsentExport[];
  guardedChildren: ChildExport[];
}

export interface ContributionExport {
  id: string;
  productName: string;
  status: string;
  submittedAt: string;
}

export interface AccountExport {
  account: AccountSummary;
  households: HouseholdExport[];
  contributions: ContributionExport[];
  exportedAt: string;
}

/** Every household the caller belongs to, their own profile/consent history, every child
 *  they currently guard, and their own contributions. Never another person's data. */
export function exportMyData(): Promise<AccountExport> {
  return request<AccountExport>('/account/export');
}

export interface AccountDeletionResult {
  status: string;
  erasureEffectiveAt: string;
}

/** Immediate: blocks sign-in and ends every session right away. There is no cancellation. */
export function requestAccountDeletion(): Promise<AccountDeletionResult> {
  return request<AccountDeletionResult>('/account/deletion', { method: 'POST' });
}
