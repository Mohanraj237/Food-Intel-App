import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useMeQuery } from '@/lib/me';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import {
  acceptInvitation,
  addChild,
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
import { PageHeader } from '@/design-system/PageHeader';
import './FamilyScreen.css';

/**
 * `/you/household` — invite an adult, add a child, leave, remove or erase a member (plan §18
 * Journey 3.5, workstreams doc §3/§0.5).
 *
 * Extracted out of `FamilyScreen.tsx`, which used to carry this alongside the wellness feed —
 * conflating a one-time setup task with a screen meant to be visited often. The plan is explicit
 * that a plain row-list is the *right* pattern here (§18 3.5): unlike Family or Today, this is
 * reached rarely and does one job, the same way a consumer app's "manage household" or "family
 * members" account-settings page does.
 *
 * Reached only once `/me`'s `mode !== NONE` (`App.tsx`'s `RequireHousehold`), so unlike the
 * screen this replaced, there is no "create or join a household from scratch" state to render —
 * onboarding (`/welcome`) already owns that. What is kept from the old `NoHouseholdView` is
 * narrower and genuinely still needed: accepting an invitation to an *additional* household
 * (`MeView.otherHouseholdCount` is a real, already-typed concept — there is no switcher, but
 * membership in more than one household is supported), offered as a small secondary action
 * rather than the screen's opening choice.
 */
export function HouseholdSettingsScreen() {
  useDocumentTitleKey('household.heading');
  const me = useMeQuery();
  const [searchParams] = useSearchParams();
  const intent = searchParams.get('intent');

  if (me.isPending) {
    return (
      <div className="page">
        <h1>{t('household.heading')}</h1>
        <Skeleton lines={3} label={t('loading.default')} />
      </div>
    );
  }

  if (me.isError) {
    return (
      <div className="page">
        <h1>{t('household.heading')}</h1>
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      </div>
    );
  }

  // Defensive only: `RequireHousehold` already redirects `mode = NONE` away before this route
  // mounts.
  if (me.data.household === null) {
    return (
      <div className="page">
        <h1>{t('household.heading')}</h1>
        <p>{t('today.needsHousehold')}</p>
        <Link to="/welcome" className="btn btn--primary btn--md">
          {t('household.create')}
        </Link>
      </div>
    );
  }

  return (
    <HouseholdSettingsContent
      householdId={me.data.household.id}
      householdName={me.data.household.name}
      accountId={me.data.accountId}
      intent={intent === 'invite' || intent === 'addChild' ? intent : null}
    />
  );
}

function HouseholdSettingsContent({
  householdId,
  householdName,
  accountId,
  intent,
}: {
  householdId: string;
  householdName: string;
  accountId: string;
  intent: 'invite' | 'addChild' | null;
}) {
  const [staleNotice, setStaleNotice] = useState(false);
  const membersQuery = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId),
    retry: false,
  });

  const isStaleError =
    membersQuery.isError && membersQuery.error instanceof ApiError && membersQuery.error.status === 403;

  useEffect(() => {
    if (isStaleError) setStaleNotice(true);
  }, [isStaleError]);

  return (
    <div className="page layout-list">
      <PageHeader
        title={t('household.heading')}
        {...(householdName ? { eyebrow: householdName } : {})}
        lede={t('household.settingsLede')}
        backTo="/family"
        backLabel={t('nav.family')}
      />

      {staleNotice && (
        <p className="form-error" role="alert">
          {t('household.staleNotice')}
        </p>
      )}

      {membersQuery.isPending && <Skeleton lines={3} label={t('loading.default')} />}

      {membersQuery.isError && !isStaleError && (
        <ErrorState error={membersQuery.error} onRetry={() => void membersQuery.refetch()} />
      )}

      {membersQuery.isSuccess && (
        <MembersList householdId={householdId} accountId={accountId} members={membersQuery.data} />
      )}

      {membersQuery.isSuccess && (
        <>
          <InviteAdultPanel householdId={householdId} autoFocus={intent === 'invite'} />
          <AddChildPanel householdId={householdId} autoFocus={intent === 'addChild'} />
          <JoinAnotherHousehold />
        </>
      )}
    </div>
  );
}

