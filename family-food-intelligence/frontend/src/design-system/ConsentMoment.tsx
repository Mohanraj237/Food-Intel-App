import { useId, type ReactNode } from 'react';
import { t } from '@/lib/i18n';
import { Button } from './Button';
import './ConsentMoment.css';

interface ConsentMomentProps {
  title: string;
  /** 2-3 plain bullets — what turning it on actually does, no jargon. */
  bullets: readonly string[];
  onGrant: () => void;
  onNotNow: () => void;
  granting?: boolean;
  /** The child variant adds a guardian attestation checkbox and requires it before Grant is
   *  enabled — a guardian must actively confirm, never a pre-ticked box. */
  variant?: 'adult' | 'child';
  attestationLabel?: string;
  attested?: boolean;
  onAttestedChange?: (checked: boolean) => void;
  grantLabel?: string;
  notNowLabel?: string;
  error?: ReactNode;
}

/**
 * The in-context consent ask (plan §8.2/§2.4) — a designed card at the moment tracking is first
 * needed, never a settings-section trip. Grant and "Not now" are the same `Button` variant, size
 * and width, side by side: nothing here is allowed to look like the "real" choice while the
 * other looks like an afterthought.
 */
export function ConsentMoment({
  title,
  bullets,
  onGrant,
  onNotNow,
  granting = false,
  variant = 'adult',
  attestationLabel,
  attested = false,
  onAttestedChange,
  grantLabel,
  notNowLabel,
  error,
}: ConsentMomentProps) {
  const checkboxId = useId();
  const grantDisabled = variant === 'child' && !attested;

  return (
    <div className="consent-moment" role="note">
      <p className="consent-moment__title">{title}</p>
      <ul className="consent-moment__bullets" role="list">
        {bullets.map((bullet, i) => (
          <li key={i}>{bullet}</li>
        ))}
      </ul>

      {variant === 'child' && onAttestedChange && (
        <label className="consent-moment__attestation">
          <input
            id={checkboxId}
            type="checkbox"
            checked={attested}
            onChange={(e) => onAttestedChange(e.target.checked)}
          />
          <span>{attestationLabel ?? t('consentMoment.attestationDefault')}</span>
        </label>
      )}

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      <div className="consent-moment__actions">
        <Button variant="secondary" fullWidth loading={granting} disabled={grantDisabled} onClick={onGrant}>
          {grantLabel ?? t('consentMoment.grant')}
        </Button>
        <Button variant="secondary" fullWidth disabled={granting} onClick={onNotNow}>
          {notNowLabel ?? t('consentMoment.notNow')}
        </Button>
      </div>
    </div>
  );
}
