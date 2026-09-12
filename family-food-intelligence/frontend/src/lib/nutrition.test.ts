import { describe, expect, it } from 'vitest';
import { formatAmount, formatMeasure, isPlausible, isPlausibleAmount } from './nutrition';

/**
 * The bar these bounds have to clear is narrow and specific: reject the
 * arithmetically impossible, keep everything a real label can say. A rule that
 * also hides an unusual-but-true figure would be a worse bug than the one it
 * fixes, because the value it suppressed was correct.
 */
describe('nutrient plausibility', () => {
  it('rejects the catalogue value that prompted this guard', () => {
    // A real row in the catalogue: 1,000,000 mg of sodium per 100 g is one
    // kilogram of sodium inside 100 g of food.
    expect(isPlausible(1_000_000, 'mg')).toBe(false);
    // Its derived salt figure, 2,500 g per 100 g, is impossible the same way.
    expect(isPlausible(2500, 'g')).toBe(false);
  });

  it('keeps values a real label can legitimately declare', () => {
    expect(isPlausible(671, 'mg')).toBe(true); // Maggi noodles, sodium
    expect(isPlausible(8.2, 'g')).toBe(true); // saturated fat
    expect(isPlausible(45.1, 'g')).toBe(true); // sugar in dark chocolate
    expect(isPlausible(0, 'g')).toBe(true); // a declared zero is a fact
    expect(isPlausible(100, 'g')).toBe(true); // pure sugar: the exact ceiling
  });

  it('keeps table salt, which is mostly sodium and looks extreme', () => {
    // ~39 g of sodium per 100 g. The guard must not reject a real product just
    // because its number is unusually large.
    expect(isPlausible(39_000, 'mg')).toBe(true);
  });

  it('applies an energy ceiling rather than a mass one', () => {
    expect(isPlausible(543, 'kcal')).toBe(true);
    expect(isPlausible(900, 'kcal')).toBe(true); // pure fat
    expect(isPlausible(5000, 'kcal')).toBe(false);
    expect(isPlausible(3766, 'kJ')).toBe(true);
  });

  it('rejects negatives and non-finite values', () => {
    expect(isPlausible(-1, 'g')).toBe(false);
    expect(isPlausible(Number.NaN, 'g')).toBe(false);
    expect(isPlausible(Number.POSITIVE_INFINITY, 'mg')).toBe(false);
  });

  it('treats a missing value as not plausible, so callers need only one branch', () => {
    expect(isPlausible(null, 'g')).toBe(false);
    expect(isPlausible(undefined, 'g')).toBe(false);
  });

  it('passes through units it cannot check rather than hiding the value', () => {
    // Not knowing how to verify something is not evidence that it is wrong.
    expect(isPlausibleAmount({ value: 12, unit: 'IU' })).toBe(true);
  });

  it('handles unit casing and micrograms', () => {
    expect(isPlausible(500, 'MG')).toBe(true);
    expect(isPlausible(200, 'µg')).toBe(true);
    expect(isPlausible(1e9, 'µg')).toBe(false); // 1000 g
  });
});

describe('measure formatting', () => {
  it('separates the value from its unit', () => {
    // "384kcal" and "70 g" appeared on the same screen before this.
    expect(formatMeasure(384, 'kcal')).toBe('384 kcal');
    expect(formatMeasure(8.2, 'g')).toBe('8.2 g');
  });

  it('rounds to whole numbers above 100 and one decimal below', () => {
    expect(formatAmount(671.4)).toBe('671');
    expect(formatAmount(8.25)).toBe('8.3');
    expect(formatAmount(12)).toBe('12');
  });

  it('groups large numbers', () => {
    expect(formatAmount(40_000)).toMatch(/[,. ]/);
  });
});
