import { useId } from 'react';
import './WeekStrip.css';

export interface WeekStripPoint {
  /** `YYYY-MM-DD`. */
  date: string;
  /** `null` is a day with nothing logged — drawn as a gap, never as a zero (spec §6.3). */
  value: number | null;
  /** The second reading of a pair — a blood pressure's bottom number — drawn hollow. */
  pair?: number | null;
  /** What hovering the day says: "Thu 10 Sep: 8100 steps". */
  label: string;
}

interface WeekStripProps {
  /** Oldest first. */
  points: readonly WeekStripPoint[];
  /** SUM (water, steps) draws bars from zero; LATEST (weight, a reading) draws dots. */
  aggregation: 'SUM' | 'LATEST';
  /** The day being viewed: drawn solid, the others softened. One hue either way (spec A.1). */
  selectedDate?: string;
  /** A dashed goal line — a daily SUM goal only, never a reading (spec §4.10, D-037). */
  goalValue?: number | null;
  /** The strip's accessible name (`<title>`). */
  title: string;
  /** The one sentence a screen reader gets instead of seven marks (`<desc>`, spec §6.4). */
  summary: string;
  size?: 'sm' | 'md';
  /** Stretch to the container's width (bars only — dots would distort). */
  fluid?: boolean;
}

const SIZES = {
  sm: { slot: 14, height: 30, dot: 2.5 },
  md: { slot: 22, height: 44, dot: 3.5 },
} as const;

const BAR_RATIO = 0.62;

/**
 * A week at a glance, small enough to sit beside a number (spec §4.4, §7.2's "inline SVG, no
 * charting library"). Seven slots, one per day; the chart is a picture of the summary sentence,
 * which is what assistive technology gets. Individual marks are not focusable (spec §7.1); each
 * carries a `<title>` so hovering a day says what it holds.
 *
 * <p>What it never draws: a zero-height bar for a day nobody logged, a line across a gap, a
 * range band, a reference line, or any second colour — members are never charted together, so
 * nothing here needs to be told apart by hue (spec §6.7, A.1). Blood pressure is two dots per
 * day, joined, with no band (D-037).
 */
export function WeekStrip({
  points,
  aggregation,
  selectedDate,
  goalValue,
  title,
  summary,
  size = 'sm',
  fluid = false,
}: WeekStripProps) {
  const titleId = useId();
  const descId = useId();
  const { slot, height, dot } = SIZES[size];
  const width = Math.max(points.length, 1) * slot;
  const baseline = height - 1;

  const readings = points.flatMap((p) => [p.value, p.pair ?? null]).filter((v): v is number => v !== null);

  return (
    <figure className={`week-strip week-strip--${size}${fluid ? ' week-strip--fluid' : ''}`}>
      <svg
        className="week-strip__svg"
        viewBox={`0 0 ${width} ${height}`}
        width={fluid ? '100%' : width}
        height={height}
        preserveAspectRatio={fluid ? 'none' : 'xMidYMid meet'}
        role="img"
        aria-labelledby={titleId}
        aria-describedby={descId}
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{summary}</desc>

        <line
          className="week-strip__baseline"
          x1={0}
          x2={width}
          y1={baseline}
          y2={baseline}
          vectorEffect="non-scaling-stroke"
        />

        {aggregation === 'SUM'
          ? renderBars(points, { slot, height, baseline, selectedDate, goalValue: goalValue ?? null, width })
          : renderDots(points, readings, { slot, height, dot, selectedDate })}
      </svg>
    </figure>
  );
}

function slotClass(point: WeekStripPoint, selectedDate: string | undefined): string {
  return point.date === selectedDate || selectedDate === undefined
    ? 'week-strip__mark'
    : 'week-strip__mark week-strip__mark--soft';
}

function renderBars(
  points: readonly WeekStripPoint[],
  geo: { slot: number; height: number; baseline: number; selectedDate: string | undefined; goalValue: number | null; width: number },
) {
  const values = points.map((p) => p.value).filter((v): v is number => v !== null);
  const top = Math.max(...values, geo.goalValue ?? 0, 0) * 1.1 || 1;
  const usable = geo.baseline - 2;
  const barWidth = geo.slot * BAR_RATIO;
  const goalY = geo.goalValue !== null && geo.goalValue > 0 ? geo.baseline - (geo.goalValue / top) * usable : null;

  return (
    <>
      {points.map((point, i) => {
        const x = i * geo.slot + (geo.slot - barWidth) / 2;
        if (point.value === null) {
          return (
            <g key={point.date} data-day={point.date} data-logged="false">
              <title>{point.label}</title>
              <rect className="week-strip__tick" x={x + barWidth / 2 - 2} y={geo.baseline - 1.5} width={4} height={1.5} />
            </g>
          );
        }
        // A recorded 0 still gets a sliver, so "0 steps" and "not logged" never look the same.
        const barHeight = Math.max((point.value / top) * usable, 2);
        return (
          <g key={point.date} data-day={point.date} data-logged="true" className={slotClass(point, geo.selectedDate)}>
            <title>{point.label}</title>
            <rect className="week-strip__bar" x={x} y={geo.baseline - barHeight} width={barWidth} height={barHeight} rx={1.5} />
          </g>
        );
      })}
      {goalY !== null && (
        <line
          className="week-strip__goal"
          x1={0}
          x2={geo.width}
          y1={goalY}
          y2={goalY}
          vectorEffect="non-scaling-stroke"
        />
      )}
    </>
  );
}

function renderDots(
  points: readonly WeekStripPoint[],
  readings: readonly number[],
  geo: { slot: number; height: number; dot: number; selectedDate: string | undefined },
) {
  // A reading's axis fits the data with 10% padding (spec §4.10) — never pinned to a reference
  // range, which would be an interpretation (D-037).
  const lo = Math.min(...readings);
  const hi = Math.max(...readings);
  const pad = hi === lo ? Math.max(Math.abs(hi) * 0.1, 1) : (hi - lo) * 0.1;
  const min = lo - pad;
  const max = hi + pad;
  const inset = geo.dot + 1;
  const y = (v: number) => inset + (1 - (v - min) / (max - min)) * (geo.height - 2 * inset - 1);

  return points.map((point, i) => {
    const cx = i * geo.slot + geo.slot / 2;
    const hasPair = point.pair !== undefined && point.pair !== null;
    if (point.value === null && !hasPair) {
      return (
        <g key={point.date} data-day={point.date} data-logged="false">
          <title>{point.label}</title>
          <rect className="week-strip__tick" x={cx - 2} y={geo.height - 2.5} width={4} height={1.5} />
        </g>
      );
    }
    return (
      <g key={point.date} data-day={point.date} data-logged="true" className={slotClass(point, geo.selectedDate)}>
        <title>{point.label}</title>
        {point.value !== null && hasPair && (
          <line className="week-strip__pair-link" x1={cx} x2={cx} y1={y(point.value)} y2={y(point.pair as number)} />
        )}
        {point.value !== null && <circle className="week-strip__dot" cx={cx} cy={y(point.value)} r={geo.dot} />}
        {hasPair && (
          <circle className="week-strip__dot week-strip__dot--hollow" cx={cx} cy={y(point.pair as number)} r={geo.dot} />
        )}
      </g>
    );
  });
}
