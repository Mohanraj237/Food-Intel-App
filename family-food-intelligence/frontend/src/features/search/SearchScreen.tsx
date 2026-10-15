import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import {
  getCategories,
  searchProducts,
  NUMERIC_FILTER_KEYS,
  type InterpretedTerm,
  type NumericFilterKey,
  type ProductSummary,
  type SearchFilters,
  type SearchSort,
} from '@/lib/catalog';
import { formatAmount } from '@/lib/nutrition';
import {
  FILTER_UNITS,
  SEARCH_SORTS,
  activeFilterCount,
  readFilters,
  readSort,
  removePhrase,
  withQuery,
  writeFilters,
} from '@/lib/searchParams';
import { useBatchFit, type CardFit } from '@/lib/useBatchFit';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { Button } from '@/design-system/Button';
import { Chip, ProductGrid, ProductRail, SectionHeader } from '@/design-system/Rails';
import type { ProductCardData } from '@/design-system/ProductCard';
import { ErrorState } from '@/design-system/States';
import { Icon } from '@/design-system/Icon';
import { toCardData } from '../home/HomeScreen';
import { FilterSheet } from './FilterSheet';
import './SearchScreen.css';

const PAGE_SIZE = 30;

const FILTER_NUTRIENT: Record<NumericFilterKey, string> = {
  maxTotalSugar: 'TOTAL_SUGAR_G',
  maxAddedSugar: 'ADDED_SUGAR_G',
  maxSodium: 'SODIUM_MG',
  maxSatFat: 'SAT_FAT_G',
  maxEnergy: 'ENERGY_KCAL',
  minProtein: 'PROTEIN_G',
  minFibre: 'FIBRE_G',
};

function nutrientName(code: string | null): string {
  return code ? t(`search.nutrient.${code}` as MessageKey) : '';
}

/** A card that knows the viewer's own verdict and the product's small rating, when there is one. */
function toSearchCard(p: ProductSummary, fit: CardFit | undefined): ProductCardData {
  return {
    ...toCardData(p),
    verdict: fit?.verdict ?? null,
    allergenFlagged: fit?.allergenFlagged ?? false,
    ratingStars: p.assessment?.status === 'SCORED' ? p.assessment.stars : null,
  };
}

/**
 * Search and browse (D-061). Query, category, filters and sort live in the URL, so a result page
 * is shareable, survives a reload and works with the back button. Filtering and ordering happen
 * on the server; a product that does not declare a filtered fact is counted, never shown as if
 * it were checked. Phrases the server read from the words ("under 10g sugar", "veg") appear as
 * chips — dismissing one removes that phrase from the words.
 */
