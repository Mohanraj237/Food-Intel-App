import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import {
  ACCESS_QUERY_KEYS,
  grantAccess,
  grantErrorKey,
  revokeAccess,
  scopeNoun,
  type AccessGrant,
  type AccessLevel,
} from '@/lib/access';
import { BottomSheet } from '@/design-system/BottomSheet';
import { Button } from '@/design-system/Button';
import { LEVEL_HINT_KEYS, LEVEL_OPTION_KEYS } from './sharingCopy';
import { CaregiverExplainer } from './CaregiverExplainer';
import { useSheetFocusTrap } from './useSheetFocus';

const LEVELS: readonly AccessLevel[] = ['VIEW', 'MANAGE'];

function errorText(error: unknown): string {
  const key = grantErrorKey(error);
  if (key) return t(key);
  return error instanceof ApiError ? error.message : t('error.internal');
}

/**
 * Change what one person can do with one area: See, or See and log for you. The server
 * replaces the grant and keeps the history, so "from when could they log for me" stays
 * answerable (AccessGrantService.grant).
 */
export function ChangeLevelSheet({
  memberId,
  grant,
  name,
  onClose,
  onDone,
}: {
  memberId: string;
  grant: AccessGrant;
  name: string;
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const queryClient = useQueryClient();
  const [level, setLevel] = useState<AccessLevel>(grant.level);
  const anchorRef = useRef<HTMLDivElement>(null);
  useSheetFocusTrap(anchorRef);

  const save = useMutation({
    mutationFn: () => grantAccess(memberId, { audienceMemberId: grant.audienceMemberId, scope: grant.scope, level }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ACCESS_QUERY_KEYS.grants(memberId) }),
    onSuccess: () => onDone(t('sharing.changed')),
  });

  const noun = scopeNoun(grant.scope);

  return (
    <BottomSheet open onClose={onClose} title={t('sharing.changeSheet.title', { name, scope: noun })}>
      <div ref={anchorRef}>
        <fieldset className="sharing-options">
          <legend className="sr-only">{t('sharing.changeSheet.title', { name, scope: noun })}</legend>
          {LEVELS.map((option) => (
            <label key={option} className="sharing-option">
              <input
                type="radio"
                name="change-level"
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

        {level === 'MANAGE' && <CaregiverExplainer name={name} areas={noun} />}

        {save.error && (
          <p className="form-error" role="alert">
            {errorText(save.error)}
          </p>
        )}

        <div className="sheet__actions">
          <Button variant="ghost" onClick={onClose} disabled={save.isPending}>
            {t('action.cancel')}
          </Button>
          <Button
            onClick={() => (level === grant.level ? onClose() : save.mutate())}
            loading={save.isPending}
          >
            {t('action.save')}
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}

/**
 * Stop sharing one area, or everything, with one person. Confirmed, because it is a change to
 * someone else's access; but never styled as a danger — withdrawing access is the safe
 * direction, and it must never look harder than granting it.
 */
export function StopSharingSheet({
  memberId,
  grants,
  title,
  body,
  onClose,
  onDone,
}: {
  memberId: string;
  grants: AccessGrant[];
  title: string;
  body: string;
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const queryClient = useQueryClient();
  const anchorRef = useRef<HTMLDivElement>(null);
  useSheetFocusTrap(anchorRef);

  const stop = useMutation({
    mutationFn: async () => {
      const results = await Promise.allSettled(grants.map((grant) => revokeAccess(memberId, grant.id)));
      const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
      if (failed) throw failed.reason;
    },
    // Invalidated whether or not every revocation landed, so the list shows what is true now.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ACCESS_QUERY_KEYS.grants(memberId) }),
    onSuccess: () => onDone(t('sharing.stopped')),
  });

  return (
    <BottomSheet open onClose={onClose} title={title}>
      <div ref={anchorRef}>
        <p>{body}</p>
        {stop.error && (
          <p className="form-error" role="alert">
            {errorText(stop.error)}
          </p>
        )}
        <div className="sheet__actions">
          <Button variant="ghost" onClick={onClose} disabled={stop.isPending}>
            {t('action.cancel')}
          </Button>
          <Button onClick={() => stop.mutate()} loading={stop.isPending}>
            {t('sharing.stopConfirm.action')}
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}
