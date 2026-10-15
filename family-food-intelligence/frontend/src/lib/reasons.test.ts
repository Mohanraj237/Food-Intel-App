import { describe, expect, it } from 'vitest';
import { describeReason } from './reasons';
import type { NutrientRow } from './catalog';
import type { Reason } from './scoring';

/**
 * The engine-v2 reason codes (D-059) render as plain sentences built only from the
 * values the backend sent — never a number typed into copy, never a raw code when a
 * label exists.
 */
const nutrients: NutrientRow[] = [
  {
    nutrientCode: 'FIBRE_G',
    displayName: 'Dietary fibre',
    amountPer100: 2,
    amountPerServing: null,
    unit: 'g',
    basisPer100: 'PER_100G',
    isEstimated: false,
    confidence: null,
    source: null,
  },
];

function reason(partial: Partial<Reason> & { code: string }): Reason {
  return { nutrientCode: null, basis: null, value: null, threshold: null, standardRef: null, ...partial };
}

describe('describeReason — engine v2 codes', () => {
  it('names a derived rating fact and its reference point', () => {
    const lines = describeReason(
      reason({ code: 'DERIVED_FACT_THRESHOLD', nutrientCode: 'INR_STARS', basis: 'PER_100G', value: 1.5, threshold: 2.5, standardRef: 'FSSAI_INR' }),
      nutrients,
    );
    expect(lines).toHaveLength(2);
    expect(lines[0]).toContain('Rating');
    expect(lines[0]).toContain('1.5');
    expect(lines[1]).toContain('2.5');
    expect(lines[1]).toContain('FSSAI_INR');
  });

  it('labels the processing-level fact rather than showing its code', () => {
    const [line] = describeReason(reason({ code: 'DERIVED_FACT_THRESHOLD', nutrientCode: 'NOVA_GROUP', value: 4, threshold: 4 }), nutrients);
    expect(line).toContain('NOVA group');
    expect(line).not.toContain('NOVA_GROUP');
  });

  it('says an undeclared factor counted as zero, using the nutrient name', () => {
    const [line] = describeReason(reason({ code: 'ASSUMED_ZERO', nutrientCode: 'FIBRE_G' }), nutrients);
    expect(line).toContain('Dietary fibre');
    expect(line).toContain('not declared');
  });

  it('explains a food type the method does not cover', () => {
    expect(describeReason(reason({ code: 'SCORE_NOT_APPLICABLE' }), nutrients)[0]).toContain('does not cover');
  });

  it('renders a per-100 ml basis for liquids', () => {
    const [line] = describeReason(
      reason({ code: 'NUTRIENT_EXCEEDS_THRESHOLD', nutrientCode: 'FIBRE_G', basis: 'PER_100ML', value: 3 }),
      nutrients,
    );
    expect(line).toContain('per 100 ml');
  });

  it('keeps the existing codes unchanged', () => {
    expect(describeReason(reason({ code: 'NO_ACTIVE_RULES' }), nutrients)[0]).toContain('No rule applied');
    expect(describeReason(reason({ code: 'MISSING_NUTRIENT_DATA', nutrientCode: 'FIBRE_G' }), nutrients)[0]).toContain('Dietary fibre');
  });
});
