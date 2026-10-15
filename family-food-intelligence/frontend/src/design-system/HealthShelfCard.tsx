import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { Skeleton } from './States';
import { Button } from './Button';
import './HealthShelfCard.css';

interface HealthShelfCardProps {
  /** e.g. "Next dose" — never a severity-graded label. */
  label: string;
  /** e.g. "Metformin" */
  fact: string;
  /** e.g. "8pm", "Fri" */
  dateTime?: string;
  href?: string;
  state?: 'ready' | 'loading' | 'empty';
  emptyText?: string;
  emptyActionLabel?: string;
  onEmptyAction?: () => void;
}

/**
 * One card per record/medication/appointment (plan §8.2/§18 4.2) — "your health stuff", never a
 * document-management table. Icon-free, one key fact, one date/time, a plain sentence for the
 * empty case rather than a blank card. Colour never carries meaning here: there is no severity
 * ramp, on purpose (this whole surface stays behind a feature flag until D-049 clears — see
 * `lib/healthShelf.ts`).
 */
export function HealthShelfCard({
  label,
  fact,
  dateTime,
  href,
  state = 'ready',
  emptyText,
  emptyActionLabel,
  onEmptyAction,
}: HealthShelfCardProps) {
  if (state === 'loading') {
    return (
      <div className="health-shelf-card">
        <Skeleton lines={2} label={t('loading.default')} />
      </div>
    );
  }

  if (state === 'empty') {
    return (
      <div className="health-shelf-card health-shelf-card--empty">
        <p className="health-shelf-card__fact">{emptyText ?? t('healthShelfCard.emptyDefault')}</p>
        {onEmptyAction && emptyActionLabel && (
          <Button variant="secondary" size="sm" onClick={onEmptyAction}>
            {emptyActionLabel}
          </Button>
        )}
      </div>
    );
  }

  const body = (
    <>
      <p className="health-shelf-card__label">{label}</p>
      <p className="health-shelf-card__fact">{fact}</p>
      {dateTime && <p className="health-shelf-card__when">{dateTime}</p>}
    </>
  );

  if (href) {
    return (
      <Link to={href} className="health-shelf-card health-shelf-card--link">
        {body}
      </Link>
    );
  }
  return <div className="health-shelf-card">{body}</div>;
}
