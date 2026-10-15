import { describe, expect, it } from 'vitest';
import { activeFilterCount, readFilters, readSort, removePhrase, withQuery, writeFilters } from './searchParams';

describe('search URL state', () => {
  it('reads filters, ignoring invalid values rather than guessing', () => {
    const params = new URLSearchParams(
      'q=biscuits&category=BISCUITS&maxTotalSugar=10&maxSodium=-5&minProtein=abc&excludeAllergens=peanut,MILK,CHEESE,milk&dietary=VEG&maxNova=9',
    );
    expect(readFilters(params)).toEqual({
      category: 'BISCUITS',
      maxTotalSugar: 10,
      excludeAllergens: ['PEANUT', 'MILK'],
      dietary: 'VEG',
    });
  });

  it('writes filters back and keeps q and sort untouched', () => {
    const params = new URLSearchParams('q=amul&sort=SODIUM_ASC&maxFibre=3&dietary=VEGAN');
    const next = writeFilters(params, { minFibre: 4, excludeAllergens: ['SOY'], maxNova: 3 });
    expect(next.get('q')).toBe('amul');
    expect(next.get('sort')).toBe('SODIUM_ASC');
    expect(next.get('minFibre')).toBe('4');
    expect(next.get('excludeAllergens')).toBe('SOY');
    expect(next.get('maxNova')).toBe('3');
    expect(next.get('dietary')).toBeNull();
  });

  it('round-trips', () => {
    const filters = { category: 'CHIPS', maxSodium: 300, minProtein: 8, excludeAllergens: ['PEANUT', 'TREE_NUT'], dietary: 'VEG' as const, maxNova: 2 };
    expect(readFilters(writeFilters(new URLSearchParams(), filters))).toEqual(filters);
  });

  it('maps the old client-side sort values so old links still work', () => {
    expect(readSort(new URLSearchParams('sort=sugar'))).toBe('ADDED_SUGAR_ASC');
    expect(readSort(new URLSearchParams('sort=PROTEIN_DESC'))).toBe('PROTEIN_DESC');
    expect(readSort(new URLSearchParams('sort=price'))).toBe('RELEVANCE');
  });

  it('counts active filters, not the category', () => {
    expect(activeFilterCount({ category: 'CHIPS', maxSodium: 1, excludeAllergens: ['MILK', 'SOY'], dietary: 'VEG' })).toBe(4);
    expect(activeFilterCount({})).toBe(0);
  });

  it('removes exactly the phrase a chip stood for', () => {
    expect(removePhrase('veg biscuits under 10g sugar', 'under 10g sugar')).toBe('veg biscuits');
    expect(removePhrase('Veg Biscuits', 'veg')).toBe('Biscuits');
    expect(removePhrase('high  protein snacks', 'high protein')).toBe('snacks');
    expect(removePhrase('biscuits', '')).toBe('biscuits');
  });

  it('sets q without dropping filters', () => {
    const next = withQuery(new URLSearchParams('maxTotalSugar=5&sort=SUGAR_ASC&q=old'), 'new words');
    expect(next.get('q')).toBe('new words');
    expect(next.get('maxTotalSugar')).toBe('5');
    expect(withQuery(next, '  ').has('q')).toBe(false);
  });
});
