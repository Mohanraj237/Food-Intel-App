import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { getCategories, searchProducts } from '@/lib/catalog';
import { Chip, ProductGrid, SectionHeader } from '@/design-system/Rails';
import { ErrorState } from '@/design-system/States';
import { toCardData } from '../home/HomeScreen';
import './SearchScreen.css';

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

  const heading = q
    ? t('search.resultsFor', { query: q })
    : category
      ? (categories.data?.find((c) => c.code === category)?.name ?? t('nav.search'))
      : t('nav.search');

  const items = results.data?.items ?? [];
  const isEmpty = results.isSuccess && items.length === 0;

  return (
    <div className="searchpage">
      {categories.isSuccess && categories.data.length > 0 && (
        <div className="chip-row" role="group" aria-label={t('home.browseCategories')}>
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
        <SectionHeader
          title={heading}
          {...(results.isSuccess
            ? { subtitle: t('search.resultCount', { count: results.data.total }) }
            : {})}
        />

        {results.isError && (
          <ErrorState error={results.error} onRetry={() => void results.refetch()} />
        )}

        {isEmpty ? (
          <NotFound query={q} />
        ) : (
          <ProductGrid loading={results.isPending} products={items.map(toCardData)} />
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
