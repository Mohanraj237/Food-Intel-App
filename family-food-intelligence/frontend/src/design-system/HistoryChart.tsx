import { useId } from 'react';
import { t } from '@/lib/i18n';
import './HistoryChart.css';

export interface HistoryPoint {
  /** `YYYY-MM-DD`. */
  date: string;
  /** `null` is a genuine gap — the day was not logged. Never substitute 0 (plan §18 1.4). */
  value: number | null;
  /** How `date` reads to a person ("Mon 8 Sep") — the caller already knows the locale/format. */
  label: string;
  /** The second number of a paired reading — a blood pressure's bottom number — drawn hollow. */
  pair?: number | null;
}

interface HistoryChartProps {
  /** Chronological order, oldest first. */
  points: readonly HistoryPoint[];
  unit: string;
  /** SUM (water, steps) draws bars; LATEST (weight, a reading) draws dots. */
  aggregation: 'SUM' | 'LATEST';
  /** A dashed goal line — SUM goals only (plan §9's `--chart-goal-line`). */
  goalValue?: number | null;
  /** The chart's accessible name (`<svg role="img">`'s title). */
  title: string;
  /** A text summary standing in for the chart's `<desc>` — the one sentence a screen reader
   *  gets instead of trying to read 30 bars. */
  summary: string;
  /** Shown instead of any chart when every point is `null`. */
  emptyText?: string;
  /** How a day reads in the table ("2.2 L", "132/84 mmHg"); defaults to "{value} {unit}". */
  formatValue?: (point: HistoryPoint) => string;
}

const CHART_HEIGHT = 140;
const BAR_GAP_RATIO = 0.35;

/**
 * Tracker-history chart (plan §8.2/§18 1.4) — inline SVG, no charting library. Gaps for
 * not-logged days are drawn as nothing, never as a zero-height bar (the same "missing is not
 * zero" rule `lib/dashboard.ts` states for the day view). A "Show as table" toggle always sits
 * beside the chart, opening a real `<table>` with the same numbers — the chart is a picture of
 * the table, never the only way to get at the data (WCAG 1.1.1 by construction, not as a bolt-on).
 */
export function HistoryChart({
  points,
  unit,
  aggregation,
  goalValue,
  title,
  summary,
  emptyText,
  formatValue,
}: HistoryChartProps) {
  const titleId = useId();
  const descId = useId();
  const tableId = useId();

  const hasAnyValue = points.some((p) => p.value !== null || (p.pair !== undefined && p.pair !== null));
  if (!hasAnyValue) {
    return (
      <div className="history-chart history-chart--empty">
        <p className="history-chart__empty-text">{emptyText ?? t('historyChart.emptyDefault')}</p>
      </div>
    );
  }

  const values = points.flatMap((p) => [p.value, p.pair ?? null]).filter((v): v is number => v !== null);
  const max = Math.max(...values, goalValue ?? 0) * 1.1 || 1;
  const width = Math.max(points.length * 32, 240);
  const barWidth = (width / points.length) * (1 - BAR_GAP_RATIO);
  const goalY = goalValue != null ? CHART_HEIGHT - (goalValue / max) * CHART_HEIGHT : null;
  // Bars start at zero; a reading's axis fits the data with 10% padding (spec §4.10), so 84–132
  // isn't squashed into the top third — and is never pinned to a reference range (D-037).
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const pad = hi === lo ? Math.max(Math.abs(hi) * 0.1, 1) : (hi - lo) * 0.1;
  const dotY = (v: number) => 6 + (1 - (v - (lo - pad)) / (hi - lo + 2 * pad)) * (CHART_HEIGHT - 12);

  return (
    <figure className="history-chart">
      <svg
        className="history-chart__svg"
        viewBox={`0 0 ${width} ${CHART_HEIGHT}`}
        role="img"
        aria-labelledby={titleId}
        aria-describedby={descId}
        preserveAspectRatio="xMinYMid meet"
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{summary}</desc>

        {goalY !== null && (
          <line
            x1={0}
            x2={width}
            y1={goalY}
            y2={goalY}
            style={{ stroke: 'var(--chart-goal-line)' }}
            strokeWidth={1.5}
            strokeDasharray="4 4"
          />
        )}

        {aggregation === 'SUM'
          ? points.map((point, i) => {
              if (point.value === null) return null;
              const barHeight = (point.value / max) * CHART_HEIGHT;
              const x = i * (width / points.length) + (width / points.length - barWidth) / 2;
              return (
                <rect
                  key={point.date}
                  x={x}
                  y={CHART_HEIGHT - barHeight}
                  width={barWidth}
                  height={Math.max(barHeight, 2)}
                  style={{ fill: 'var(--chart-bar)' }}
                  rx={2}
                />
              );
            })
          : points.map((point, i) => {
              const cx = i * (width / points.length) + width / points.length / 2;
              const y = dotY;
              const pair = point.pair ?? null;
              if (point.value === null && pair === null) return null;
              // A paired reading is two dots joined, the bottom number hollow — one hue, no band,
              // no reference line (D-037).
              return (
                <g key={point.date}>
                  {point.value !== null && pair !== null && (
                    <line
                      x1={cx}
                      x2={cx}
                      y1={y(point.value)}
                      y2={y(pair)}
                      style={{ stroke: 'var(--chart-bar)', opacity: 0.5 }}
                      strokeWidth={2}
                    />
                  )}
                  {point.value !== null && <circle cx={cx} cy={y(point.value)} r={4} style={{ fill: 'var(--chart-bar)' }} />}
                  {pair !== null && (
                    <circle
                      cx={cx}
                      cy={y(pair)}
                      r={4}
                      style={{ fill: 'var(--card-bg)', stroke: 'var(--chart-bar)' }}
                      strokeWidth={2}
                    />
                  )}
                </g>
              );
            })}

        <line
          x1={0}
          x2={width}
          y1={CHART_HEIGHT}
          y2={CHART_HEIGHT}
          style={{ stroke: 'var(--chart-grid)' }}
          strokeWidth={1}
        />
      </svg>

      <details className="history-chart__table-toggle">
        <summary>{t('historyChart.showAsTable')}</summary>
        <table id={tableId} className="history-chart__table">
          <caption className="sr-only">{title}</caption>
          <thead>
            <tr>
              <th scope="col">{t('historyChart.tableDate')}</th>
              <th scope="col">{t('historyChart.tableValue', { unit })}</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.date}>
                <td>{point.label}</td>
                <td>
                  {point.value === null && (point.pair ?? null) === null
                    ? t('progressTile.notLogged')
                    : formatValue
                      ? formatValue(point)
                      : `${point.value} ${unit}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
