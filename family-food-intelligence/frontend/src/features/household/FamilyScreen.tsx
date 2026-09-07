import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useAccountId, useIsSignedIn } from '@/lib/useSession';
import { MY_HOUSEHOLDS_QUERY_KEY, useMyHousehold } from '@/lib/useMyHousehold';
import {
  acceptInvitation,
  addChild,
  createHousehold,
  eraseChildMember,
  inviteAdult,
  listMembers,
  removeMember,
  type GuardianRelationship,
  type HouseholdMember,
  type InvitationIssued,
} from '@/lib/household';
import { getProfile } from '@/lib/profile';
import { Button } from '@/design-system/Button';
import { Card } from '@/design-system/Card';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { HouseholdMemberCard } from '@/design-system/HouseholdMemberCard';
import { ConfirmSheet } from '@/design-system/BottomSheet';
import { ProfilePanel } from './ProfilePanel';
import './FamilyScreen.css';

/**
 * "Who is eating?" — see/manage the signed-in account's household, then set up
 * each person's profile.
 *
 * The household id comes from `useMyHousehold` (`lib/useMyHousehold.ts`),
 * backed by `GET /households`. If that turns out to be stale — a 403
 * `NOT_HOUSEHOLD_MEMBER` from `listMembers`, or the caller having just left —
 * the `['households', 'mine']` query is invalidated so it refetches and this
 * screen falls through to the "no household yet" view with an honest
 * explanation, rather than a dead error screen.
 */
export function FamilyScreen() {
  const signedIn = useIsSignedIn();
  const accountId = useAccountId();
  const queryClient = useQueryClient();
  const myHousehold = useMyHousehold({ enabled: signedIn });
  const [staleNotice, setStaleNotice] = useState(false);

  // Stable identities: `onStale` is a dependency of `HouseholdView`'s effect
  // (fires when a 403 is detected) and `onReady`/`onStale` flow into
  // `StaleHousehold`-style effects too — a new function each render would
  // re-fire those effects on every unrelated re-render, invalidating the
  // query in a loop.
  const handleStale = useCallback(() => {
    setStaleNotice(true);
    void queryClient.invalidateQueries({ queryKey: MY_HOUSEHOLDS_QUERY_KEY });
  }, [queryClient]);

  const handleReady = useCallback(() => {
    setStaleNotice(false);
    void queryClient.invalidateQueries({ queryKey: MY_HOUSEHOLDS_QUERY_KEY });
  }, [queryClient]);

  if (!signedIn) {
    return (
      <div className="page">
        <h1>{t('household.heading')}</h1>
        <p>{t('household.signInRequired')}</p>
        <Link to="/settings" className="btn btn--primary btn--md">
          {t('action.signIn')}
        </Link>
      </div>
    );
  }

  if (myHousehold.isPending) {
    return (
      <div className="page">
        <h1>{t('household.heading')}</h1>
        <Skeleton lines={3} label={t('loading.default')} />
      </div>
    );
  }

  if (myHousehold.isError) {
    return (
      <div className="page">
        <h1>{t('household.heading')}</h1>
        <ErrorState error={myHousehold.error} onRetry={() => void myHousehold.refetch()} />
      </div>
    );
  }

  if (myHousehold.householdId === null) {
    return <NoHouseholdView staleNotice={staleNotice} onReady={handleReady} />;
  }

  return (
    <HouseholdView
      householdId={myHousehold.householdId}
      accountId={accountId}
      onStale={handleStale}
    />
  );
}

// ------------------------------------------------------------- no household

/**
 * The very first thing an unattached account sees. Previously a form was
 * shown by default with a small text toggle to flip to the other one — easy
 * to miss, and it framed "join" as an afterthought. Instead this leads with
 * an explicit, equal-weight choice (each option already carries its own
 * explainer copy, reused verbatim from the forms below) and only drops into
 * a form once one is picked, with a plain way back to reconsider.
 */
