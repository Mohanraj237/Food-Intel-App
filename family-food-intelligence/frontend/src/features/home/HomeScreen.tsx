import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { getCategories, headlineFact, searchProducts, type ProductSummary } from '@/lib/catalog';
import { getRecentActivity } from '@/lib/activity';
import { useSaved } from '@/lib/saved';
import { CategoryGrid, CategoryGridSkeleton, ProductRail, SectionHeader } from '@/design-system/Rails';
import type { ProductCardData } from '@/design-system/ProductCard';
import { ErrorState } from '@/design-system/States';
import { SearchBar } from '@/design-system/SearchBar';
import { Icon } from '@/design-system/Icon';
import { FoodReadinessCard } from './FoodReadinessCard';
import { TodayGlance } from './TodayGlance';
import './HomeScreen.css';

/**
 * Home — the landing screen for everyone.
 *
 * Answers the product's one question first ("is this a good choice for the people I shop
 * for?") with search and scan in reach of a thumb, then gives a signed-in household its own
 * context (how the household is set up, how today is going), then opens the shelf: categories,
 * saved products, curated rails and what was looked at recently.
 */
export function HomeScreen() {
  const signedIn = useIsSignedIn();
  const navigate = useNavigate();
  const me = useMeQuery({ enabled: signedIn });
  const [query, setQuery] = useState('');
  const saved = useSaved();

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 30 * 60 * 1000,
  });

  const popular = useQuery({ queryKey: ['rail', 'popular'], queryFn: () => searchProducts({ size: 10 }) });
  const biscuits = useQuery({
    queryKey: ['rail', 'BISCUITS'],
    queryFn: () => searchProducts({ category: 'BISCUITS', size: 10 }),
  });
  const beverages = useQuery({
    queryKey: ['rail', 'BEVERAGES'],
    queryFn: () => searchProducts({ category: 'BEVERAGES', size: 10 }),
  });

  const recent = useQuery({ queryKey: ['activity', 'recent'], queryFn: getRecentActivity, enabled: signedIn });
  const recentProducts = dedupe(
    (recent.data ?? []).map((entry) => entry.product).filter((p): p is ProductSummary => p !== null),
  );

  const catalogueUnavailable = popular.isError;
  const firstName = me.data?.member?.displayLabel ?? null;
  const householdName = me.data?.household && !me.data.household.individual ? me.data.household.name : null;

  function submitSearch(trimmed: string) {
    navigate(trimmed ? `/search?q=${encodeURIComponent(trimmed)}` : '/search');
  }

  return (
    <div className="home">
      {/* ---- hero: the promise, and the two ways in ---- */}
      <section className="home__hero" aria-labelledby="home-title">
        <div className="home__hero-inner">
          <p className="home__greeting">
            {signedIn && firstName ? t(timeGreetingKey(), { name: firstName }) : t('home.welcome')}
          </p>
          <h1 id="home-title" className="home__title">
            {householdName ? t('home.heroTitleFamily', { household: householdName }) : t('home.heroTitle')}
          </h1>
          <p className="home__sub">{t('home.heroSub')}</p>

          <div className="home__search">
            <SearchBar id="home-search" variant="hero" value={query} onChange={setQuery} onSubmit={submitSearch} />
          </div>

          <div className="home__actions">
            <Link to="/scan" className="qaction qaction--scan">
              <span className="qaction__glyph">
                <Icon name="scan" size={24} />
              </span>
              <span className="qaction__text">
                <span className="qaction__title">{t('scan.heading')}</span>
                <span className="qaction__sub">{t('home.scanSub')}</span>
              </span>
              <Icon name="chevron-right" size={18} className="qaction__chev" />
            </Link>
            <Link to="/scan?mode=label" className="qaction qaction--label">
              <span className="qaction__glyph">
                <Icon name="camera" size={24} />
              </span>
              <span className="qaction__text">
                <span className="qaction__title">{t('capture.heading')}</span>
                <span className="qaction__sub">{t('home.labelSub')}</span>
              </span>
              <Icon name="chevron-right" size={18} className="qaction__chev" />
            </Link>
          </div>
        </div>
      </section>

      <div className="home__body">
        {/* ---- your context: today, and how the household is set up ---- */}
        {signedIn && <TodayGlance />}
        {signedIn && <FoodReadinessCard />}

        {!signedIn && (
          <section className="home__signin">
            <div className="home__signin-icon">
              <Icon name="family" size={26} />
            </div>
            <div className="home__signin-text">
              <p className="home__signin-title">{t('home.signInTitle')}</p>
              <p className="home__signin-sub">{t('home.signInSub')}</p>
            </div>
            <Link to="/you" className="btn btn--primary btn--sm home__signin-btn">
              {t('action.signIn')}
            </Link>
          </section>
        )}

        {/* ---- the shelf ---- */}
        <section aria-labelledby="cat-heading" className="home__section">
          <SectionHeader title={t('home.browseCategories')} actionLabel={t('home.seeAll')} actionTo="/search" />
          <div id="cat-heading" className="sr-only">{t('home.browseCategories')}</div>
          {categories.isPending && <CategoryGridSkeleton />}
          {categories.isSuccess && (
            <CategoryGrid categories={categories.data.map((c) => ({ code: c.code, name: c.name }))} />
          )}
          {categories.isError && <ErrorState error={categories.error} onRetry={() => void categories.refetch()} />}
        </section>

        {saved.length > 0 && (
          <section className="home__section">
            <SectionHeader
              title={t('saved.heading')}
              subtitle={t('saved.count', { count: saved.length })}
              actionLabel={t('home.seeAll')}
              actionTo="/saved"
            />
            <ProductRail
              products={saved.slice(0, 10).map((p) => ({
                id: p.id,
                brandName: p.brandName,
                name: p.name,
                packSize: p.packSize,
                categoryCode: p.categoryCode,
                categoryName: p.categoryName,
                imageUrl: p.imageUrl,
              }))}
            />
          </section>
        )}

        {catalogueUnavailable ? (
          <section className="home__section">
            <ErrorState error={popular.error} onRetry={() => void popular.refetch()} />
          </section>
        ) : (
          <>
            <Rail title={t('home.railPopular')} subtitle={t('home.railPopularSub')} query={popular} seeAllTo="/search" />
            <Rail title={t('home.railBiscuits')} query={biscuits} seeAllTo="/search?category=BISCUITS" />
            <Rail title={t('home.railBeverages')} query={beverages} seeAllTo="/search?category=BEVERAGES" />
          </>
        )}

        {signedIn && recentProducts.length > 0 && (
          <section className="home__section">
            <SectionHeader title={t('home.railRecent')} actionLabel={t('home.seeAll')} actionTo="/history" />
            <ProductRail loading={false} products={recentProducts.map(toCardData)} />
          </section>
        )}

        <div className="home__trust">
          <div className="home__trust-item">
            <Icon name="shield" size={20} />
            <span>{t('home.trustSource')}</span>
          </div>
          <div className="home__trust-item">
            <Icon name="lock" size={20} />
            <span>{t('home.trustPrivate')}</span>
          </div>
          <div className="home__trust-item">
            <Icon name="leaf" size={20} />
            <span>{t('home.trustNoAds')}</span>
          </div>
        </div>

        <p className="home__disclaimer">{t('medical.notice')}</p>
      </div>
    </div>
  );
}

