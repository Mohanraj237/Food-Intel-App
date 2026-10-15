import { useCallback, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { useMeQuery, type MeView } from '@/lib/me';
import { listMembers, type HouseholdMember } from '@/lib/household';
import {
  ACCESS_QUERY_KEYS,
  getMemberAccess,
  groupGrantsByAudience,
  groupGrantsBySubject,
  joinList,
  listAccessGrants,
  listAccessScopes,
  listSharedWithMe,
  offeredScopes,
  scopeLabel,
  scopeNoun,
  type AccessGrant,
  type GrantGroup,
} from '@/lib/access';
import { Button } from '@/design-system/Button';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { Avatar } from '@/design-system/Avatar';
import { PageHeader } from '@/design-system/PageHeader';
import { AccessLogList } from './AccessLogList';
import { ShareFlowSheet } from './ShareFlowSheet';
import { ChangeLevelSheet, StopSharingSheet } from './GrantSheets';
import { CaregiverExplainer } from './CaregiverExplainer';
import { childMembers, eligibleAudiences } from './shareFlow';
import { LEVEL_GLYPH, LEVEL_INCOMING_KEYS, LEVEL_STATUS_KEYS } from './sharingCopy';
import './SharingScreen.css';

/**
 * Sharing and access — intended at `/you/sharing` (D-044, D-045).
 *
 * Four questions, in the order a person asks them:
 *   1. What do I share, and with whom?   (change a level, stop sharing)
 *   2. Can I share with someone?         (an adult in my household only)
 *   3. What has been shared with me?
 *   4. Who has looked at my data?        (the access log — the feature that makes 1 safe)
 * and, for a guardian, each child's access log, with the rule that a child's data is never
 * shared this way.
 *
 * The screen never decides who may see what. It renders the server's grants and log, offers
 * only choices the server would accept, and shows the server's refusal in words when it says no.
 */
export function SharingScreen() {
  useDocumentTitleKey('sharing.heading');
  const signedIn = useIsSignedIn();
  const me = useMeQuery();

  let body: ReactNode;
  if (!signedIn) {
    body = (
      <div className="sharing-note">
        <p>{t('sharing.signInPrompt')}</p>
        <Link to="/settings" className="btn btn--primary btn--sm">
          {t('action.signIn')}
        </Link>
      </div>
    );
  } else if (me.isPending) {
    body = <Skeleton lines={5} label={t('loading.default')} />;
  } else if (me.isError) {
    body = <ErrorState error={me.error} onRetry={() => void me.refetch()} />;
  } else if (me.data.member === null || me.data.household === null) {
    body = (
      <div className="sharing-note">
        <p>{t('sharing.noHousehold')}</p>
        <Link to="/welcome" className="btn btn--primary btn--sm">
          {t('sharing.noHouseholdAction')}
        </Link>
      </div>
    );
  } else {
    body = (
      <SharingContent
        me={me.data}
        memberId={me.data.member.id}
        householdId={me.data.household.id}
        memberCount={me.data.household.memberCount}
        timeZone={me.data.household.timeZone}
      />
    );
  }

  return (
    <div className="page sharing">
      <PageHeader title={t('sharing.heading')} lede={t('sharing.lede')} backTo="/you" backLabel={t('nav.you')} />
      {body}
    </div>
  );
}

type SheetState =
  | { kind: 'share'; audienceId: string | null }
  | { kind: 'change'; grant: AccessGrant; name: string }
  | { kind: 'stop'; grants: AccessGrant[]; title: string; body: string }
  | null;

function SharingContent({
  me,
  memberId,
  householdId,
  memberCount,
  timeZone,
}: {
  me: MeView;
  memberId: string;
  householdId: string;
  memberCount: number;
  timeZone: string;
}) {
  const members = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId),
  });
  const grants = useQuery({
    queryKey: ACCESS_QUERY_KEYS.grants(memberId),
    queryFn: () => listAccessGrants(memberId),
  });
  const scopes = useQuery({
    queryKey: ACCESS_QUERY_KEYS.scopes,
    queryFn: listAccessScopes,
    staleTime: 30 * 60 * 1000,
  });

  const [sheet, setSheet] = useState<SheetState>(null);
  const [status, setStatus] = useState('');

  // Stable identities: BottomSheet re-runs its focus effect whenever `onClose` changes, which
  // would pull focus out of a field on every keystroke if this were a new function per render.
  const closeSheet = useCallback(() => setSheet(null), []);
  const finish = useCallback((message: string) => {
    setSheet(null);
    setStatus(message);
  }, []);

  const memberList = members.data ?? [];
  const audiences = eligibleAudiences(memberList, memberId);
  const kids = childMembers(memberList);
  const solo = members.isSuccess ? memberList.length <= 1 : memberCount <= 1;
  const offered = scopes.data ? offeredScopes(scopes.data) : null;

  const openShare = useCallback((audienceId: string | null) => {
    setStatus('');
    setSheet({ kind: 'share', audienceId });
  }, []);

  return (
    <>
      {/* Always in the tree, so a screen reader hears the confirmation when it appears. */}
      <p className="sharing__status" role="status" aria-live="polite">
        {status}
      </p>

      <section className="sharing__section" aria-labelledby="sharing-yours">
        <h2 id="sharing-yours" className="sharing__h2">
          {t('sharing.yours.heading')}
        </h2>
        {grants.isPending || members.isPending ? (
          <Skeleton lines={3} label={t('loading.default')} />
        ) : grants.isError ? (
          <ErrorState error={grants.error} onRetry={() => void grants.refetch()} />
        ) : members.isError ? (
          <ErrorState error={members.error} onRetry={() => void members.refetch()} />
        ) : (
          <YourSharing
            groups={groupGrantsByAudience(grants.data)}
            audiences={audiences}
            solo={solo}
            trackingActive={me.trackingConsent.active}
            onShare={openShare}
            onChange={(grant, name) => {
              setStatus('');
              setSheet({ kind: 'change', grant, name });
            }}
            onStop={(grant, name) => {
              setStatus('');
              setSheet({
                kind: 'stop',
                grants: [grant],
                title: t('sharing.stopConfirm.title', { name, scope: scopeNoun(grant.scope) }),
                body: t('sharing.stopConfirm.body'),
              });
            }}
            onStopAll={(group, name) => {
              setStatus('');
              setSheet({
                kind: 'stop',
                grants: group.grants,
                title: t('sharing.stopAllConfirm.title', { name }),
                body: t('sharing.stopAllConfirm.body', { name }),
              });
            }}
          />
        )}
      </section>

      {(!solo || me.otherHouseholdCount > 0) && <SharedWithYou />}

      <section className="sharing__section" aria-labelledby="sharing-log">
        <h2 id="sharing-log" className="sharing__h2">
          {t('sharing.log.heading')}
        </h2>
        <p className="sharing__section-lede">{t('sharing.log.lede')}</p>
        <AccessLogList
          memberId={memberId}
          subject={{ kind: 'SELF' }}
          timeZone={timeZone}
          emptyText={t('sharing.log.empty')}
          dayHeadingLevel={3}
        />
      </section>

      {kids.length > 0 && <GuardianSection kids={kids} timeZone={timeZone} />}

      {sheet?.kind === 'share' && (
        <ShareFlowSheet
          memberId={memberId}
          audiences={audiences}
          hasChildren={kids.length > 0}
          offered={offered}
          offeredError={scopes.error}
          onRetryOffered={() => void scopes.refetch()}
          grants={grants.data ?? []}
          initialAudienceId={sheet.audienceId}
          onClose={closeSheet}
          onDone={finish}
        />
      )}
      {sheet?.kind === 'change' && (
        <ChangeLevelSheet memberId={memberId} grant={sheet.grant} name={sheet.name} onClose={closeSheet} onDone={finish} />
      )}
      {sheet?.kind === 'stop' && (
        <StopSharingSheet
          memberId={memberId}
          grants={sheet.grants}
          title={sheet.title}
          body={sheet.body}
          onClose={closeSheet}
          onDone={finish}
        />
      )}
    </>
  );
}

