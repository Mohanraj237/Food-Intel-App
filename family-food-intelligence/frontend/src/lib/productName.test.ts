import { describe, expect, it } from 'vitest';
import {
  displayProductName,
  isBarcodeLikeName,
  normaliseBrand,
  stripPriceNoise,
} from './productName';

/**
 * Every string below is a real row from the catalogue this runs against. The
 * risk in this module is over-reach — suppressing a legitimate product name —
 * so the "leaves alone" cases matter more than the ones it catches.
 */
describe('barcode-like names', () => {
  it('catches names that are only a barcode', () => {
    expect(isBarcodeLikeName('8901063019201')).toBe(true);
    expect(isBarcodeLikeName('18111286')).toBe(true);
    expect(isBarcodeLikeName('3948764092305')).toBe(true);
    expect(isBarcodeLikeName(' 8901491003162 ')).toBe(true);
  });

  it('leaves real product names alone, including numeric ones', () => {
    expect(isBarcodeLikeName('20-20')).toBe(false); // a Parle biscuit
    expect(isBarcodeLikeName('50 50 Gol Maal')).toBe(false); // a Britannia biscuit
    expect(isBarcodeLikeName('5050 Maska Chaska')).toBe(false);
    expect(isBarcodeLikeName('2-minute Noodles')).toBe(false);
    expect(isBarcodeLikeName('Maggi 8901058000252')).toBe(false); // has a real name too
    expect(isBarcodeLikeName('70%coCÓA')).toBe(false); // garbled, but not a barcode
  });
});

describe('price noise', () => {
  it('strips trailing retail-price fragments', () => {
    expect(stripPriceNoise('Maggi 2 Mins Noodles 30 MRP')).toBe('Maggi 2 Mins Noodles');
    expect(stripPriceNoise('Britannia Cake MRP 45')).toBe('Britannia Cake');
    expect(stripPriceNoise('Lays Classic ₹20')).toBe('Lays Classic');
  });

  it('does not eat a pack size or a real trailing number', () => {
    expect(stripPriceNoise('20 20 butter cookies 200g')).toBe('20 20 butter cookies 200g');
    expect(stripPriceNoise('7up Super Duper 750ml')).toBe('7up Super Duper 750ml');
    expect(stripPriceNoise('Maggi 70g')).toBe('Maggi 70g');
  });

  it('never returns an empty string', () => {
    expect(stripPriceNoise('30 MRP')).not.toBe('');
  });
});

describe('brand casing', () => {
  it('title-cases an all-lowercase brand', () => {
    expect(normaliseBrand('britannia')).toBe('Britannia');
    expect(normaliseBrand('yoga bar')).toBe('Yoga Bar');
  });

  it('leaves a brand that already carries its own casing', () => {
    expect(normaliseBrand('PepsiCo')).toBe('PepsiCo');
    expect(normaliseBrand('PARLE')).toBe('PARLE');
    expect(normaliseBrand('Cadbury Bournville')).toBe('Cadbury Bournville');
  });
});

describe('display name', () => {
  it('uses the stored name when it is usable', () => {
    expect(
      displayProductName({ name: '2-minute Noodles', brandName: 'Maggi' }),
    ).toEqual({ text: '2-minute Noodles', isPlaceholder: false });
  });

  it('falls back to the category when the name is only a barcode', () => {
    expect(
      displayProductName({
        name: '8901063019201',
        brandName: 'britannia',
        categoryName: 'Biscuits and cookies',
      }),
    ).toEqual({ text: 'Biscuits and cookies', isPlaceholder: true });
  });

  it('falls back to the brand when there is no category either', () => {
    expect(
      displayProductName({ name: '18111286', brandName: 'britannia' }),
    ).toEqual({ text: 'Britannia', isPlaceholder: true });
  });

  it('never invents a name it cannot support', () => {
    const result = displayProductName({ name: '18111286', brandName: '' });
    expect(result.isPlaceholder).toBe(true);
    expect(result.text).toBe('18111286');
  });
});
