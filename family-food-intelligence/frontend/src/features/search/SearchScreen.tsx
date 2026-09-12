import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { getCategories, searchProducts, type ProductSummary } from '@/lib/catalog';
import { isPlausible } from '@/lib/nutrition';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { Button } from '@/design-system/Button';
import { Chip, ProductGrid, SectionHeader } from '@/design-system/Rails';
import { ErrorState } from '@/design-system/States';
import { toCardData } from '../home/HomeScreen';
import './SearchScreen.css';

/** Results requested per page, and the size of each "Load more" step. */
const PAGE_SIZE = 30;

/**
 * Client-side-only "best data quality first" ordering.
 *
 * The backend's `/search` does not accept a `sort` parameter (see
 * `src/lib/catalog.ts`), so this never re-queries or fabricates anything — it
 * re-orders the same pages of real `dataQuality` values the server already
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

/**
 * Nutrient ordering.
 *
 * "Which of these has the least sugar?" is the question this product exists to
 * answer, and until now the only orderings offered were relevance and data
 * quality — neither of which a shopper is actually asking. Like the quality
 * sort this re-orders the pages already fetched rather than inventing a
 * server-side ranking, so it is honest about only covering what is loaded.
 *
 * A product with no usable figure for the chosen nutrient sorts last rather
 * than first: an absent value is not a low value, and ranking "unknown" as
 * "best" would be the most misleading thing this screen could do.
 */
const NUTRIENT_SORTS = {
  sugar: 'ADDED_SUGAR_G',
  sodium: 'SODIUM_MG',
} as const;

type SortMode = 'relevance' | 'quality' | 'sugar' | 'sodium';

const SORT_MODES: ReadonlyArray<{ mode: SortMode; key: MessageKey }> = [
  { mode: 'relevance', key: 'search.sortRelevance' },
  { mode: 'quality', key: 'search.sortQuality' },
  { mode: 'sugar', key: 'search.sortSugar' },
  { mode: 'sodium', key: 'search.sortSodium' },
];

function isSortMode(value: string): value is SortMode {
  return SORT_MODES.some((s) => s.mode === value);
}

/** The comparable amount for a nutrient, or `null` when there is none to trust. */
function nutrientAmount(product: ProductSummary, nutrientCode: string): number | null {
  const fact = product.keyFacts.find((f) => f.nutrientCode === nutrientCode);
  if (!fact) return null;
  // Per 100 is the only basis that compares across products of different pack
  // sizes, which is exactly what an ordering needs.
  return isPlausible(fact.amountPer100, fact.unit) ? fact.amountPer100 : null;
}

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
  const sortParam = params.get('sort') ?? '';
  const sort: SortMode = isSortMode(sortParam) ? sortParam : 'relevance';

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 30 * 60 * 1000,
  });

  /**
   * Paged, not capped.
   *
   * This screen previously fetched a single page of 30 and offered no way to
   * see more, so 96% of the catalogue was unreachable by browsing at all. A
   * "Load more" button rather than scroll-triggered loading: it is operable by
   * keyboard and screen reader, it does not fight the back button, and it
   * leaves the end of the page reachable.
   */
  const results = useInfiniteQuery({
    queryKey: ['search', q, category],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      searchProducts({ q, category, page: pageParam, size: PAGE_SIZE }),
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.total ? lastPage.page + 1 : undefined;
    },
  });

  const categoryName = categories.data?.find((c) => c.code === category)?.name;

  const heading = q
    ? t('search.resultsFor', { query: q })
    : (categoryName ?? t('search.browseHeading'));

  useDocumentTitle(q ? t('search.resultsFor', { query: q }) : (categoryName ?? t('nav.search')));

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

  const items = useMemo<ProductSummary[]>(
    () => results.data?.pages.flatMap((p) => p.items) ?? [],
    [results.data],
  );
  const total = results.data?.pages[0]?.total ?? 0;
  const isEmpty = results.isSuccess && items.length === 0;

  // Re-orders the pages already in hand; never re-fetches and never invents a
  // value for a product missing one. Only worth offering once there is more
  // than one item to reorder.
  const sortedItems = useMemo<ProductSummary[]>(() => {
    if (sort === 'relevance') return items;

    if (sort === 'quality') {
      return [...items].sort((a, b) => qualityRank(a.dataQuality) - qualityRank(b.dataQuality));
    }

    const nutrientCode = NUTRIENT_SORTS[sort];
    return [...items].sort((a, b) => {
      const left = nutrientAmount(a, nutrientCode);
      const right = nutrientAmount(b, nutrientCode);
      if (left === null && right === null) return 0;
      if (left === null) return 1; // unknown is not "best" — it goes last
      if (right === null) return -1;
      return left - right;
    });
  }, [items, sort]);

  const canSort = results.isSuccess && items.length > 1;

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
        {/* The page's own <h1>. This screen previously started at <h2>, leaving
            the document with no top-level heading at all — the one route in the
            app where that was true. */}
        <h1 className="sr-only">{t('search.heading')}</h1>

        <div className="searchpage__toolbar">
          <SectionHeader
            title={heading}
            {...(results.isSuccess
              ? {
                  subtitle:
                    items.length < total
                      ? t('search.showingCount', { shown: items.length, total })
                      : t('search.resultCount', { count: total }),
                }
              : {})}
          />

          {canSort && (
            <div
              className="searchpage__sort"
              role="group"
              aria-label={t('search.sortLabel')}
            >
              {SORT_MODES.map(({ mode, key }) => (
                <Chip key={mode} active={sort === mode} onClick={() => setSort(mode)}>
                  {t(key)}
                </Chip>
              ))}
            </div>
          )}
        </div>

        {results.isError && (
          <ErrorState error={results.error} onRetry={() => void results.refetch()} />
        )}

        {isEmpty ? (
          <NotFound query={q} />
        ) : (
          <>
            <ProductGrid loading={results.isPending} products={sortedItems.map(toCardData)} />

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
                {/* Announces the new count to a screen-reader user, who would
                    otherwise get no signal that more rows arrived below them. */}
                <p className="sr-only" role="status">
                  {t('search.showingCount', { shown: items.length, total })}
                </p>
              </div>
            )}
          </>
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
