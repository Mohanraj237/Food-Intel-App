import { t } from '@/lib/i18n';
import type { GoalProgress } from '@/lib/goals';
import { formatAmount, formatMeasure, formatMetricMeasure } from '@/lib/nutrition';
import { metricName } from '@/lib/metricDisplay';
import { DIRECTION_KEYS, PERIOD_KEYS, metricLabelKey } from './goalOptions';
import './Goals.css';
import './GoalMeter.css';

/**
 * Where one goal stands, from recorded data only (spec §6.5) — shared by the goals list, a Family
 * card and a member's page so the three can never phrase the same goal differently.
 *
 * <p>No score, ring, streak or badge. The only status words are plain comparisons of the
 * person's own number with the number they set. For a child there is no "Reached" and no colour
 * change (spec §6.5, D-036): progress is described, not scored.
 */

/** Actual and target in the same spoken unit: "2.2 of 2.5 L", "6 h 40 min of 9 h", "7200 of 8000 steps". */
export function goalAmounts(actual: number, target: number, unit: string): { actual: string; target: string } {
  if (unit === 'ml' && target >= 1000) {
    const litres = (ml: number) => formatAmount(Number((ml / 1000).toFixed(2)));
    return { actual: litres(actual), target: t('unit.litres', { value: litres(target) }) };
  }
  if (unit === 'min' && target >= 60) {
    return { actual: formatMetricMeasure(actual, unit), target: formatMetricMeasure(target, unit) };
  }
  return { actual: formatAmount(actual), target: formatMeasure(target, unit) };
}

export interface GoalStatus {
  /** The one sentence about where the goal stands. */
  status: string;
  /** "Reached" / "Above the number you set" — a plain comparison, adults only. */
  fact: string | null;
  /** Bar fill, 0..∞ (clamped when drawn); `null` when this goal shape has no bar. */
  fraction: number | null;
  /** Nothing logged in the window: a sentence, never a zero on a bar. */
  none: boolean;
  /** Neutral fill: an "at most" or a child's goal never turns into a colour signal. */
  neutral: boolean;
}

export function goalStatus(p: GoalProgress, { child = false }: { child?: boolean } = {}): GoalStatus {
  const actual = p.dataAvailable && typeof p.actual === 'number' ? p.actual : null;
  if (actual === null) {
    return {
      status: p.period === 'DAILY' ? t('goal.nothingToday') : t('goal.nothingWeek'),
      fact: null,
      fraction: null,
      none: true,
      neutral: true,
    };
  }
  const amounts = goalAmounts(actual, p.target, p.unit);
  const fraction = p.target > 0 ? actual / p.target : null;

  if (child) {
    const aimKey = p.direction === 'AT_MOST' ? 'goal.aim.AT_MOST' : p.direction === 'MAINTAIN' ? 'goal.aim.MAINTAIN' : 'goal.aim.AT_LEAST';
    const actualWithUnit = formatMetricMeasure(actual, p.unit);
    return {
      status: t('goal.childProgress', { actual: actualWithUnit, aim: t(aimKey, { target: amounts.target }) }),
      fact: null,
      fraction: p.aggregation === 'SUM' && p.direction === 'AT_LEAST' ? fraction : null,
      none: false,
      neutral: true,
    };
  }
  if (p.aggregation === 'LATEST') {
    // A body measurement: the latest reading and the number set — no bar, no "to go" (spec §6.5).
    return {
      status: t('goal.latestReading', { actual: formatAmount(actual), unit: p.unit }),
      fact: null,
      fraction: null,
      none: false,
      neutral: true,
    };
  }
  if (p.direction === 'AT_LEAST') {
    return {
      status: t('goal.progressOfFormatted', amounts),
      fact: actual >= p.target ? t('goal.reached') : null,
      fraction,
      none: false,
      neutral: false,
    };
  }
  if (p.direction === 'AT_MOST') {
    return {
      status: t('goal.soFarFormatted', { actual: formatMetricMeasure(actual, p.unit) }),
      fact: actual > p.target ? t('goal.above') : null,
      fraction,
      none: false,
      neutral: true,
    };
  }
  return {
    status: t('goal.aroundFormatted', { actual: formatMetricMeasure(actual, p.unit), target: amounts.target }),
    fact: null,
    fraction: null,
    none: false,
    neutral: true,
  };
}

export function goalMetricLabel(code: string): string {
  const key = metricLabelKey(code);
  return key ? t(key) : metricName(code);
}

interface GoalMeterProps {
  progress: GoalProgress;
  child?: boolean;
  /** `compact`: one status line and a thin bar, for a Family card row. */
  variant?: 'full' | 'compact';
}

export function GoalMeter({ progress: p, child = false, variant = 'full' }: GoalMeterProps) {
  const s = goalStatus(p, { child });
  const bar = s.fraction !== null && (
    // The words carry the meaning; the bar is a glance aid, hidden from assistive technology
    // rather than announced as a second, vaguer version of the same sentence.
    <div className={`goal-meter__bar${s.neutral ? ' goal-meter__bar--neutral' : ''}`} aria-hidden="true">
      <span style={{ width: `${Math.min(s.fraction, 1) * 100}%` }} />
    </div>
  );

  if (variant === 'compact') {
    return (
      <div className="goal-meter goal-meter--compact">
        {bar}
        <span className={s.none ? 'goal-meter__status goal-meter__status--none' : 'goal-meter__status'}>
          {s.status}
          {p.period === 'WEEKLY' && <span className="goal-meter__window"> · {t('goal.windowWeek')}</span>}
          {s.fact && <span className="goal-meter__fact">{s.fact}</span>}
        </span>
      </div>
    );
  }

  const targetText =
    p.aggregation === 'LATEST'
      ? t('goal.targetLatest', { direction: t(DIRECTION_KEYS[p.direction]), target: formatAmount(p.target), unit: p.unit })
      : t('goal.targetFormatted', {
          direction: t(DIRECTION_KEYS[p.direction]),
          target: goalAmounts(p.target, p.target, p.unit).target,
          period: t(PERIOD_KEYS[p.period]),
        });

  return (
    <div className="goal__body">
      <div className="goal__head">
        <span className="goal__metric">{goalMetricLabel(p.metricCode)}</span>
        <span className="goal__window">{p.period === 'DAILY' ? t('goal.windowToday') : t('goal.windowWeek')}</span>
      </div>
      <p className="goal__target">{targetText}</p>
      <p className={s.none ? 'goal__status goal__status--none' : 'goal__status'}>
        {s.status}
        {s.fact && <span className="goal__fact">{s.fact}</span>}
      </p>
      {bar}
    </div>
  );
}