export function SearchScreen() {
  const [params, setParams] = useSearchParams();
  const [sheetOpen, setSheetOpen] = useState(false);
  const q = params.get('q') ?? '';
  const filters = useMemo(() => readFilters(params), [params]);
  const urlSort = readSort(params);

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 30 * 60 * 1000,
  });

  const results = useInfiniteQuery({
    queryKey: ['search', q, JSON.stringify(filters), urlSort],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => searchProducts({ q, ...filters, sort: urlSort, page: pageParam, size: PAGE_SIZE }),
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.total ? lastPage.page + 1 : undefined;
    },
  });

  const firstPage = results.data?.pages[0];
  const items = useMemo<ProductSummary[]>(() => results.data?.pages.flatMap((p) => p.items) ?? [], [results.data]);
  const fit = useBatchFit(useMemo(() => items.map((i) => i.id), [items]));
  const total = firstPage?.total ?? 0;
  const isEmpty = results.isSuccess && items.length === 0;
  const shownSort: SearchSort = urlSort !== 'RELEVANCE' ? urlSort : (firstPage?.sort ?? 'RELEVANCE');
  const anyRated = items.some((i) => i.assessment?.status === 'SCORED');

  const categoryName = (code: string | null | undefined) => categories.data?.find((c) => c.code === code)?.name ?? code ?? '';
  const heading = q ? t('search.resultsFor', { query: q }) : filters.category ? categoryName(filters.category) : t('search.browseHeading');
  useDocumentTitle(q ? t('search.resultsFor', { query: q }) : filters.category ? categoryName(filters.category) : t('nav.search'));

  function update(next: URLSearchParams) {
    setParams(next, { replace: true });
  }

  function toggleCategory(code: string) {
    const next: SearchFilters = { ...filters };
    if (filters.category === code) delete next.category;
    else next.category = code;
    update(writeFilters(params, next));
  }

  function setSort(sort: SearchSort) {
    const next = new URLSearchParams(params);
    if (sort === 'RELEVANCE') next.delete('sort');
    else next.set('sort', sort);
    update(next);
  }

  function removeFilter(key: keyof SearchFilters, allergen?: string) {
    const next: SearchFilters = { ...filters };
    if (key === 'excludeAllergens' && allergen) {
      next.excludeAllergens = (filters.excludeAllergens ?? []).filter((a) => a !== allergen);
    } else {
      delete next[key];
    }
    update(writeFilters(params, next));
  }

  function removeInterpreted(term: InterpretedTerm) {
    update(withQuery(params, removePhrase(q, term.source)));
  }

  const filterChips = buildFilterChips(filters, removeFilter);
  const interpreted = firstPage?.interpreted ?? [];
  const filterCount = activeFilterCount(filters);
  const excluded = firstPage?.excludedForMissingData ?? 0;

  return (
    <div className="searchpage">
      <h1 className="sr-only">{t('search.heading')}</h1>

      {/* ---- category filter strip ---- */}
      {categories.isSuccess && categories.data.length > 0 && (
        <div className="searchpage__filters">
          <div className="chip-row searchpage__chiprow" role="group" aria-label={t('home.browseCategories')}>
            <Chip active={!filters.category} onClick={() => filters.category && toggleCategory(filters.category)}>
              {t('search.allCategories')}
            </Chip>
            {categories.data.map((c) => (
              <Chip key={c.code} active={filters.category === c.code} onClick={() => toggleCategory(c.code)}>
                {c.name}
              </Chip>
            ))}
          </div>
        </div>
      )}

      <div className="searchpage__body">
        <div className="searchpage__toolbar">
          <div className="searchpage__heading">
            <h2 className="searchpage__title">{heading}</h2>
            {results.isSuccess && (
              <p className="searchpage__count">
                {items.length < total
                  ? t('search.showingCount', { shown: items.length, total })
                  : t('search.resultCount', { count: total })}
              </p>
            )}
          </div>

          <div className="searchpage__controls">
            <Button variant="secondary" size="sm" onClick={() => setSheetOpen(true)} aria-haspopup="dialog">
              <Icon name="settings" size={16} />
              {filterCount > 0 ? t('search.filtersCount', { count: filterCount }) : t('search.filters')}
            </Button>
            <label className="searchpage__sort">
              <span className="searchpage__sort-label">{t('search.sortLabel')}</span>
              <select
                className="searchpage__sort-select"
                value={shownSort}
                onChange={(e) => setSort(e.target.value as SearchSort)}
              >
                {SEARCH_SORTS.filter((s) => s !== 'SCORE_DESC' || anyRated || shownSort === 'SCORE_DESC').map((s) => (
                  <option key={s} value={s}>
                    {t(`search.sort.${s}` as MessageKey)}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        {(filterChips.length > 0 || interpreted.length > 0) && (
          <div className="searchpage__active" aria-label={t('search.activeFilters')}>
            {filterChips.map((chip) => (
              <RemovableChip key={chip.key} label={chip.label} onRemove={chip.onRemove} />
            ))}
            {interpreted.length > 0 && <span className="searchpage__understood">{t('search.understood')}</span>}
            {interpreted.map((term) => (
              <RemovableChip
                key={`${term.type}-${term.code ?? ''}-${term.source}`}
                label={interpretedLabel(term, categoryName)}
                onRemove={() => removeInterpreted(term)}
              />
            ))}
          </div>
        )}

        {results.isSuccess && excluded > 0 && (
          <p className="searchpage__excluded" role="note">
            <Icon name="info" size={16} />
            {t('search.excludedMissing', { count: excluded })}
          </p>
        )}

        {results.isError && <ErrorState error={results.error} onRetry={() => void results.refetch()} />}

        {isEmpty ? (
          <NotFound query={q} didYouMean={firstPage?.didYouMean} filtered={filterCount > 0 || interpreted.length > 0} />
        ) : (
          <>
            <ProductGrid loading={results.isPending} products={items.map((p) => toSearchCard(p, fit.get(p.id)))} />

            {results.isSuccess && items.length > 0 && (
              <div className="searchpage__more">
                {results.hasNextPage ? (
                  <Button
                    variant="secondary"
                    onClick={() => void results.fetchNextPage()}
                    disabled={results.isFetchingNextPage}
                  >
                    {results.isFetchingNextPage ? t('search.loadingMore') : t('search.loadMore')}
                  </Button>
                ) : (
                  <p className="searchpage__more-end">{t('search.allLoaded')}</p>
                )}
                <p className="sr-only" role="status">
                  {t('search.showingCount', { shown: items.length, total })}
                </p>
              </div>
            )}
          </>
        )}

        {firstPage?.related && firstPage.related.betterInCategory.length > 0 && (
          <section className="searchpage__related">
            <SectionHeader
              title={t('search.related.betterIn', { category: categoryName(firstPage.related.categoryCode) })}
              as="h3"
            />
            <ProductRail products={firstPage.related.betterInCategory.map((p) => toSearchCard(p, fit.get(p.id)))} />
          </section>
        )}
        {firstPage?.related && firstPage.related.sameBrand.length > 0 && (
          <section className="searchpage__related">
            <SectionHeader title={t('search.related.moreFrom', { brand: firstPage.related.brandName ?? '' })} as="h3" />
            <ProductRail products={firstPage.related.sameBrand.map((p) => toSearchCard(p, fit.get(p.id)))} />
          </section>
        )}
      </div>

      <FilterSheet open={sheetOpen} onClose={() => setSheetOpen(false)} value={filters} onApply={(f) => update(writeFilters(params, f))} />
    </div>
  );
}

function RemovableChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <button type="button" className="searchpage__chip" onClick={onRemove} aria-label={t('search.removeFilter', { filter: label })}>
      <span>{label}</span>
      <Icon name="close" size={14} />
    </button>
  );
}

