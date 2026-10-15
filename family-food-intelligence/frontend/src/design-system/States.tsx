import type { ReactNode } from 'react';
import { t } from '@/lib/i18n';
import { Button } from './Button';
import { Icon, type IconName } from './Icon';
import { ApiError } from '@/lib/api';
import './States.css';

/** Matches the final layout's boxes so nothing jumps when content arrives. */
export function Skeleton({ lines = 3, label }: { lines?: number; label?: string }) {
  return (
    <div className="skeleton" aria-busy="true" aria-live="polite">
      <span className="sr-only">{label ?? t('loading.default')}</span>
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className="skeleton__line" aria-hidden="true" />
      ))}
    </div>
  );
}

/**
 * A designed empty state: an icon on a soft disc, one sentence, and the next step. Never a
 * blank area, never an error styled as one.
 */
export function EmptyState({
  title,
  body,
  icon = 'sparkles',
  action,
  compact = false,
}: {
  title: string;
  body?: string;
  icon?: IconName;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={compact ? 'state state--empty state--compact' : 'state state--empty'}>
      <span className="state__icon">
        <Icon name={icon} size={compact ? 20 : 26} />
      </span>
      <div className="state__text">
        <p className="state__title">{title}</p>
        {body && <p className="state__message">{body}</p>}
      </div>
      {action && <div className="state__action">{action}</div>}
    </div>
  );
}

/**
 * Renders an error honestly. A transport failure and a 404 look different and say different
 * things: one means "we could not reach our service", the other means "we do not have this".
 */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null;
  const message = apiError ? apiError.message : t('error.internal');
  const canRetry = onRetry && (apiError?.isRetryable ?? true);

  return (
    <div className="state state--error" role="alert">
      <span className="state__icon">
        <Icon name="alert" size={24} />
      </span>
      <div className="state__text">
        <p className="state__title">{t('error.title')}</p>
        <p className="state__message">{message}</p>
        {apiError?.correlationId && (
          <p className="state__reference">{t('error.reference', { correlationId: apiError.correlationId })}</p>
        )}
      </div>
      {canRetry && (
        <div className="state__action">
          <Button variant="secondary" size="sm" onClick={onRetry}>
            {t('action.retry')}
          </Button>
        </div>
      )}
    </div>
  );
}