/* ------------------------------------------------------------ what you share */

function YourSharing({
  groups,
  audiences,
  solo,
  trackingActive,
  onShare,
  onChange,
  onStop,
  onStopAll,
}: {
  groups: GrantGroup[];
  audiences: HouseholdMember[];
  solo: boolean;
  trackingActive: boolean;
  onShare: (audienceId: string | null) => void;
  onChange: (grant: AccessGrant, name: string) => void;
  onStop: (grant: AccessGrant, name: string) => void;
  onStopAll: (group: GrantGroup, name: string) => void;
}) {
  // Nobody to share with and nothing shared: say why, once, quietly (AC-2.7 — never nag).
  if (groups.length === 0 && audiences.length === 0) {
    return solo ? (
      <div className="sharing-note">
        <p className="sharing-note__title">{t('sharing.solo.title')}</p>
        <p>{t('sharing.solo.body')}</p>
        <Link to="/family" className="btn btn--secondary btn--sm">
          {t('sharing.solo.action')}
        </Link>
      </div>
    ) : (
      <div className="sharing-note">
        <p className="sharing-note__title">{t('sharing.solo.title')}</p>
        <p>{t('sharing.noAdults.body')}</p>
      </div>
    );
  }

  const eligibleIds = new Set(audiences.map((a) => a.id));

  return (
    <>
      {!trackingActive && (
        <div className="sharing-note" role="note">
          <p>{t('sharing.trackingOff')}</p>
          <Link to="/settings" className="btn btn--secondary btn--sm">
            {t('sharing.trackingOffAction')}
          </Link>
        </div>
      )}

      {groups.length === 0 ? (
        <p className="sharing__empty">{t('sharing.yours.empty')}</p>
      ) : (
        <ul className="sharing__people" role="list">
          {groups.map((group) => (
            <li key={group.memberId}>
              <PersonShareCard
                group={group}
                canShareMore={eligibleIds.has(group.memberId)}
                onChange={onChange}
                onStop={onStop}
                onStopAll={onStopAll}
                onShareMore={onShare}
              />
            </li>
          ))}
        </ul>
      )}

      {audiences.length > 0 && (
        <div>
          <Button variant="secondary" onClick={() => onShare(null)}>
            {t('sharing.share.open')}
          </Button>
        </div>
      )}
    </>
  );
}

