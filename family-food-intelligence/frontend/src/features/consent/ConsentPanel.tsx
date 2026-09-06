import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useAccountId, useIsSignedIn } from '@/lib/useSession';
import { MY_HOUSEHOLDS_QUERY_KEY, useMyHousehold } from '@/lib/useMyHousehold';
import { listMembers, type HouseholdMember } from '@/lib/household';
import {
  listPolicies,
  listGrants,
  grant as grantConsent,
  withdraw as withdrawConsent,
  type ConsentPolicy,
  type ConsentRecord,
} from '@/lib/consent';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import './ConsentPanel.css';

/**
 * Self-contained "your privacy choices" panel, meant to be dropped into a
 * settings-style screen. Takes no props: it resolves the caller's household
 * and account from `@/lib/useMyHousehold`/`@/lib/household` and
 * `@/lib/useSession` itself. Only ever mounted while signed in (`SettingsScreen`
 * gates it), so `useMyHousehold` fetches unconditionally here.
 *
 * The backend is the sole authority on who may grant or withdraw what
 * (D-001, D-002) — this panel never computes eligibility beyond deciding
 * which controls are even worth rendering (an adult only edits their own
 * non-essential purposes; a guardian's child toggle is always attempted and
 * lets the server's 403 speak for itself if the caller turns out not to be a
 * guardian, since the frontend has no way to know that in advance).
 */