function timeGreetingKey(): 'home.greetingMorningName' | 'home.greetingAfternoonName' | 'home.greetingEveningName' {
  const hour = new Date().getHours();
  if (hour < 12) return 'home.greetingMorningName';
  if (hour < 17) return 'home.greetingAfternoonName';
  return 'home.greetingEveningName';
}

/** The same product viewed three times is one row on a "recently viewed" rail. */
function dedupe(products: ProductSummary[]): ProductSummary[] {
  const seen = new Set<string>();
  return products.filter((p) => {
    if (seen.has(p.id)) return false;
    seen.add(p.id);
    return true;
  });
}

/** One curated rail. Renders nothing at all when the catalogue has no match. */
function Rail({
  title,
  subtitle,
  query,
  seeAllTo,
}: {
  title: string;
  subtitle?: string;
  query: { isPending: boolean; isSuccess: boolean; data: { items: ProductSummary[] } | undefined };
  seeAllTo: string;
}) {
  if (query.isSuccess && (query.data?.items.length ?? 0) === 0) {
    return null;
  }

  return (
    <section className="home__section">
      <SectionHeader
        title={title}
        {...(subtitle ? { subtitle } : {})}
        actionLabel={t('home.seeAll')}
        actionTo={seeAllTo}
      />
      <ProductRail loading={query.isPending} products={(query.data?.items ?? []).map(toCardData)} />
    </section>
  );
}

export function toCardData(p: ProductSummary): ProductCardData {
  return {
    id: p.id,
    brandName: p.brandName,
    name: p.name,
    packSize: p.packSize,
    categoryCode: p.categoryCode,
    categoryName: p.categoryName,
    imageUrl: p.imageUrl,
    dataQuality: p.dataQuality,
    headlineFact: headlineFact(p),
  };
}
