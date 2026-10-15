import {
  NUMERIC_FILTER_KEYS,
  type NumericFilterKey,
  type SearchFilters,
  type SearchSort,
} from './catalog';

/**
 * Search state lives in the URL (a result page is shareable, survives a reload, works with
 * back). These helpers are the one place that reads and writes it, so the screen, the shell's
 * search pill and the filter sheet never disagree about what a parameter means.
 */

export const SEARCH_SORTS: readonly SearchSort[] = [
  'RELEVANCE',
  'SCORE_DESC',
  'SUGAR_ASC',
  'ADDED_SUGAR_ASC',
  'SODIUM_ASC',
  'SAT_FAT_ASC',
  'PROTEIN_DESC',
  'FIBRE_DESC',
  'QUALITY',
];

/** Sort values the previous client-side sorter wrote into URLs; kept so old links still work. */
const LEGACY_SORTS: Record<string, SearchSort> = {
  relevance: 'RELEVANCE',
  sugar: 'ADDED_SUGAR_ASC',
  sodium: 'SODIUM_ASC',
  quality: 'QUALITY',
};

export const ALLERGEN_CODES = [
  'MILK',
  'EGG',
  'FISH',
  'CRUSTACEAN',
  'TREE_NUT',
  'PEANUT',
  'WHEAT',
  'GLUTEN',
  'SOY',
  'SESAME',
  'SULPHITE',
  'MUSTARD',
] as const;

/** Unit of each numeric filter, in the catalogue's canonical units. */
export const FILTER_UNITS: Record<NumericFilterKey, 'g' | 'mg' | 'kcal'> = {
  maxTotalSugar: 'g',
  maxAddedSugar: 'g',
  maxSodium: 'mg',
  maxSatFat: 'g',
  maxEnergy: 'kcal',
  minProtein: 'g',
  minFibre: 'g',
};

export function readSort(params: URLSearchParams): SearchSort {
  const raw = params.get('sort') ?? '';
  if ((SEARCH_SORTS as readonly string[]).includes(raw)) return raw as SearchSort;
  return LEGACY_SORTS[raw] ?? 'RELEVANCE';
}

export function readFilters(params: URLSearchParams): SearchFilters {
  const filters: SearchFilters = {};
  const category = params.get('category');
  if (category) filters.category = category;
  for (const key of NUMERIC_FILTER_KEYS) {
    const raw = params.get(key);
    if (raw === null || raw.trim() === '') continue;
    const value = Number(raw);
    if (Number.isFinite(value) && value >= 0) filters[key] = value;
  }
  const allergens = (params.get('excludeAllergens') ?? '')
    .split(',')
    .map((s) => s.trim().toUpperCase())
    .filter((s): s is (typeof ALLERGEN_CODES)[number] => (ALLERGEN_CODES as readonly string[]).includes(s));
  if (allergens.length > 0) filters.excludeAllergens = [...new Set(allergens)];
  const dietary = params.get('dietary');
  if (dietary === 'VEG' || dietary === 'VEGAN') filters.dietary = dietary;
  const nova = Number(params.get('maxNova'));
  if (Number.isInteger(nova) && nova >= 1 && nova <= 4) filters.maxNova = nova;
  return filters;
}

/** Replaces every filter parameter with `filters`, keeping `q` and `sort`. */
export function writeFilters(params: URLSearchParams, filters: SearchFilters): URLSearchParams {
  const next = new URLSearchParams(params);
  ['category', ...NUMERIC_FILTER_KEYS, 'excludeAllergens', 'dietary', 'maxNova'].forEach((k) => next.delete(k));
  if (filters.category) next.set('category', filters.category);
  for (const key of NUMERIC_FILTER_KEYS) {
    const value = filters[key];
    if (value != null && Number.isFinite(value)) next.set(key, String(value));
  }
  if (filters.excludeAllergens && filters.excludeAllergens.length > 0) {
    next.set('excludeAllergens', filters.excludeAllergens.join(','));
  }
  if (filters.dietary) next.set('dietary', filters.dietary);
  if (filters.maxNova != null) next.set('maxNova', String(filters.maxNova));
  return next;
}

/** How many filters (besides category) are active — the number on the Filters button. */
export function activeFilterCount(filters: SearchFilters): number {
  let count = NUMERIC_FILTER_KEYS.filter((k) => filters[k] != null).length;
  count += filters.excludeAllergens?.length ?? 0;
  if (filters.dietary) count += 1;
  if (filters.maxNova != null) count += 1;
  return count;
}

/**
 * Removes a phrase the server read from the query (an interpreted term's `source`) from the
 * typed words, so dismissing a chip undoes exactly what it stood for. Case-insensitive; tidies
 * the spaces it leaves behind.
 */
export function removePhrase(query: string, phrase: string): string {
  if (!phrase.trim()) return query;
  const escaped = phrase.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+');
  return query.replace(new RegExp(escaped, 'i'), ' ').replace(/\s+/g, ' ').trim();
}

/** Sets `q`, keeping every other parameter (filters, sort) as it is. */
export function withQuery(params: URLSearchParams, q: string): URLSearchParams {
  const next = new URLSearchParams(params);
  if (q.trim()) next.set('q', q.trim());
  else next.delete('q');
  return next;
}
