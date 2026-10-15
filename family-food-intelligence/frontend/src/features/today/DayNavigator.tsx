import { t, getLocale, type MessageKey } from '@/lib/i18n';
import { formatDayLabel } from './timezone';
import './DayNavigator.css';

interface DayNavigatorProps {
  /** `YYYY-MM-DD`, the day currently shown. */
  dateKey: string;
  /** `YYYY-MM-DD`, today in the household's own zone — the ceiling "Next" can reach. */
  todayKey: string;
  onPrevious: () => void;
  onNext: () => void;
}

/**
 * "‹ Sat, 12 Sep ›" (plan §18 1.1) — past dates only, per the plan's own explicit rule: `/today`
 * is never asked for a future day (the server 400s one anyway), so "Next" simply disables at
 * the household's own today rather than letting someone reach a day with nothing to show.
 */
export function DayNavigator({ dateKey, todayKey, onPrevious, onNext }: DayNavigatorProps) {
  const isToday = dateKey === todayKey;
  const label = isToday ? t('today.dateToday' as MessageKey) : formatDayLabel(dateKey, getLocale());

  return (
    <div className="day-nav">
      <button type="button" className="day-nav__btn" aria-label={t('today.previousDay' as MessageKey)} onClick={onPrevious}>
        <span aria-hidden="true">‹</span>
      </button>
      <span className="day-nav__label">{label}</span>
      <button
        type="button"
        className="day-nav__btn"
        aria-label={t('today.nextDay' as MessageKey)}
        disabled={isToday}
        onClick={onNext}
      >
        <span aria-hidden="true">›</span>
      </button>
    </div>
  );
}
