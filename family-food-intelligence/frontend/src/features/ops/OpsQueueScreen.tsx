import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { listQueue, type ContributionQueueEntry } from '@/lib/ops';
import { ErrorState, Skeleton } from '@/design-system/States';
import './OpsScreens.css';

/**
 * The ops-review queue (docs/product/post-mvp-backlog.md's "correction queue ops console"
 * item). Deliberately minimal — a list, a status filter, a link to each detail/review
 * screen. The backend re-checks the caller's operator role on every request regardless of
 * how this screen got reached (D-032); a non-operator simply sees an access-denied error
 * here rather than any client-side gate being the real boundary.
 */
export function OpsQueueScreen() {
  const [status, setStatus] = useState<'PENDING' | 'APPROVED' | 'REJECTED'>('PENDING');

  const queue = useQuery({
    queryKey: ['ops', 'queue', status],
    queryFn: () => listQueue(status),
  });

  return (
    <div className="page ops">
      <h1>{t('ops.queueHeading')}</h1>

      <div className="ops__filter" role="tablist" aria-label={t('ops.queueHeading')}>
        {(['PENDING', 'APPROVED', 'REJECTED'] as const).map((s) => (
          <button
            key={s}
            id={`ops-tab-${s}`}
            type="button"
            role="tab"
            aria-selected={status === s}
            aria-controls="ops-queue-panel"
            className={status === s ? 'settings__tab settings__tab--active' : 'settings__tab'}
            onClick={() => setStatus(s)}
          >
            {t(`ops.status.${s}` as Parameters<typeof t>[0])}
          </button>
        ))}
      </div>

      <div id="ops-queue-panel" role="tabpanel" aria-labelledby={`ops-tab-${status}`}>
        {queue.isPending && <Skeleton lines={4} label={t('loading.default')} />}
        {queue.isError && <ErrorState error={queue.error} onRetry={() => void queue.refetch()} />}
        {queue.isSuccess && queue.data.length === 0 && (
          <p className="ops__empty">{t('ops.queueEmpty')}</p>
        )}
        {queue.isSuccess && queue.data.length > 0 && (
          <ul className="ops__list">
            {queue.data.map((entry) => (
              <QueueRow key={entry.id} entry={entry} />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function QueueRow({ entry }: { entry: ContributionQueueEntry }) {
  return (
    <li className="ops__row">
      <Link to={`/ops/${entry.id}`} className="ops__row-link">
        <span className="ops__row-name">
          {entry.brandName ? `${entry.brandName} — ${entry.productName}` : entry.productName}
        </span>
        <span className="ops__row-meta">
          {entry.gtin && `${entry.gtin} · `}
          {entry.categoryCode ?? t('profile.notSpecified')} ·{' '}
          {new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(
            new Date(entry.submittedAt),
          )}
        </span>
      </Link>
    </li>
  );
}
