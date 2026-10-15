import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { Skeleton } from './States';
import { Icon, type IconName } from './Icon';
import './ProgressTile.css';

interface ProgressTileProps {
  metricLabel: string;
  historyHref: string;
  onLog: () => void;
  logLabel?: string;
  state: 'loading' | 'logged' | 'not-logged';
  /** The day's value, already formatted ("1.5 of 2 L", "1,420 kcal"). */
  valueText?: string;
  /** A goal-context or status line under the value. */
  subLine?: string;
  /** Leading icon that gives the tile an identity at a glance. */
  icon?: IconName;
  /** 0..1 progress towards a goal; draws a quiet bar. Omit when there is no goal. */
  progress?: number | null;
}

/**
 * Today's per-metric tile: an icon, the metric, the day's number, and a **+** to log more. The
 * whole tile is a link to the metric's history; the **+** is a sibling button, never nested.
 */
export function ProgressTile({
  metricLabel,
  historyHref,
  onLog,
  logLabel,
  state,
  valueText,
  subLine,
  icon,
  progress,
}: ProgressTileProps) {
  if (state === 'loading') {
    return (
      <li className="progress-tile progress-tile--loading">
        <span className="progress-tile__label">{metricLabel}</span>
        <Skeleton lines={2} label={t('loading.default')} />
      </li>
    );
  }

  const logged = state === 'logged' && valueText !== undefined;
  const linkName = logged
    ? t('progressTile.openHistoryLogged', { metric: metricLabel, value: valueText })
    : t('progressTile.openHistoryNotLogged', { metric: metricLabel });
  const fraction = typeof progress === 'number' ? Math.max(0, Math.min(progress, 1)) : null;

  return (
    <li className={logged ? 'progress-tile' : 'progress-tile progress-tile--empty'}>
      <Link to={historyHref} className="progress-tile__link" aria-label={linkName}>
        <span className="progress-tile__head" aria-hidden="true">
          {icon && (
            <span className="progress-tile__icon">
              <Icon name={icon} size={18} />
            </span>
          )}
          <span className="progress-tile__label">{metricLabel}</span>
        </span>
        {logged ? (
          <>
            <span className="progress-tile__value" aria-hidden="true">
              {valueText}
            </span>
            {subLine && (
              <span className="progress-tile__subline" aria-hidden="true">
                {subLine}
              </span>
            )}
          </>
        ) : (
          <span className="progress-tile__subline progress-tile__subline--muted" aria-hidden="true">
            {t('progressTile.notLogged')}
          </span>
        )}
        {fraction !== null && (
          <span className="progress-tile__bar" aria-hidden="true">
            <span style={{ width: `${fraction * 100}%` }} />
          </span>
        )}
      </Link>
      <button
        type="button"
        className="progress-tile__log"
        aria-label={logLabel ?? t('progressTile.logAction', { metric: metricLabel })}
        onClick={onLog}
      >
        <Icon name="plus" size={18} strokeWidth={2.25} />
      </button>
    </li>
  );
}
