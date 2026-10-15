import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import type { ProductSummary } from '@/lib/catalog';
import { getCategoryIntelligence, getCategoryPicks, type CategoryIntelligence } from '@/lib/recommendation';
import { useIsSignedIn } from '@/lib/useSession';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { useBatchFit, type CardFit } from '@/lib/useBatchFit';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { categoryGlyph, categoryTint } from '@/design-system/CategoryArt';
import { ProductRail, SectionHeader } from '@/design-system/Rails';
import type { ProductCardData } from '@/design-system/ProductCard';
import { ErrorState, Skeleton } from '@/design-system/States';
import { Icon } from '@/design-system/Icon';
import { toCardData } from '../home/HomeScreen';
import './CategoryScreen.css';

type RailKey = keyof CategoryIntelligence['rails'];

const RAILS: ReadonlyArray<{ key: RailKey; title: MessageKey; sort?: string }> = [
  { key: 'betterChoices', title: 'category.rail.betterChoices', sort: 'SCORE_DESC' },
  { key: 'popular', title: 'category.rail.popular' },
  { key: 'lowestSugar', title: 'category.rail.lowestSugar', sort: 'SUGAR_ASC' },
  { key: 'lowestSodium', title: 'category.rail.lowestSodium', sort: 'SODIUM_ASC' },
  { key: 'highestProtein', title: 'category.rail.highestProtein', sort: 'PROTEIN_DESC' },
  { key: 'leastProcessed', title: 'category.rail.leastProcessed' },
];

function toCard(p: ProductSummary, fit: CardFit | undefined): ProductCardData {
  return {
    ...toCardData(p),
    verdict: fit?.verdict ?? null,
    allergenFlagged: fit?.allergenFlagged ?? false,
    ratingStars: p.assessment?.status === 'SCORED' ? p.assessment.stars : null,
  };
}

/**
 * A category page (D-060): what the category holds and how many are rated, then rails of real,
 * declared values — each rail is an ordering of what labels say, and a rail with nothing real
 * to show is left out rather than padded. Signed in, "picks for your household" come first.
 * Every rail links on into the full, filterable search for the category.
 */
export function CategoryScreen() {
  const { code = '' } = useParams();
  const signedIn = useIsSignedIn();
  const household = useMyHousehold({ enabled: signedIn });

  const intelligence = useQuery({
    queryKey: ['category-intelligence', code],
    queryFn: () => getCategoryIntelligence(code),
    enabled: code !== '',
  });

  const picks = useQuery({
    queryKey: ['category-picks', household.householdId, code],
    queryFn: () => getCategoryPicks(household.householdId as string, code),
    enabled: signedIn && household.householdId !== null && code !== '',
  });

  const allProducts = intelligence.data
    ? Object.values(intelligence.data.rails).flat().concat(picks.data?.map((p) => p.product) ?? [])
    : [];
  const fit = useBatchFit(allProducts.map((p) => p.id));

  useDocumentTitle(intelligence.data?.category.name ?? null);

  if (intelligence.isPending) {
    return (
      <div className="categorypage">
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }
  if (intelligence.isError) {
    return (
      <div className="categorypage">
        <ErrorState error={intelligence.error} onRetry={() => void intelligence.refetch()} />
      </div>
    );
  }

  const { category, counts, rails } = intelligence.data;
  const searchAll = `/search?category=${encodeURIComponent(category.code)}`;
  const shown = RAILS.filter((r) => rails[r.key].length > 0);

  return (
    <div className="categorypage">
      <header className="categorypage__hero" style={{ backgroundColor: categoryTint(category.code) }}>
        <span className="categorypage__glyph" aria-hidden="true">{categoryGlyph(category.code)}</span>
        <div>
          <h1 className="categorypage__title">{category.name}</h1>
          <p className="categorypage__counts">
            {counts.rated > 0
              ? t('category.countsRated', { products: counts.products, rated: counts.rated })
              : t('category.counts', { products: counts.products })}
          </p>
        </div>
        <Link to={searchAll} className="btn btn--primary btn--sm categorypage__all">
          <Icon name="search" size={16} />
          {t('category.browseAll')}
        </Link>
      </header>

      {signedIn && picks.isSuccess && picks.data.length > 0 && (
        <section className="categorypage__rail">
          <SectionHeader title={t('category.rail.picks')} subtitle={t('category.rail.picksHint')} />
          <ProductRail products={picks.data.map((p) => toCard(p.product, fit.get(p.product.id)))} />
        </section>
      )}

      {shown.map((rail) => (
        <section key={rail.key} className="categorypage__rail">
          {rail.sort ? (
            <SectionHeader title={t(rail.title)} actionLabel={t('category.seeAll')} actionTo={`${searchAll}&sort=${rail.sort}`} />
          ) : (
            <SectionHeader title={t(rail.title)} />
          )}
          <ProductRail products={rails[rail.key].map((p) => toCard(p, fit.get(p.id)))} />
        </section>
      ))}

      {shown.length === 0 && <p className="categorypage__empty">{t('category.empty')}</p>}

      {counts.rated === 0 && counts.products > 0 && <p className="categorypage__note">{t('category.notRatedYet')}</p>}
    </div>
  );
}
