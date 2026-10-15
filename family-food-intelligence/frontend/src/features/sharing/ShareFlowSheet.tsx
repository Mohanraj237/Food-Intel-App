import { useId, useRef, useState, type ReactNode, type RefObject } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import type { HouseholdMember } from '@/lib/household';
import {
  ACCESS_QUERY_KEYS,
  grantAccess,
  grantErrorKey,
  joinList,
  scopeLabel,
  scopeNoun,
  sortScopes,
  type AccessGrant,
  type AccessLevel,
  type SharingScope,
} from '@/lib/access';
import { BottomSheet } from '@/design-system/BottomSheet';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { scopeChoices, shareStepBlocker, shareSteps } from './shareFlow';
import { useFocusOnChange, useSheetFocusTrap } from './useSheetFocus';
import { LEVEL_HINT_KEYS, LEVEL_OPTION_KEYS, SCOPE_HINT_KEYS } from './sharingCopy';
import { CaregiverExplainer } from './CaregiverExplainer';

interface ShareFlowSheetProps {
  memberId: string;
  audiences: HouseholdMember[];
  hasChildren: boolean;
  /** `null` while the scope list is loading. */
  offered: SharingScope[] | null;
  offeredError: unknown;
  onRetryOffered: () => void;
  grants: AccessGrant[];
  initialAudienceId: string | null;
  onClose: () => void;
  onDone: (message: string) => void;
}

const LEVELS: readonly AccessLevel[] = ['VIEW', 'MANAGE'];

/**
 * "Share with someone": person → areas → what they can do → check and share.
 *
 * Mounted only while open, so every run starts clean. The level starts at VIEW, the least a
 * grant can give; choosing MANAGE shows, before anything is saved, exactly what a caregiver
 * can and cannot do (D-045).
 */
export function ShareFlowSheet({
  memberId,
  audiences,
  hasChildren,
  offered,
  offeredError,
  onRetryOffered,
  grants,
  initialAudienceId,
  onClose,
  onDone,
}: ShareFlowSheetProps) {
  const queryClient = useQueryClient();
  const steps = shareSteps(initialAudienceId !== null);
  const [stepIndex, setStepIndex] = useState(0);
  const [audienceId, setAudienceId] = useState<string | null>(initialAudienceId);
  const [scopes, setScopes] = useState<Set<SharingScope>>(new Set());
  const [level, setLevel] = useState<AccessLevel>('VIEW');
  const [blocker, setBlocker] = useState<MessageKey | null>(null);

  const anchorRef = useRef<HTMLDivElement>(null);
  const stepHeadingRef = useRef<HTMLHeadingElement>(null);
  const step = steps[stepIndex] ?? 'REVIEW';
  useSheetFocusTrap(anchorRef);
  useFocusOnChange(stepHeadingRef, step);

  const audience = audiences.find((a) => a.id === audienceId) ?? null;
  const name = audience?.displayLabel ?? '';
  const chosenScopes = sortScopes(Array.from(scopes));

  const share = useMutation({
    mutationFn: async () => {
      if (audienceId === null) return;
      // One grant per scope, in order, stopping at the first refusal so the message names it.
      for (const scope of chosenScopes) {
        await grantAccess(memberId, { audienceMemberId: audienceId, scope, level });
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ACCESS_QUERY_KEYS.grants(memberId) }),
    onSuccess: () => onDone(t('sharing.share.done', { name })),
  });

  function goNext() {
    const reason = shareStepBlocker(step, { audienceId, scopes });
    if (reason === 'PERSON_REQUIRED') {
      setBlocker('sharing.share.personRequired');
      return;
    }
    if (reason === 'SCOPES_REQUIRED') {
      setBlocker('sharing.share.scopesRequired');
      return;
    }
    setBlocker(null);
    setStepIndex((i) => Math.min(i + 1, steps.length - 1));
  }

  function goBack() {
    setBlocker(null);
    share.reset();
    if (stepIndex === 0) {
      onClose();
    } else {
      setStepIndex((i) => i - 1);
    }
  }

  function toggleScope(scope: SharingScope) {
    setBlocker(null);
    setScopes((prev) => {
      const next = new Set(prev);
      if (next.has(scope)) next.delete(scope);
      else next.add(scope);
      return next;
    });
  }

  function chooseAudience(id: string) {
    setBlocker(null);
    if (id !== audienceId) {
      // Areas already shared differ per person; a choice made for someone else must not carry over.
      setScopes(new Set());
    }
    setAudienceId(id);
  }

  const shareError = share.error ? errorText(share.error) : null;
  const isLast = step === 'REVIEW';

  return (
    <BottomSheet open onClose={onClose} title={t('sharing.share.title')}>
      <div ref={anchorRef} className="share-flow">
        {steps.length > 1 && (
          <p className="share-flow__progress">
            {t('sharing.share.progress', { current: stepIndex + 1, total: steps.length })}
          </p>
        )}

        {step === 'PERSON' && (
          <StepFrame headingRef={stepHeadingRef} heading={t('sharing.share.stepPerson')}>
            {(legendId) => (
              <fieldset className="sharing-options" aria-labelledby={legendId}>
                {audiences.map((a) => (
                  <label key={a.id} className="sharing-option">
                    <input
                      type="radio"
                      name="share-audience"
                      value={a.id}
                      checked={audienceId === a.id}
                      onChange={() => chooseAudience(a.id)}
                    />
                    <span className="sharing-option__text">
                      <span className="sharing-option__label">{a.displayLabel}</span>
                    </span>
                  </label>
                ))}
                <p className="sharing-hint">{t('sharing.share.personHint')}</p>
                {hasChildren && <p className="sharing-hint">{t('sharing.share.childrenNote')}</p>}
              </fieldset>
            )}
          </StepFrame>
        )}

        {step === 'SCOPES' && (
          <StepFrame headingRef={stepHeadingRef} heading={t('sharing.share.stepScopes', { name })}>
            {(legendId) =>
              offered === null ? (
                offeredError ? (
                  <ErrorState error={offeredError} onRetry={onRetryOffered} />
                ) : (
                  <Skeleton lines={4} label={t('loading.default')} />
                )
              ) : (
                <ScopeChooser
                  legendId={legendId}
                  offered={offered}
                  grants={grants}
                  audienceId={audienceId ?? ''}
                  selected={scopes}
                  onToggle={toggleScope}
                  name={name}
                />
              )
            }
          </StepFrame>
        )}

        {step === 'LEVEL' && (
          <StepFrame headingRef={stepHeadingRef} heading={t('sharing.share.stepLevel', { name })}>
            {(legendId) => (
              <>
                <fieldset className="sharing-options" aria-labelledby={legendId}>
                  {LEVELS.map((option) => (
                    <label key={option} className="sharing-option">
                      <input
                        type="radio"
                        name="share-level"
                        value={option}
                        checked={level === option}
                        onChange={() => setLevel(option)}
                      />
                      <span className="sharing-option__text">
                        <span className="sharing-option__label">{t(LEVEL_OPTION_KEYS[option])}</span>
                        <span className="sharing-option__hint">{t(LEVEL_HINT_KEYS[option])}</span>
                      </span>
                    </label>
                  ))}
                </fieldset>
                {level === 'MANAGE' && (
                  <CaregiverExplainer name={name} areas={joinList(chosenScopes.map(scopeNoun))} />
                )}
              </>
            )}
          </StepFrame>
        )}

        {step === 'REVIEW' && (
          <StepFrame headingRef={stepHeadingRef} heading={t('sharing.share.stepReview')}>
            {() => (
              <div className="share-flow__review">
                <p className="share-flow__summary">
                  {t(level === 'MANAGE' ? 'sharing.share.reviewMANAGE' : 'sharing.share.reviewVIEW', {
                    name,
                    areas: joinList(chosenScopes.map(scopeNoun)),
                  })}
                </p>
                <ul className="share-flow__chips" role="list">
                  {chosenScopes.map((scope) => (
                    <li key={scope} className="sharing-chip">
                      {scopeLabel(scope)}
                    </li>
                  ))}
                </ul>
                <p className="sharing-hint">{t('sharing.share.reviewNote')}</p>
              </div>
            )}
          </StepFrame>
        )}

        {blocker && (
          <p className="form-error" role="alert">
            {t(blocker)}
          </p>
        )}
        {shareError && (
          <p className="form-error" role="alert">
            {shareError}
          </p>
        )}

        <div className="sheet__actions">
          <Button variant="ghost" onClick={goBack} disabled={share.isPending}>
            {stepIndex === 0 ? t('action.cancel') : t('action.back')}
          </Button>
          {isLast ? (
            <Button onClick={() => share.mutate()} loading={share.isPending}>
              {t('sharing.share.confirm')}
            </Button>
          ) : (
            <Button onClick={goNext} disabled={step === 'SCOPES' && offered === null}>
              {t('action.continue')}
            </Button>
          )}
        </div>
      </div>
    </BottomSheet>
  );
}

