import type { ReactNode } from 'react';
import { t } from '@/lib/i18n';
import { Button } from './Button';
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

export function EmptyState({
  title,
  action,
}: {
  title: string;
  action?: ReactNode;
}) {
  return (
    <div className="state state--empty">
      <p className="state__title">{title}</p>
      {action}
    </div>
  );
}

/**
 * Renders an error honestly.
 *
 * A transport failure and a 404 look different and say different things: one
 * means "we could not reach our service", the other means "we do not have this".
 * Collapsing them would tell a user a product does not exist when the network
 * merely dropped (Constitution §46, AC-10.3).
 */
export function ErrorState({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry?: () => void;
}) {
  const apiError = error instanceof ApiError ? error : null;
  const message = apiError ? apiError.message : t('error.internal');
  const canRetry = onRetry && (apiError?.isRetryable ?? true);

  return (
    <div className="state state--error" role="alert">
      <p className="state__title">{t('error.title')}</p>
      <p className="state__message">{message}</p>
      {apiError?.correlationId && (
        <p className="state__reference">
          {t('error.reference', { correlationId: apiError.correlationId })}
        </p>
      )}
      {canRetry && (
        <Button variant="secondary" onClick={onRetry}>
          {t('action.retry')}
        </Button>
      )}
    </div>
  );
}
