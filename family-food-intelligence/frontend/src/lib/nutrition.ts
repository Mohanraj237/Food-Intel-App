/**
 * Nutrient plausibility and number formatting.
 *
 * Two jobs that belong together, because both answer "how do we put a nutrient
 * value in front of a person".
 *
 * **Plausibility.** The catalogue draws on open data and user contributions, and
 * some of it is wrong in ways arithmetic can catch: a product recorded with
 * 1,000,000 mg of sodium per 100 g is claiming 1 kg of sodium inside 100 g of
 * food. Publishing that as a fact is worse than publishing nothing — a user
 * making a health decision cannot tell a typo from a reading, and one absurd
 * figure discredits every honest one beside it. So a value that fails a
 * mass-balance or energy-ceiling check is withheld and labelled as needing a
 * check, exactly as a missing value is withheld, rather than rendered.
 *
 * These are deliberately *loose* bounds. The goal is to catch the impossible,
 * not to second-guess an unusual-but-real product: salt is ~39 g sodium per
 * 100 g, so a sodium figure of 40,000 mg/100 g is physically possible and stays.
 * Anything rejected here is arithmetically impossible, not merely surprising.
 *
 * **Formatting.** One place decides that a value and its unit are separated by a
 * space and that large numbers are grouped, so `8.2 g` and `1,028 mg` read the
 * same wherever they appear — card, table, comparison row.
 */

import { getLocale } from './i18n';

/** A nutrient amount as the API reports it. */
export interface NutrientAmount {
  value: number;
  unit: string;
  /** `true` when the figure is expressed per 100 g/ml rather than per serving. */
  per100?: boolean;
}

/**
 * Grams of a single nutrient that can physically sit inside 100 g of food.
 * 100 is the hard ceiling; nothing can be more than all of the product.
 */
const MAX_G_PER_100 = 100;

/** kcal per 100 g of pure fat (9 kcal/g) — no food exceeds this. */
const MAX_KCAL_PER_100 = 900;

/**
 * kJ equivalent of the above, with a little headroom.
 *
 * 900 kcal converts to 3765.6 kJ, and a label declaring both will have rounded
 * each independently — so a genuine 900 kcal product can legitimately print
 * 3766 kJ. Taking the exact conversion as a hard ceiling rejected a real value
 * by four tenths of a unit. The point of these bounds is to catch figures that
 * are impossible by orders of magnitude, never to arbitrate rounding.
 */
const MAX_KJ_PER_100 = Math.ceil(MAX_KCAL_PER_100 * 4.184) + 100;

/** Multiplier converting a unit into grams. */
const TO_GRAMS: Record<string, number> = {
  g: 1,
  mg: 1e-3,
  µg: 1e-6,
  mcg: 1e-6,
  ug: 1e-6,
};

/**
 * Is this amount physically possible?
 *
 * A serving-basis figure is checked against the same ceiling as a per-100 g one:
 * a serving is never more than the pack, and a nutrient is never more than the
 * serving, so a serving figure above 100 g of a single nutrient is only
 * plausible for packs above 100 g — a distinction too fine to be worth a false
 * rejection. The common failure this catches is a unit or decimal error, which
 * overshoots by orders of magnitude, not by a few grams.
 */
export function isPlausibleAmount(amount: NutrientAmount): boolean {
  const { value, unit } = amount;

  if (!Number.isFinite(value) || value < 0) return false;

  const normalised = unit.trim().toLowerCase();

  if (normalised === 'kcal') return value <= MAX_KCAL_PER_100;
  if (normalised === 'kj') return value <= MAX_KJ_PER_100;

  const gramsPerUnit = TO_GRAMS[normalised] ?? TO_GRAMS[unit.trim()];
  if (gramsPerUnit === undefined) {
    // An unrecognised unit is not evidence of an error — pass it through rather
    // than hiding a value we simply do not know how to check.
    return true;
  }

  return value * gramsPerUnit <= MAX_G_PER_100;
}

/** Convenience for the common `(value, unit)` call shape. */
export function isPlausible(value: number | null | undefined, unit: string): boolean {
  if (value == null) return false;
  return isPlausibleAmount({ value, unit });
}

/**
 * Formats a bare number: grouped above four digits, at most one decimal place.
 *
 * Grouping uses the active locale, so an Indian user sees the digit grouping
 * they read everywhere else.
 */
export function formatAmount(value: number): string {
  const rounded = value >= 100 ? Math.round(value) : Number(value.toFixed(1));

  if (Math.abs(rounded) >= 10_000) {
    return rounded.toLocaleString(getLocale());
  }
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

/**
 * The canonical "value + unit" rendering.
 *
 * A plain space, not a non-breaking one: callers that must not wrap say so in
 * CSS, and an invisible character in source is a maintenance trap.
 */
export function formatMeasure(value: number, unit: string): string {
  return `${formatAmount(value)} ${unit}`;
}