function buildFilterChips(
  filters: SearchFilters,
  remove: (key: keyof SearchFilters, allergen?: string) => void,
): Array<{ key: string; label: string; onRemove: () => void }> {
  const chips: Array<{ key: string; label: string; onRemove: () => void }> = [];
  for (const key of NUMERIC_FILTER_KEYS) {
    const value = filters[key];
    if (value == null) continue;
    chips.push({
      key,
      label: t(key.startsWith('min') ? 'search.chip.min' : 'search.chip.max', {
        nutrient: nutrientName(FILTER_NUTRIENT[key]),
        value: formatAmount(value),
        unit: FILTER_UNITS[key],
      }),
      onRemove: () => remove(key),
    });
  }
  for (const code of filters.excludeAllergens ?? []) {
    chips.push({
      key: `allergen-${code}`,
      label: t('search.chip.hide', { allergen: t(`allergenCode.${code}` as MessageKey) }),
      onRemove: () => remove('excludeAllergens', code),
    });
  }
  if (filters.dietary) {
    chips.push({ key: 'dietary', label: t(`search.chip.diet.${filters.dietary}` as MessageKey), onRemove: () => remove('dietary') });
  }
  if (filters.maxNova != null) {
    chips.push({ key: 'maxNova', label: t('search.chip.maxNova', { value: filters.maxNova }), onRemove: () => remove('maxNova') });
  }
  return chips;
}

function interpretedLabel(term: InterpretedTerm, categoryName: (code: string | null) => string): string {
  switch (term.type) {
    case 'CATEGORY':
      return categoryName(term.code);
    case 'MAX_NUTRIENT':
    case 'MIN_NUTRIENT':
      return t(term.type === 'MIN_NUTRIENT' ? 'search.chip.min' : 'search.chip.max', {
        nutrient: nutrientName(term.code),
        value: term.value == null ? '' : formatAmount(term.value),
        unit: term.unit ?? '',
      });
    case 'SORT':
      return t(`search.sort.${term.code}` as MessageKey);
    case 'DIET':
      return t(`search.chip.diet.${term.code}` as MessageKey);
    case 'EXCLUDE_ALLERGEN':
      return t('search.chip.hide', { allergen: t(`allergenCode.${term.code}` as MessageKey) });
    case 'MAX_NOVA':
      return t('search.chip.maxNova', { value: term.value ?? '' });
    default:
      return term.source;
  }
}

/** "We don't have this yet" is a destination with a way forward, never a dead end. */
function NotFound({ query, didYouMean, filtered }: { query: string; didYouMean: string | undefined; filtered: boolean }) {
  return (
    <div className="notfound">
      <span className="notfound__glyph">
        <Icon name="search" size={28} />
      </span>
      <h3 className="notfound__title">{filtered ? t('search.noResultsFiltered') : t('search.noResults')}</h3>
      {query && <p className="notfound__query">“{query}”</p>}
      {didYouMean && (
        <p className="notfound__body">
          <Link to={`/search?q=${encodeURIComponent(didYouMean)}`}>{t('search.didYouMean', { suggestion: didYouMean })}</Link>
        </p>
      )}
      <p className="notfound__body">{t('search.noResultsAction')}</p>
      <div className="notfound__actions">
        <Link to="/scan" className="btn btn--primary">
          <Icon name="scan" size={18} />
          {t('scan.heading')}
        </Link>
        <Link to="/scan?mode=label" className="btn btn--secondary">
          <Icon name="camera" size={18} />
          {t('capture.heading')}
        </Link>
      </div>
    </div>
  );
}