function MembersList({
  householdId,
  accountId,
  members,
}: {
  householdId: string;
  accountId: string | null;
  members: HouseholdMember[];
}) {
  const queryClient = useQueryClient();
  const selfMember = members.find((m) => m.userAccountId !== null && m.userAccountId === accountId);
  const isOwner = selfMember?.role === 'OWNER';

  // Replaces three separate `window.confirm()` calls (leave, remove, erase) — a native browser
  // prompt cannot be styled, cannot explain why the action matters, and reads as the OS
  // interrupting rather than part of the product.
  const [confirmAction, setConfirmAction] = useState<
    { type: 'leave' | 'remove' | 'erase'; member: HouseholdMember } | null
  >(null);

  const removeMutation = useMutation({
    mutationFn: (member: HouseholdMember) => removeMember(householdId, member.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['household', 'members', householdId] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  // Distinct from `removeMutation`: removing only ends a membership, this erases the child's
  // profile too and cannot be undone. Only an active guardian of this specific child can
  // actually succeed — the backend is the sole authority on that, so this is offered for every
  // child the same way, and a non-guardian simply sees the resulting error.
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

      <ul className="family__list" role="list">
        {members.map((member) => {
          const isSelf = member.userAccountId !== null && member.userAccountId === accountId;
          const canEditProfile = member.memberType === 'CHILD' || isSelf;
          const isRemoving = removeMutation.isPending && removeMutation.variables?.id === member.id;
          const isChild = member.memberType === 'CHILD';
          const profileHref = isChild ? `/family/${member.id}/profile` : '/you/profile';

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
                    <ProfileLink householdId={householdId} member={member} href={profileHref} />
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
                      {t('household.removeChildAction')}
                    </Button>
                  )}
                  {member.memberType === 'CHILD' && (
                    <Button
                      variant="destructive"
                      size="sm"
                      loading={eraseMutation.isPending && eraseMutation.variables?.id === member.id}
                      onClick={() => setConfirmAction({ type: 'erase', member })}
                    >
                      {t('household.eraseChildAction')}
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
 * Shares its query cache entry with `ProfilePanel` (same query key), so opening the panel right
 * after this link renders never re-fetches. Links by URL rather than importing `ProfilePanel`
 * and passing props (workstreams doc §4.3 — that component is directly routable now).
 */
function ProfileLink({
  householdId,
  member,
  href,
}: {
  householdId: string;
  member: HouseholdMember;
  href: string;
}) {
  const query = useQuery({
    queryKey: ['profile', householdId, member.id],
    queryFn: () => getProfile(householdId, member.id),
    retry: false,
  });

  return (
    <Link to={href} className="btn btn--secondary btn--sm">
      {query.isSuccess ? t('profile.editAction') : t('profile.setUpAction')}
    </Link>
  );
}

function InviteAdultPanel({ householdId, autoFocus }: { householdId: string; autoFocus: boolean }) {
  const [email, setEmail] = useState('');
  const [copied, setCopied] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!autoFocus) return;
    // Focusing scrolls it into view on every browser this product targets — no separate
    // container ref needed (`Card` is a plain function component, not `forwardRef`).
    inputRef.current?.focus();
  }, [autoFocus]);

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
              ref={inputRef}
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

function AddChildPanel({ householdId, autoFocus }: { householdId: string; autoFocus: boolean }) {
  const queryClient = useQueryClient();
  const [displayLabel, setDisplayLabel] = useState('');
  const [relationship, setRelationship] = useState<GuardianRelationship>('PARENT');
  const [authorityBasis, setAuthorityBasis] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!autoFocus) return;
    inputRef.current?.focus();
  }, [autoFocus]);

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
            ref={inputRef}
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

/**
 * Accepting an invitation into a *second* household. `MeView.otherHouseholdCount` (`lib/me.ts`)
 * is a real, already-shipped concept — there is no household switcher, but membership in more
 * than one household is supported — so this keeps `acceptInvitation` reachable rather than
 * dropping it silently now that the old "no household yet" screen (where it used to live) is
 * gone. Collapsed by default: this is not the common case this screen exists for.
 */
function JoinAnotherHousehold() {
  const [open, setOpen] = useState(false);
  const [token, setToken] = useState('');
  const [displayLabel, setDisplayLabel] = useState('');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => acceptInvitation(token.trim(), displayLabel.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      setToken('');
      setDisplayLabel('');
      setOpen(false);
    },
  });

  const submit = useCallback(
    (event: FormEvent) => {
      event.preventDefault();
      mutation.mutate();
    },
    [mutation],
  );

  if (!open) {
    return (
      <p className="field__hint">
        <Button variant="ghost" size="sm" onClick={() => setOpen(true)}>
          {t('household.haveInvite')}
        </Button>
      </p>
    );
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
        <Button type="submit" loading={mutation.isPending}>
          {t('action.join')}
        </Button>
      </form>
    </Card>
  );
}