export function ConsentPanel() {
  const accountId = useAccountId();
  const queryClient = useQueryClient();
  const myHousehold = useMyHousehold();
  const householdId = myHousehold.householdId;

  const policiesQuery = useQuery({
    queryKey: ['consent', 'policies'],
    queryFn: listPolicies,
  });

  const membersQuery = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: householdId !== null,
  });

  // Stable identity: passed into `StaleHousehold`'s mount effect below — a new
  // function each render would re-fire that effect on every unrelated
  // re-render, invalidating the query in a loop.
  const handleStale = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: MY_HOUSEHOLDS_QUERY_KEY });
  }, [queryClient]);

  return (
    <section className="consent-panel" aria-labelledby="consent-panel-heading">
      <h2 id="consent-panel-heading" className="consent-panel__heading">
        {t('consent.heading')}
      </h2>

      {myHousehold.isPending || policiesQuery.isPending ? (
        <Skeleton lines={4} label={t('loading.default')} />
      ) : myHousehold.isError ? (
        <ErrorState error={myHousehold.error} onRetry={() => void myHousehold.refetch()} />
      ) : policiesQuery.isError ? (
        <ErrorState error={policiesQuery.error} onRetry={() => void policiesQuery.refetch()} />
      ) : householdId === null ? (
        <NoHousehold />
      ) : membersQuery.isPending ? (
        <Skeleton lines={4} label={t('loading.default')} />
      ) : membersQuery.isError ? (
        isStaleHouseholdError(membersQuery.error) ? (
          <StaleHousehold onStale={handleStale} />
        ) : (
          <ErrorState error={membersQuery.error} onRetry={() => void membersQuery.refetch()} />
        )
      ) : membersQuery.data.length === 0 ? (
        <NoHousehold />
      ) : (
        <div className="consent-panel__members">
          {membersQuery.data.map((member) => (
            <MemberConsentSection
              key={member.id}
              member={member}
              policies={policiesQuery.data}
              isSelf={accountId !== null && member.userAccountId === accountId}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function isStaleHouseholdError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'NOT_HOUSEHOLD_MEMBER';
}

function NoHousehold() {
  const signedIn = useIsSignedIn();
  return (
    <div className="consent-panel__empty">
      <p className="consent-panel__empty-text">
        {signedIn ? t('household.createExplainer') : t('household.signInRequired')}
      </p>
      <Link to="/family" className="btn btn--secondary btn--sm">
        {t('nav.family')}
      </Link>
    </div>
  );
}

/**
 * The caller's household (`useMyHousehold`) turned out to be stale — a 403
 * `NOT_HOUSEHOLD_MEMBER` fetching its members, most likely because the
 * caller's membership was removed elsewhere (another tab, another device).
 * Retrying the same request would just 403 again, so this is a distinct state
 * from a transient `ErrorState`, not a variant of it. Invalidating the
 * `['households', 'mine']` query means the next resolution reflects reality —
 * either a different household, or none — instead of looping on the same 403.
 */
function StaleHousehold({ onStale }: { onStale: () => void }) {
  useEffect(() => {
    onStale();
  }, [onStale]);
  return (
    <div className="consent-panel__empty">
      <p className="consent-panel__empty-text">{t('household.staleNotice')}</p>
      <Link to="/family" className="btn btn--secondary btn--sm">
        {t('nav.family')}
      </Link>
    </div>
  );
}

function MemberConsentSection({
  member,
  policies,
  isSelf,
}: {
  member: HouseholdMember;
  policies: ConsentPolicy[];
  isSelf: boolean;
}) {
  const isChild = member.memberType === 'CHILD';
  // Grants for another adult 403 server-side (ConsentService.authorize allows
  // only the subject themselves for an ADULT subject) — there is no point
  // asking for a response we already know is a denial. A child's grants are
  // always worth asking for: the frontend cannot tell in advance which
  // household adults are that child's guardians, and the backend is the
  // correct place for that answer to come from.
  const shouldFetchGrants = isSelf || isChild;

  const grantsQuery = useQuery({
    queryKey: ['consent', 'grants', member.id],
    queryFn: () => listGrants(member.id),
    enabled: shouldFetchGrants,
  });

  const essentialPurposes = policies.filter((p) => p.essential);
  const adultPurposes = policies.filter((p) => !p.essential && !p.requiresVerifiable);
  const childPurposes = policies.filter((p) => !p.essential && p.requiresVerifiable);

  return (
    <section className="consent-panel__member" aria-labelledby={`consent-member-${member.id}`}>
      <header className="consent-panel__member-header">
        <h3 id={`consent-member-${member.id}`} className="consent-panel__member-name">
          {member.displayLabel}
        </h3>
        <span className="consent-panel__member-meta">
          {isSelf && <span className="consent-panel__you">{t('household.you')}</span>}
          <span className="consent-panel__role">
            {t(`household.role.${member.role}` as MessageKey)}
          </span>
        </span>
      </header>

      {!isChild && essentialPurposes.map((policy) => (
        <EssentialRow key={policy.purposeCode} policy={policy} />
      ))}

      {/* Another adult's non-essential purposes (PERSONAL_FIT etc.) are
          deliberately not rendered here: ConsentService.authorize denies even
          reading another adult's grants (403), so there is nothing honest to
          show beyond silence, and no control that would only ever 403. */}

      {!isChild && isSelf && (
        <GrantsDependentRows
          grantsQuery={grantsQuery}
          render={(grants) =>
            adultPurposes.map((policy) => (
              <AdultToggleRow
                key={policy.purposeCode}
                policy={policy}
                member={member}
                grants={grants}
              />
            ))
          }
        />
      )}

      {isChild && childPurposes.length > 0 && (
        <GrantsDependentRows
          grantsQuery={grantsQuery}
          render={(grants) =>
            childPurposes.map((policy) => (
              <ChildToggleRow
                key={policy.purposeCode}
                policy={policy}
                member={member}
                grants={grants}
              />
            ))
          }
        />
      )}
    </section>
  );
}

/** Loading/error/data plumbing shared by the two toggle-row groups so each
 *  member's grants fetch gets its own honest skeleton/error/retry rather than
 *  silently rendering nothing while pending or failed. */
function GrantsDependentRows({
  grantsQuery,
  render,
}: {
  grantsQuery: ReturnType<typeof useQuery<ConsentRecord[]>>;
  render: (grants: ConsentRecord[]) => React.ReactNode;
}) {
  if (grantsQuery.isPending) {
    return <Skeleton lines={2} label={t('loading.default')} />;
  }
  if (grantsQuery.isError) {
    return <ErrorState error={grantsQuery.error} onRetry={() => void grantsQuery.refetch()} />;
  }
  return <>{render(grantsQuery.data)}</>;
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return iso;
  }
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : t('error.internal', { correlationId: '—' });
}

function activeGrantFor(grants: ConsentRecord[], purposeCode: string): ConsentRecord | undefined {
  return grants.find((g) => g.purposeCode === purposeCode && g.status === 'ACTIVE');
}

function EssentialRow({ policy }: { policy: ConsentPolicy }) {
  return (
    <div className="consent-row consent-row--essential">
      <div className="consent-row__text">
        <p className="consent-row__name">{policy.purposeName}</p>
        <p className="consent-row__description">{policy.purposeDescription}</p>
      </div>
      <span className="consent-row__status consent-row__status--essential">
        {t('consent.essential')}
      </span>
    </div>
  );
}

function AdultToggleRow({
  policy,
  member,
  grants,
}: {
  policy: ConsentPolicy;
  member: HouseholdMember;
  grants: ConsentRecord[];
}) {
  const queryClient = useQueryClient();
  const activeRecord = activeGrantFor(grants, policy.purposeCode);
  const isActive = activeRecord !== undefined;

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['consent', 'grants', member.id] });

  const grantMutation = useMutation({
    mutationFn: () => grantConsent(member.id, policy.purposeCode),
    onSuccess: invalidate,
  });
  const withdrawMutation = useMutation({
    mutationFn: () => withdrawConsent(member.id, policy.purposeCode),
    onSuccess: invalidate,
  });

  const pending = grantMutation.isPending || withdrawMutation.isPending;
  const error = grantMutation.error ?? withdrawMutation.error;

  return (
    <div className="consent-row">
      <div className="consent-row__text">
        <p className="consent-row__name">{policy.purposeName}</p>
        <p className="consent-row__description">{policy.purposeDescription}</p>
        <p className="consent-row__version">
          {t('consent.policyVersion', { version: policy.policyVersion })}
        </p>
        {isActive && activeRecord.grantedAt && (
          <p className="consent-row__granted">
            {t('consent.grantedOn', { date: formatDate(activeRecord.grantedAt) })}
          </p>
        )}
        {isActive && <p className="consent-row__effect-note">{t('consent.withdrawEffect')}</p>}
      </div>

      <div className="consent-row__control">
        <span
          className={`consent-row__status ${
            isActive ? 'consent-row__status--on' : 'consent-row__status--off'
          }`}
        >
          {isActive ? t('consent.granted') : t('consent.withdrawn')}
        </span>
        <Button
          variant={isActive ? 'secondary' : 'primary'}
          size="sm"
          loading={pending}
          onClick={() => (isActive ? withdrawMutation.mutate() : grantMutation.mutate())}
        >
          {isActive ? t('consent.withdraw') : t('consent.grant')}
        </Button>
      </div>

      {error && (
        <p className="form-error" role="alert">
          {errorMessage(error)}
        </p>
      )}
    </div>
  );
}

function ChildToggleRow({
  policy,
  member,
  grants,
}: {
  policy: ConsentPolicy;
  member: HouseholdMember;
  grants: ConsentRecord[];
}) {
  const queryClient = useQueryClient();
  const [acknowledged, setAcknowledged] = useState(false);
  const activeRecord = activeGrantFor(grants, policy.purposeCode);
  const isActive = activeRecord !== undefined;

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['consent', 'grants', member.id] });

  const grantMutation = useMutation({
    mutationFn: () => grantConsent(member.id, policy.purposeCode),
    onSuccess: () => {
      invalidate();
      setAcknowledged(false);
    },
  });
  const withdrawMutation = useMutation({
    mutationFn: () => withdrawConsent(member.id, policy.purposeCode),
    onSuccess: invalidate,
  });

  const pending = grantMutation.isPending || withdrawMutation.isPending;
  const error = grantMutation.error ?? withdrawMutation.error;
  const attestationId = `consent-attest-${member.id}-${policy.purposeCode}`;

  return (
    <div className="consent-row consent-row--child">
      <div className="consent-row__text">
        <p className="consent-row__name">{policy.purposeName}</p>
        <p className="consent-row__description">{policy.purposeDescription}</p>
        <p className="consent-row__version">
          {t('consent.policyVersion', { version: policy.policyVersion })}
        </p>
        {isActive && activeRecord.grantedAt ? (
          <p className="consent-row__granted">
            {t('consent.grantedOn', { date: formatDate(activeRecord.grantedAt) })}
          </p>
        ) : (
          <p className="consent-row__pending-note">{t('consent.childPending')}</p>
        )}
        {isActive && <p className="consent-row__effect-note">{t('consent.withdrawEffect')}</p>}
      </div>

      {/* Never hidden, granted or not — a documented legal-review caveat on the
          one mechanism (GUARDIAN_ATTESTATION) that is not yet DPDP-verifiable
          parental consent. */}
      <p className="consent-row__legal-note">{t('consent.guardianAttestationNote')}</p>

      {!isActive && (
        <label className="consent-row__attestation" htmlFor={attestationId}>
          <input
            id={attestationId}
            type="checkbox"
            checked={acknowledged}
            onChange={(e) => setAcknowledged(e.target.checked)}
          />
          <span>{t('consent.guardianAttestation')}</span>
        </label>
      )}

      <div className="consent-row__control">
        <span
          className={`consent-row__status ${
            isActive ? 'consent-row__status--on' : 'consent-row__status--off'
          }`}
        >
          {isActive ? t('consent.granted') : t('consent.withdrawn')}
        </span>
        <Button
          variant={isActive ? 'secondary' : 'primary'}
          size="sm"
          loading={pending}
          disabled={!isActive && !acknowledged}
          onClick={() => (isActive ? withdrawMutation.mutate() : grantMutation.mutate())}
        >
          {isActive ? t('consent.withdraw') : t('consent.grant')}
        </Button>
      </div>

      {error && (
        <p className="form-error" role="alert">
          {errorMessage(error)}
        </p>
      )}
    </div>
  );
}
