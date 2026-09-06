import { request } from './api';

/**
 * The consent contract. Mirrors the backend records field-for-field —
 * see backend/.../consent/api/{ConsentPolicyResponse,ConsentRecordResponse,
 * GrantConsentRequest}.java and backend/.../consent/domain/{ConsentStatus,
 * ConsentMechanism,SubjectType}.java — hand-written for the same reason noted
 * on catalog.ts: no OpenAPI generation yet.
 */

/** Only `ACTIVE` permits processing. Everything else — including no record at
 *  all — means "off". See ConsentStatus.java. */
export type ConsentStatus = 'PENDING' | 'ACTIVE' | 'WITHDRAWN' | 'EXPIRED';

/** How a consent was obtained. GUARDIAN_ATTESTATION is not the DPDP-verifiable
 *  parental consent mechanism — see ConsentMechanism.java's own caveat, echoed
 *  in the UI via `consent.guardianAttestationNote`. */
export type ConsentMechanism = 'SELF_ATTESTATION' | 'GUARDIAN_ATTESTATION';

export type SubjectType = 'ADULT_SELF' | 'CHILD';

/** The current policy version for one active purpose. Public reference data —
 *  `listPolicies` needs no session. */
export interface ConsentPolicy {
  purposeCode: string;
  purposeName: string;
  purposeDescription: string;
  requiresVerifiable: boolean;
  essential: boolean;
  policyVersion: string;
  documentUri: string;
  summary: string;
  effectiveFrom: string;
}

/** One row of a household member's consent ledger. Never carries `evidence` —
 *  the backend deliberately omits it from every listing. */
export interface ConsentRecord {
  id: string;
  subjectMemberId: string;
  subjectType: SubjectType;
  purposeCode: string;
  policyVersionId: string;
  status: ConsentStatus;
  mechanism: ConsentMechanism;
  grantedAt: string | null;
  withdrawnAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

/** Public, unauthenticated: the text a person is shown before granting any purpose. */
export function listPolicies(): Promise<ConsentPolicy[]> {
  return request<ConsentPolicy[]>('/consent/policies', { anonymous: true });
}

/**
 * Grants a purpose for a household member. An adult may only grant for
 * themselves; a child's purpose may only be granted by one of their
 * guardians — enforced server-side. A caller without rights over `memberId`
 * gets a 403 (`ApiError`), which is correct behaviour, not a bug to route
 * around client-side.
 */
export function grant(
  memberId: string,
  purposeCode: string,
  evidence?: Record<string, unknown>,
): Promise<ConsentRecord> {
  return request<ConsentRecord>(`/consent/members/${memberId}/grants`, {
    method: 'POST',
    body: { purposeCode, evidence },
  });
}

/** Idempotent: withdrawing a purpose with no active grant succeeds without effect. */
export function withdraw(memberId: string, purposeCode: string): Promise<void> {
  return request<void>(`/consent/members/${memberId}/grants/${purposeCode}`, {
    method: 'DELETE',
  });
}

/** Every record for the member, any status, in no guaranteed order — callers
 *  must check `status === 'ACTIVE'` per `purposeCode` themselves. */
export function listGrants(memberId: string): Promise<ConsentRecord[]> {
  return request<ConsentRecord[]>(`/consent/members/${memberId}/grants`);
}