/** A step's heading (focus target on step change) and the id its fieldset is labelled by. */
function StepFrame({
  headingRef,
  heading,
  children,
}: {
  headingRef: RefObject<HTMLHeadingElement>;
  heading: string;
  children: (headingId: string) => ReactNode;
}) {
  const headingId = useId();
  return (
    <div className="share-flow__step">
      <h3 id={headingId} ref={headingRef} tabIndex={-1} className="share-flow__heading">
        {heading}
      </h3>
      {children(headingId)}
    </div>
  );
}

function ScopeChooser({
  legendId,
  offered,
  grants,
  audienceId,
  selected,
  onToggle,
  name,
}: {
  legendId: string;
  offered: SharingScope[];
  grants: AccessGrant[];
  audienceId: string;
  selected: ReadonlySet<SharingScope>;
  onToggle: (scope: SharingScope) => void;
  name: string;
}) {
  const choices = scopeChoices(offered, grants, audienceId);
  const available = choices.filter((c) => !c.alreadyShared);

  if (available.length === 0) {
    return <p className="sharing-hint">{t('sharing.share.nothingLeft', { name })}</p>;
  }

  return (
    <fieldset className="sharing-options" aria-labelledby={legendId}>
      {choices.map(({ scope, alreadyShared }) => (
        <label
          key={scope}
          className={alreadyShared ? 'sharing-option sharing-option--disabled' : 'sharing-option'}
        >
          <input
            type="checkbox"
            checked={alreadyShared || selected.has(scope)}
            disabled={alreadyShared}
            onChange={() => onToggle(scope)}
          />
          <span className="sharing-option__text">
            <span className="sharing-option__label">
              {scopeLabel(scope)}
              {alreadyShared && <span className="sharing-option__badge">{t('sharing.share.alreadyShared')}</span>}
            </span>
            <span className="sharing-option__hint">{t(SCOPE_HINT_KEYS[scope])}</span>
          </span>
        </label>
      ))}
      <p className="sharing-hint">{t('sharing.share.scopesHint')}</p>
    </fieldset>
  );
}

function errorText(error: unknown): string {
  const key = grantErrorKey(error);
  if (key) return t(key);
  return error instanceof ApiError ? error.message : t('error.internal');
}