function PersonShareCard({
  group,
  canShareMore,
  onChange,
  onStop,
  onStopAll,
  onShareMore,
}: {
  group: GrantGroup;
  canShareMore: boolean;
  onChange: (grant: AccessGrant, name: string) => void;
  onStop: (grant: AccessGrant, name: string) => void;
  onStopAll: (group: GrantGroup, name: string) => void;
  onShareMore: (audienceId: string) => void;
}) {
  const departed = group.label === null;
  const name = group.label ?? t('sharing.person.former');
  const headingId = `sharing-person-${group.memberId}`;
  const managed = group.grants.filter((g) => g.level === 'MANAGE').map((g) => scopeNoun(g.scope));

  return (
    <article className="sharing-person" aria-labelledby={headingId}>
      <header className="sharing-person__head">
        <Avatar name={departed ? '?' : name} />
        <span className="sharing-person__who">
          <h3 id={headingId} className="sharing-person__name">
            {name}
          </h3>
          <span className="sharing-tag">
            {group.relationship === 'CAREGIVER' ? t('sharing.person.caregiverTag') : t('sharing.person.viewerTag')}
          </span>
        </span>
      </header>

      {departed && <p className="sharing-hint">{t('sharing.person.formerNote')}</p>}

      <ul className="sharing-person__scopes" role="list" aria-label={t('sharing.person.scopesLabel', { name })}>
        {group.grants.map((grant) => {
          const noun = scopeNoun(grant.scope);
          return (
            <li key={grant.id} className="sharing-scope">
              <span className="sharing-scope__text">
                <span className="sharing-scope__label">{scopeLabel(grant.scope)}</span>
                <span className="sharing-scope__level">
                  <span aria-hidden="true">{LEVEL_GLYPH[grant.level]}</span> {t(LEVEL_STATUS_KEYS[grant.level])}
                </span>
              </span>
              <span className="sharing-scope__actions">
                {!departed && (
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('sharing.changeA11y', { name, scope: noun })}
                    onClick={() => onChange(grant, name)}
                  >
                    {t('sharing.change')}
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={t('sharing.stopA11y', { name, scope: noun })}
                  onClick={() => onStop(grant, name)}
                >
                  {t('sharing.stop')}
                </Button>
              </span>
            </li>
          );
        })}
      </ul>

      {!departed && managed.length > 0 && <CaregiverExplainer name={name} areas={joinList(managed)} collapsible />}

      {(canShareMore || group.grants.length > 1) && (
        <div className="sharing-person__actions">
          {canShareMore && (
            <Button variant="secondary" size="sm" onClick={() => onShareMore(group.memberId)}>
              {t('sharing.shareMore', { name })}
            </Button>
          )}
          {group.grants.length > 1 && (
            <Button variant="secondary" size="sm" onClick={() => onStopAll(group, name)}>
              {t('sharing.stopAll', { name })}
            </Button>
          )}
        </div>
      )}
    </article>
  );
}

/* ----------------------------------------------------------- shared with you */

function SharedWithYou() {
  const incoming = useQuery({
    queryKey: ACCESS_QUERY_KEYS.sharedWithMe,
    queryFn: listSharedWithMe,
  });

  return (
    <section className="sharing__section" aria-labelledby="sharing-incoming">
      <h2 id="sharing-incoming" className="sharing__h2">
        {t('sharing.incoming.heading')}
      </h2>
      {incoming.isPending ? (
        <Skeleton lines={2} label={t('loading.default')} />
      ) : incoming.isError ? (
        <ErrorState error={incoming.error} onRetry={() => void incoming.refetch()} />
      ) : (
        <IncomingList groups={groupGrantsBySubject(incoming.data)} />
      )}
    </section>
  );
}

function IncomingList({ groups }: { groups: GrantGroup[] }) {
  if (groups.length === 0) {
    return <EmptyState title={t('sharing.incoming.empty')} />;
  }
  return (
    <ul className="sharing__people" role="list">
      {groups.map((group) => {
        // The server drops grants from people who left, so a label is expected; a missing one
        // still gets a truthful name rather than a blank.
        const name = group.label ?? t('sharing.person.former');
        const headingId = `sharing-incoming-${group.memberId}`;
        return (
          <li key={group.memberId}>
            <article className="sharing-person" aria-labelledby={headingId}>
              <header className="sharing-person__head">
                <Avatar name={name} />
                <span className="sharing-person__who">
                  <h3 id={headingId} className="sharing-person__name">
                    {t('sharing.incoming.person', { name })}
                  </h3>
                  {group.relationship === 'CAREGIVER' && (
                    <span className="sharing-tag">{t('sharing.incoming.caregiverTag')}</span>
                  )}
                </span>
              </header>
              <ul className="sharing-person__scopes" role="list">
                {group.grants.map((grant) => (
                  <li key={grant.id} className="sharing-scope">
                    <span className="sharing-scope__text">
                      <span className="sharing-scope__label">{scopeLabel(grant.scope)}</span>
                      <span className="sharing-scope__level">
                        <span aria-hidden="true">{LEVEL_GLYPH[grant.level]}</span>{' '}
                        {t(LEVEL_INCOMING_KEYS[grant.level])}
                      </span>
                    </span>
                  </li>
                ))}
              </ul>
              {group.relationship === 'CAREGIVER' && (
                <p className="sharing-hint">{t('sharing.incoming.caregiverNote', { name })}</p>
              )}
            </article>
          </li>
        );
      })}
    </ul>
  );
}

/* ----------------------------------------------------------------- guardian */

/**
 * The children in the household whose guardian the caller is, each with their access log.
 * Guardianship is the server's answer (`GET /members/{id}/access` → GUARDIAN), asked per child;
 * the section appears only when at least one answer is yes.
 */
function GuardianSection({ kids, timeZone }: { kids: HouseholdMember[]; timeZone: string }) {
  const checks = useQueries({
    queries: kids.map((kid) => ({
      queryKey: ACCESS_QUERY_KEYS.member(kid.id),
      queryFn: () => getMemberAccess(kid.id),
    })),
  });

  const guarded = kids.filter((_, i) => checks[i]?.data?.relationship === 'GUARDIAN');
  const failed = checks.find((check) => check.isError);
  if (guarded.length === 0 && !failed) return null;

  return (
    <section className="sharing__section" aria-labelledby="sharing-children">
      <h2 id="sharing-children" className="sharing__h2">
        {t('sharing.children.heading')}
      </h2>
      <div className="sharing-note" role="note">
        <p>{t('sharing.children.body')}</p>
      </div>
      {failed && (
        <ErrorState
          error={failed.error}
          onRetry={() => checks.forEach((check) => void (check.isError && check.refetch()))}
        />
      )}
      {guarded.map((kid) => {
        const headingId = `sharing-child-${kid.id}`;
        return (
          <section key={kid.id} className="sharing__child" aria-labelledby={headingId}>
            <h3 id={headingId} className="sharing__h3">
              {t('sharing.children.logHeading', { name: kid.displayLabel })}
            </h3>
            <AccessLogList
              memberId={kid.id}
              subject={{ kind: 'CHILD', label: kid.displayLabel }}
              timeZone={timeZone}
              emptyText={t('sharing.children.logEmpty', { name: kid.displayLabel })}
              dayHeadingLevel={4}
            />
          </section>
        );
      })}
    </section>
  );
}
