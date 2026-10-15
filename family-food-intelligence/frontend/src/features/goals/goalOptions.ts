import type { MessageKey } from '@/lib/i18n';
import type { Capabilities } from '@/lib/me';
import type { MetricDefinition } from '@/lib/wellness';
import type { GoalDirection, GoalPeriod, HabitCadence } from '@/lib/goals';

/**
 * Which goals a subject is offered, and the labels/periods that go with each metric.
 *
 * <p>The offer is capability-driven, not a hardcoded child/adult table (plan §18 1.3, closing
 * G-04): `offeredGoalMetrics` below intersects the server's metric catalogue with
 * `capabilities.goalCategories`/`goalDirections` from `GET /members/{id}/experience` — the same
 * capabilities the rest of the app already treats as authoritative (D-046). A child subject's
 * `goalCategories` today is ACTIVITY/SLEEP/HYDRATION and `goalDirections` is AT_LEAST/MAINTAIN
 * (D-047), which is why weight, energy and the nutrient goals never reach a child's form — not
 * because this file says "child", but because the server's answer for that subject doesn't
 * include BODY/NUTRITION. If the server ever changes what a tier may set, this file does not
 * need to change to follow it.
 *
 * <p>The one thing that stays local is presentation metadata the server has no reason to carry
 * — a label, and which periods make sense to offer for a metric (`METRIC_META` below). If this
 * file and the server ever disagree about what's *allowed* rather than how it's labelled, the
 * server wins and the person sees its refusal; nothing here is the enforcement point.
 */

interface MetricMeta {
  labelKey: MessageKey;
  /** Directions that make sense for this metric at all, most common first — narrowed further
   *  by the subject's own `goalDirections` in {@link offeredGoalMetrics}. */
  directions: GoalDirection[];
  periods: GoalPeriod[];
}

const METRIC_META: Record<string, MetricMeta> = {
  WATER_ML: { labelKey: 'goal.metric.WATER_ML', directions: ['AT_LEAST'], periods: ['DAILY'] },
  STEPS: { labelKey: 'goal.metric.STEPS', directions: ['AT_LEAST'], periods: ['DAILY', 'WEEKLY'] },
  // In the plan's offer set (§18 Journey 1.3) but previously absent here, so an active
  // ACTIVE_MINUTES goal — which the server accepts and the API can create — rendered on the
  // goals screen as the raw enum "ACTIVE_MINUTES".
  ACTIVE_MINUTES: { labelKey: 'goal.metric.ACTIVE_MINUTES', directions: ['AT_LEAST'], periods: ['DAILY', 'WEEKLY'] },
  SLEEP_MINUTES: { labelKey: 'goal.metric.SLEEP_MINUTES', directions: ['AT_LEAST', 'MAINTAIN'], periods: ['DAILY'] },
  PROTEIN_G: { labelKey: 'goal.metric.PROTEIN_G', directions: ['AT_LEAST'], periods: ['DAILY'] },
  FIBRE_G: { labelKey: 'goal.metric.FIBRE_G', directions: ['AT_LEAST'], periods: ['DAILY'] },
  SODIUM_MG: { labelKey: 'goal.metric.SODIUM_MG', directions: ['AT_MOST'], periods: ['DAILY'] },
  // No AT_MOST offered here even for an adult: an energy ceiling without a reviewed floor is a
  // restriction target (D-047). If the server ever offers AT_MOST for ENERGY_KCAL this table
  // would still filter it out; widening that is a deliberate future change, not a gap to fix now.
  ENERGY_KCAL: { labelKey: 'goal.metric.ENERGY_KCAL', directions: ['MAINTAIN', 'AT_LEAST'], periods: ['DAILY'] },
  WEIGHT_KG: { labelKey: 'goal.metric.WEIGHT_KG', directions: ['MAINTAIN', 'AT_MOST', 'AT_LEAST'], periods: ['WEEKLY'] },
};

export interface GoalMetricOption {
  code: string;
  labelKey: MessageKey;
  unit: string;
  directions: GoalDirection[];
  periods: GoalPeriod[];
}

/**
 * The metrics this subject may set a goal for right now: the server's defined metrics, filtered
 * to the categories and directions `capabilities` actually offers this subject, shaped with the
 * local label/period metadata. A metric the server defines but this file has no metadata for is
 * skipped rather than shown unlabelled.
 */
export function offeredGoalMetrics(
  definitions: readonly MetricDefinition[],
  capabilities: Capabilities,
): GoalMetricOption[] {
  const options: GoalMetricOption[] = [];
  for (const def of definitions) {
    const meta = METRIC_META[def.code];
    if (!meta) continue;
    if (!capabilities.goalCategories.includes(def.category)) continue;
    const directions = meta.directions.filter((d) => capabilities.goalDirections.includes(d));
    if (directions.length === 0) continue;
    options.push({ code: def.code, labelKey: meta.labelKey, unit: def.unit, directions, periods: meta.periods });
  }
  return options;
}

export function metricLabelKey(code: string): MessageKey | null {
  return METRIC_META[code]?.labelKey ?? null;
}

export const DIRECTION_KEYS: Record<GoalDirection, MessageKey> = {
  AT_LEAST: 'goal.direction.AT_LEAST',
  AT_MOST: 'goal.direction.AT_MOST',
  MAINTAIN: 'goal.direction.MAINTAIN',
};

export const PERIOD_KEYS: Record<GoalPeriod, MessageKey> = {
  DAILY: 'goal.period.DAILY',
  WEEKLY: 'goal.period.WEEKLY',
};

export const CADENCES: HabitCadence[] = ['DAILY', 'WEEKDAYS', 'WEEKLY'];

export const CADENCE_KEYS: Record<HabitCadence, MessageKey> = {
  DAILY: 'habit.cadence.DAILY',
  WEEKDAYS: 'habit.cadence.WEEKDAYS',
  WEEKLY: 'habit.cadence.WEEKLY',
};
