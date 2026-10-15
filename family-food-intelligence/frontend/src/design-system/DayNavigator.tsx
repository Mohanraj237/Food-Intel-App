import { t } from '@/lib/i18n';
import './DayNavigator.css';

interface DayNavigatorProps {
  /** The day being viewed, already formatted ("Sat, 12 Sep"). */
  label: string;
  /** Today where the household lives (D-053) — there is no "next" from today. */
  isToday: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onToday: () => void;
}

/**
 * ‹ date › (spec Appendix A). Buttons named "Previous day" / "Next day"; › is disabled on today,
 * because a day that hasn't happened has nothing in it and would read as "nothing logged". The
 * label is a polite live region, so stepping announces the new day without moving focus.
 */
export function DayNavigator({ label, isToday, onPrevious, onNext, onToday }: DayNavigatorProps) {
  return (
    <div className="day-nav" role="group" aria-label={t('dayNavigator.label')}>
      <button type="button" className="day-nav__step" onClick={onPrevious} aria-label={t('dayNavigator.previous')}>
        <span aria-hidden="true">‹</span>
      </button>
      <span className="day-nav__label" aria-live="polite">
        {label}
      </span>
      <button
        type="button"
        className="day-nav__step"
        onClick={onNext}
        disabled={isToday}
        aria-label={t('dayNavigator.next')}
      >
        <span aria-hidden="true">›</span>
      </button>
      {!isToday && (
        <button type="button" className="day-nav__today" onClick={onToday}>
          {t('dayNavigator.today')}
        </button>
      )}
    </div>
  );
}