function NoHouseholdView({
  staleNotice,
  onReady,
}: {
  staleNotice: boolean;
  onReady: () => void;
}) {
  const [mode, setMode] = useState<'choose' | 'create' | 'join'>('choose');

  return (
    <div className="page">
      <h1>{t('household.heading')}</h1>

      {staleNotice && (
        <p className="form-error" role="alert">
          {t('household.staleNotice')}
        </p>
      )}

      {mode === 'choose' && (
        <div className="household-choice">
          <Card as="section" padding="lg" aria-labelledby="choice-create-heading">
            <h2 id="choice-create-heading" className="family__h2">
              {t('household.create')}
            </h2>
            <p className="field__hint">{t('household.createExplainer')}</p>
            <p className="field__hint">{t('household.soloIsFine')}</p>
            <Button fullWidth onClick={() => setMode('create')}>
              {t('household.create')}
            </Button>
          </Card>

          <Card as="section" padding="lg" aria-labelledby="choice-join-heading">
            <h2 id="choice-join-heading" className="family__h2">
              {t('household.joinHeading')}
            </h2>
            <p className="field__hint">{t('household.joinExplainer')}</p>
            <Button variant="secondary" fullWidth onClick={() => setMode('join')}>
              {t('household.haveInvite')}
            </Button>
          </Card>
        </div>
      )}

      {mode !== 'choose' && (
        <Button variant="ghost" size="sm" onClick={() => setMode('choose')}>
          {t('action.back')}
        </Button>
      )}

      {mode === 'create' && <CreateHouseholdForm onCreated={onReady} />}
      {mode === 'join' && <JoinHouseholdForm onJoined={onReady} />}
    </div>
  );
}

function CreateHouseholdForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('');
  const [ownerDisplayLabel, setOwnerDisplayLabel] = useState('');

  const mutation = useMutation({
    mutationFn: () => createHousehold(name.trim(), ownerDisplayLabel.trim()),
    onSuccess: () => {
      onCreated();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  return (
    <Card as="section" className="family__panel" aria-labelledby="create-heading">
      <h2 id="create-heading" className="family__h2">
        {t('household.create')}
      </h2>
      <p className="field__hint">{t('household.createExplainer')}</p>
      <p className="field__hint">{t('household.soloIsFine')}</p>
      <form className="family__form" onSubmit={submit}>
        <label className="field">
          <span className="field__label">{t('household.nameLabel')}</span>
          <input required maxLength={80} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">{t('household.yourDisplayLabel')}</span>
          <input
            required
            maxLength={60}
            value={ownerDisplayLabel}
            onChange={(e) => setOwnerDisplayLabel(e.target.value)}
          />
        </label>
        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}
        <Button type="submit" fullWidth loading={mutation.isPending}>
          {t('household.create')}
        </Button>
      </form>
    </Card>
  );
}

function JoinHouseholdForm({ onJoined }: { onJoined: () => void }) {
  const [token, setToken] = useState('');
  const [displayLabel, setDisplayLabel] = useState('');

  const mutation = useMutation({
    mutationFn: () => acceptInvitation(token.trim(), displayLabel.trim()),
    onSuccess: () => {
      onJoined();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  return (
    <Card as="section" className="family__panel" aria-labelledby="join-heading">
      <h2 id="join-heading" className="family__h2">
        {t('household.joinHeading')}
      </h2>
      <p className="field__hint">{t('household.joinExplainer')}</p>
      <form className="family__form" onSubmit={submit}>
        <label className="field">
          <span className="field__label">{t('household.tokenLabel')}</span>
          <input required value={token} onChange={(e) => setToken(e.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">{t('household.yourDisplayLabel')}</span>
          <input
            required
            maxLength={60}
            value={displayLabel}
            onChange={(e) => setDisplayLabel(e.target.value)}
          />
        </label>
        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}
        <Button type="submit" fullWidth loading={mutation.isPending}>
          {t('action.join')}
        </Button>
      </form>
    </Card>
  );
}

// --------------------------------------------------------------- household

function HouseholdView({
  householdId,
  accountId,
  onStale,
}: {
  householdId: string;
  accountId: string | null;
  onStale: () => void;
}) {
  const [activeProfileMember, setActiveProfileMember] = useState<HouseholdMember | null>(null);

  const membersQuery = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId),
    retry: false,
  });

  const isStaleError =
    membersQuery.isError && membersQuery.error instanceof ApiError && membersQuery.error.status === 403;

  useEffect(() => {
    if (isStaleError) onStale();
  }, [isStaleError, onStale]);

  if (activeProfileMember) {
    return (
      <ProfilePanel
        householdId={householdId}
        member={activeProfileMember}
        onBack={() => setActiveProfileMember(null)}
      />
    );
  }

  return (
    <div className="page">
      <h1>{t('household.heading')}</h1>

      {membersQuery.isPending && <Skeleton lines={3} label={t('loading.default')} />}

      {membersQuery.isError && !isStaleError && (
        <ErrorState error={membersQuery.error} onRetry={() => void membersQuery.refetch()} />
      )}

      {membersQuery.isSuccess && (
        <MembersList
          householdId={householdId}
          accountId={accountId}
          members={membersQuery.data}
          onStale={onStale}
          onEditProfile={setActiveProfileMember}
        />
      )}

      {membersQuery.isSuccess && (
        <>
          <InviteAdultPanel householdId={householdId} />
          <AddChildPanel householdId={householdId} />
        </>
      )}
    </div>
  );
}

function MembersList({
  householdId,
  accountId,
  members,
  onStale,
  onEditProfile,
}: {
  householdId: string;
  accountId: string | null;
  members: HouseholdMember[];
  onStale: () => void;
  onEditProfile: (member: HouseholdMember) => void;
}) {
  const queryClient = useQueryClient();
  const selfMember = members.find((m) => m.userAccountId !== null && m.userAccountId === accountId);
  const isOwner = selfMember?.role === 'OWNER';

  // Replaces three separate `window.confirm()` calls (leave, remove, erase) —
  // a native browser prompt cannot be styled, cannot explain why the action
  // matters, and reads as the OS interrupting rather than part of the product.
  const [confirmAction, setConfirmAction] = useState<
    { type: 'leave' | 'remove' | 'erase'; member: HouseholdMember } | null
  >(null);

  const removeMutation = useMutation({
    mutationFn: (member: HouseholdMember) => removeMember(householdId, member.id),
    onSuccess: (_result, member) => {
      if (member.userAccountId !== null && member.userAccountId === accountId) {
        // Removed self: this household id is no longer ours to keep.
        onStale();
        return;
      }
      void queryClient.invalidateQueries({ queryKey: ['household', 'members', householdId] });
    },
  });

  // Distinct from removeMutation (CH-008/FR-010): removing only ends a
  // membership, this erases the child's profile too and cannot be undone. Only
  // an active guardian of this specific child can actually succeed — the
  // backend is the sole authority on that, so this is shown for every child
  // the same way `ProfileActionButton` is, and a non-guardian simply sees the
  // resulting error.
  const eraseMutation = useMutation({
    mutationFn: (member: HouseholdMember) => eraseChildMember(householdId, member.id),
    onSuccess: (_result, member) => {
      void queryClient.invalidateQueries({ queryKey: ['household', 'members', householdId] });
      void queryClient.invalidateQueries({ queryKey: ['profile', householdId, member.id] });
    },
  });

  if (members.length === 0) {
    return <EmptyState title={t('household.membersCount', { count: 0 })} />;
  }

  const removeErrorMessage =
    removeMutation.error instanceof ApiError ? removeMutation.error.message : null;
  const eraseErrorMessage =
    eraseMutation.error instanceof ApiError ? eraseMutation.error.message : null;

  function confirmSheetProps(): {
    title: string;
    confirmLabel: string;
    tone: 'default' | 'danger';
    onConfirm: () => void;
  } | null {
    if (!confirmAction) return null;
    const { type, member } = confirmAction;
    if (type === 'leave') {
      return {
        title: t('household.leaveConfirm'),
        confirmLabel: t('action.leave'),
        tone: 'default',
        onConfirm: () => removeMutation.mutate(member),
      };
    }
    if (type === 'remove') {
      return {
        title: t('household.confirmRemove', { name: member.displayLabel }),
        confirmLabel: t('action.remove'),
        tone: 'default',
        onConfirm: () => removeMutation.mutate(member),
      };
    }
    return {
      title: t('privacy.deleteChildConfirm', { name: member.displayLabel }),
      confirmLabel: t('privacy.deleteChildData'),
      tone: 'danger',
      onConfirm: () => eraseMutation.mutate(member),
    };
  }

  const sheet = confirmSheetProps();

  return (
    <section aria-labelledby="members-heading">
      <h2 id="members-heading" className="family__h2">
        {t('household.membersCount', { count: members.length })}
      </h2>

      {removeErrorMessage && (
        <p className="form-error" role="alert">
          {removeErrorMessage}
        </p>
      )}
      {eraseErrorMessage && (
        <p className="form-error" role="alert">
          {eraseErrorMessage}
        </p>
      )}

      <ul className="family__list">
        {members.map((member) => {
          const isSelf = member.userAccountId !== null && member.userAccountId === accountId;
          const canEditProfile = member.memberType === 'CHILD' || isSelf;
          const isRemoving = removeMutation.isPending && removeMutation.variables?.id === member.id;
          const isChild = member.memberType === 'CHILD';

          return (
            <HouseholdMemberCard
              as="li"
              key={member.id}
              name={member.displayLabel}
              roleLabel={t(`household.role.${member.role}`)}
              isSelf={isSelf}
              isChild={isChild}
              youLabel={t('household.you')}
              actions={
                <>
                  {canEditProfile && (
                    <ProfileActionButton
                      householdId={householdId}
                      member={member}
                      onOpen={() => onEditProfile(member)}
                    />
                  )}
                  {isSelf && (
                    <Button
                      variant="ghost"
                      size="sm"
                      loading={isRemoving}
                      onClick={() => setConfirmAction({ type: 'leave', member })}
                    >
                      {t('action.leave')}
                    </Button>
                  )}
                  {!isSelf && isOwner && (
                    <Button
                      variant="ghost"
                      size="sm"
                      loading={isRemoving}
                      onClick={() => setConfirmAction({ type: 'remove', member })}
                    >
                      {t('action.remove')}
                    </Button>
                  )}
                  {member.memberType === 'CHILD' && (
                    <Button
                      variant="destructive"
                      size="sm"
                      loading={eraseMutation.isPending && eraseMutation.variables?.id === member.id}
                      onClick={() => setConfirmAction({ type: 'erase', member })}
                    >
                      {t('privacy.deleteChildData')}
                    </Button>
                  )}
                </>
              }
            />
          );
        })}
      </ul>

      <ConfirmSheet
        open={sheet !== null}
        onClose={() => setConfirmAction(null)}
        onConfirm={() => {
          sheet?.onConfirm();
          setConfirmAction(null);
        }}
        title={sheet?.title ?? ''}
        confirmLabel={sheet?.confirmLabel ?? ''}
        tone={sheet?.tone ?? 'default'}
      />
    </section>
  );
}

/**
 * Shares its query cache entry with `ProfilePanel` (same query key), so
 * opening the panel right after this button renders never re-fetches. A real
 * fetch error here (anything but "no profile yet") is not shown as a row-level
 * error — the label simply falls back to "set up profile", and the honest
 * `ErrorState` is what the user sees once they open the panel.
 */
function ProfileActionButton({
  householdId,
  member,
  onOpen,
}: {
  householdId: string;
  member: HouseholdMember;
  onOpen: () => void;
}) {
  const query = useQuery({
    queryKey: ['profile', householdId, member.id],
    queryFn: () => getProfile(householdId, member.id),
    retry: false,
  });

  return (
    <Button variant="secondary" size="sm" loading={query.isPending} onClick={onOpen}>
      {query.isSuccess ? t('profile.editAction') : t('profile.setUpAction')}
    </Button>
  );
}

function InviteAdultPanel({ householdId }: { householdId: string }) {
  const [email, setEmail] = useState('');
  const [copied, setCopied] = useState(false);

  const mutation = useMutation({
    mutationFn: () => inviteAdult(householdId, email.trim()),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  function copyToken(issued: InvitationIssued) {
    navigator.clipboard
      ?.writeText(issued.token)
      .then(() => {
        setCopied(true);
        window.setTimeout(() => setCopied(false), 2000);
      })
      .catch(() => undefined);
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  return (
    <Card as="section" className="family__panel" aria-labelledby="invite-heading">
      <h2 id="invite-heading" className="family__h2">
        {t('household.inviteAdult')}
      </h2>
      <p className="field__hint">{t('household.inviteExplainer')}</p>

      {mutation.isSuccess ? (
        <div className="family__invite-result">
          <p className="profile__saved" role="status">
            {t('household.inviteSent')}
          </p>
          <p className="field__hint">{t('household.inviteCodeExplainer')}</p>
          <label className="field">
            <span className="field__label">{t('household.tokenLabel')}</span>
            <input readOnly value={mutation.data.token} onFocus={(e) => e.currentTarget.select()} />
          </label>
          <div className="family__invite-actions">
            <Button variant="secondary" size="sm" onClick={() => copyToken(mutation.data)}>
              {copied ? t('action.copied') : t('action.copy')}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                mutation.reset();
                setEmail('');
                setCopied(false);
              }}
            >
              {t('action.done')}
            </Button>
          </div>
        </div>
      ) : (
        <form className="family__form" onSubmit={submit}>
          <label className="field">
            <span className="field__label">{t('auth.email')}</span>
            <input
              type="email"
              required
              maxLength={254}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          {errorMessage && (
            <p className="form-error" role="alert">
              {errorMessage}
            </p>
          )}
          <Button type="submit" loading={mutation.isPending}>
            {t('action.sendInvite')}
          </Button>
        </form>
      )}
    </Card>
  );
}

function AddChildPanel({ householdId }: { householdId: string }) {
  const queryClient = useQueryClient();
  const [displayLabel, setDisplayLabel] = useState('');
  const [relationship, setRelationship] = useState<GuardianRelationship>('PARENT');
  const [authorityBasis, setAuthorityBasis] = useState('');

  const mutation = useMutation({
    mutationFn: () => addChild(householdId, displayLabel.trim(), relationship, authorityBasis.trim()),
    onSuccess: () => {
      setDisplayLabel('');
      setAuthorityBasis('');
      setRelationship('PARENT');
      void queryClient.invalidateQueries({ queryKey: ['household', 'members', householdId] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  return (
    <Card as="section" className="family__panel" aria-labelledby="addchild-heading">
      <h2 id="addchild-heading" className="family__h2">
        {t('household.addChild')}
      </h2>
      <p className="field__hint">{t('household.addChildExplainer')}</p>
      <form className="family__form" onSubmit={submit}>
        <label className="field">
          <span className="field__label">{t('household.childDisplayLabel')}</span>
          <input
            required
            maxLength={60}
            value={displayLabel}
            onChange={(e) => setDisplayLabel(e.target.value)}
          />
        </label>
        <label className="field">
          <span className="field__label">{t('household.relationshipLabel')}</span>
          <select
            value={relationship}
            onChange={(e) => setRelationship(e.target.value as GuardianRelationship)}
          >
            <option value="PARENT">{t('relationship.PARENT')}</option>
            <option value="LEGAL_GUARDIAN">{t('relationship.LEGAL_GUARDIAN')}</option>
          </select>
        </label>
        <label className="field">
          <span className="field__label">{t('household.authorityBasisLabel')}</span>
          <input
            required
            maxLength={200}
            value={authorityBasis}
            onChange={(e) => setAuthorityBasis(e.target.value)}
          />
          <span className="field__hint">{t('household.authorityBasisExplainer')}</span>
        </label>
        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}
        <Button type="submit" loading={mutation.isPending}>
          {t('action.addChild')}
        </Button>
      </form>
    </Card>
  );
}
