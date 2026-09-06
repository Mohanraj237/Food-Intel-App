import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { getCategories, searchProducts, type ProductSummary } from '@/lib/catalog';
import { Chip, ProductGrid, SectionHeader } from '@/design-system/Rails';
import { ErrorState } from '@/design-system/States';
import { toCardData } from '../home/HomeScreen';
import './SearchScreen.css';

/**
 * Client-side-only "best data quality first" ordering.
 *
 * The backend's `/search` does not accept a `sort` parameter (see
 * `src/lib/catalog.ts`), so this never re-queries or fabricates anything — it
 * re-orders the same page of real `dataQuality` values the server already
 * returned. Ranks mirror the badge treatment on the product screen
 * (`features/product/ProductScreen.tsx`): verified first, unresolved
 * conflicts last. An unrecognised or missing value sorts alongside
 * `UNVERIFIED` rather than to either extreme, so an unexpected enum value
 * degrades quietly instead of jumping to the front or back.
 */
const UNRECOGNISED_RANK = 2; // alongside UNVERIFIED — see qualityRank below

const QUALITY_RANK: Record<string, number> = {
  VERIFIED: 0,
  RECENT: 1,
  UNVERIFIED: UNRECOGNISED_RANK,
  STALE: 3,
  NEEDS_REVIEW: 4,
  CONFLICTING: 5,
};

function qualityRank(dataQuality: string | null): number {
  if (dataQuality === null) return UNRECOGNISED_RANK;
  return QUALITY_RANK[dataQuality] ?? UNRECOGNISED_RANK;
}

type SortMode = 'relevance' | 'quality';

/**
 * Search results.
 *
 * The query lives in the URL, not in component state, so a result page is
 * shareable, survives a reload, and works with the back button — all of which a
 * shopper expects and none of which come free from a local `useState`.
 *
 * The empty result is the screen that matters most. Query success will be low
 * against a young catalogue, so "we do not have this yet" is a designed
 * destination with a way forward, never a dead end (PANTRY R-06, AC-4.3).
 */
export function SearchScreen() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const sort: SortMode = params.get('sort') === 'quality' ? 'quality' : 'relevance';

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 30 * 60 * 1000,
  });

  const results = useQuery({
    queryKey: ['search', q, category],
    queryFn: () => searchProducts({ q, category, size: 30 }),
  });

  function toggleCategory(code: string) {
    const next = new URLSearchParams(params);
    if (category === code) {
      next.delete('category');
    } else {
      next.set('category', code);
    }
    setParams(next, { replace: true });
  }

  function setSort(mode: SortMode) {
    const next = new URLSearchParams(params);
    if (mode === 'relevance') {
      next.delete('sort');
    } else {
      next.set('sort', mode);
    }
    setParams(next, { replace: true });
  }

  const heading = q
    ? t('search.resultsFor', { query: q })
    : category
      ? (categories.data?.find((c) => c.code === category)?.name ?? t('nav.search'))
      : t('nav.search');

  const items = results.data?.items ?? [];
  const isEmpty = results.isSuccess && items.length === 0;

  // Re-orders the page already in hand; never re-fetches and never invents a
  // value for a product missing one. Only worth offering once there is more
  // than one item to reorder.
  const sortedItems = useMemo<ProductSummary[]>(() => {
    if (sort !== 'quality') return items;
    return [...items].sort((a, b) => qualityRank(a.dataQuality) - qualityRank(b.dataQuality));
  }, [items, sort]);

  const canSort = results.isSuccess && sortedItems.length > 1;

  return (
    <div className="searchpage">
      {categories.isSuccess && categories.data.length > 0 && (
        <div className="chip-row searchpage__chiprow" role="group" aria-label={t('home.browseCategories')}>
          {category && (
            <Chip active onClick={() => toggleCategory(category)}>
              {t('search.clearFilter')}
            </Chip>
          )}
          {categories.data.map((c) => (
            <Chip
              key={c.code}
              active={category === c.code}
              onClick={() => toggleCategory(c.code)}
            >
              {c.name}
            </Chip>
          ))}
        </div>
      )}

      <div className="searchpage__body">
        <div className="searchpage__toolbar">
          <SectionHeader
            title={heading}
            {...(results.isSuccess
              ? { subtitle: t('search.resultCount', { count: results.data.total }) }
              : {})}
          />

          {canSort && (
            <div
              className="searchpage__sort"
              role="group"
              aria-label={t('search.sortLabel' as MessageKey)}
            >
              <Chip active={sort === 'relevance'} onClick={() => setSort('relevance')}>
                {t('search.sortRelevance' as MessageKey)}
              </Chip>
              <Chip active={sort === 'quality'} onClick={() => setSort('quality')}>
                {t('search.sortQuality' as MessageKey)}
              </Chip>
            </div>
          )}
        </div>

        {results.isError && (
          <ErrorState error={results.error} onRetry={() => void results.refetch()} />
        )}

        {isEmpty ? (
          <NotFound query={q} />
        ) : (
          <ProductGrid loading={results.isPending} products={sortedItems.map(toCardData)} />
        )}
      </div>
    </div>
  );
}

/**
 * The "not found" screen.
 *
 * First-class, not an error state. It offers the two ways forward that actually
 * resolve the situation — photograph the label to get an answer now, and
 * contribute the product — so the failure becomes a contribution rather than an
 * exit.
 */
function NotFound({ query }: { query: string }) {
  return (
    <div className="notfound">
      <span className="notfound__glyph" aria-hidden="true">⌕</span>
      <h3 className="notfound__title">{t('search.noResults')}</h3>
      {query && <p className="notfound__query">“{query}”</p>}
      <p className="notfound__body">{t('search.noResultsAction')}</p>
      <div className="notfound__actions">
        <Link to="/scan?mode=label" className="btn btn--primary">
          {t('capture.heading')}
        </Link>
        <Link to="/scan" className="btn btn--secondary">
          {t('scan.heading')}
        </Link>
      </div>
    </div>
  );
}
