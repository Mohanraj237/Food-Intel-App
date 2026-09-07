import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { getCategories, headlineFact, searchProducts, type ProductSummary } from '@/lib/catalog';
import { getRecentActivity } from '@/lib/activity';
import {
  CategoryRail,
  CategoryRailSkeleton,
  ProductRail,
  SectionHeader,
} from '@/design-system/Rails';
import type { ProductCardData } from '@/design-system/ProductCard';
import { ErrorState } from '@/design-system/States';
import './HomeScreen.css';

/**
 * Home.
 *
 * The screen answers one question — *what are you looking at?* — and then gets
 * out of the way. Search lives in the shell header, so this surface is for
 * browsing: quick actions, categories, and curated rails.
 *
 * What it deliberately is not: a dashboard. No score summary, no streak, no
 * weekly total. Those would turn a decision tool into a tracker, which is the
 * category with the worst retention in the market and the thing this product is
 * explicitly not (Strategy §"What this is not", D-005).
 */
export function HomeScreen() {
  const signedIn = useIsSignedIn();

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 30 * 60 * 1000,
  });

  // Curated rails. Each is a real catalogue query, not a hand-picked list —
  // so a rail is empty when the catalogue genuinely has nothing to show, and
  // says so, rather than being padded.
  const popular = useQuery({
    queryKey: ['rail', 'popular'],
    queryFn: () => searchProducts({ size: 10 }),
  });

  const biscuits = useQuery({
    queryKey: ['rail', 'BISCUITS'],
    queryFn: () => searchProducts({ category: 'BISCUITS', size: 10 }),
  });

  const beverages = useQuery({
    queryKey: ['rail', 'BEVERAGES'],
    queryFn: () => searchProducts({ category: 'BEVERAGES', size: 10 }),
  });

  // Real, never faked (FR-012): only rendered once the activity backend actually
  // has rows for this account. A signed-out visitor or one with no history yet
  // sees no section at all — not an empty placeholder pretending to be a feature.
  const recent = useQuery({
    queryKey: ['activity', 'recent'],
    queryFn: getRecentActivity,
    enabled: signedIn,
  });
  const recentProducts = (recent.data ?? [])
    .map((entry) => entry.product)
    .filter((p): p is ProductSummary => p !== null);

  const catalogueUnavailable = popular.isError;

  return (
    <div className="home">
      {/* The value statement — restrained, not a marketing banner. Answers
          "what is this app" for a first-time visitor in one line, then gets
          out of the way; everything below is browsing, not more pitching. */}
      <section className="home__hero">
        {signedIn && <p className="home__hero-eyebrow">{t(timeGreetingKey())}</p>}
        <h1 className="home__hero-title">{t('home.heroTitle')}</h1>
        <p className="home__hero-sub">{t('home.heroSub')}</p>
      </section>

      {/* Utility zone: the two actions search cannot serve, plus the one
          boundary-stated consent prompt. Grouped and spaced tighter than the
          browsing surface below so they read as one entry strip rather than
          competing for the same visual weight as the rails. */}
      <div className="home__top">
        <section className="home__actions" aria-label={t('search.placeholder')}>
          <Link to="/scan" className="qaction qaction--scan">
            <span className="qaction__glyph" aria-hidden="true">▣</span>
            <span className="qaction__text">
              <span className="qaction__title">{t('scan.heading')}</span>
              <span className="qaction__sub">{t('scan.hint')}</span>
            </span>
          </Link>
          <Link to="/scan?mode=label" className="qaction qaction--label">
            <span className="qaction__glyph" aria-hidden="true">◎</span>
            <span className="qaction__text">
              <span className="qaction__title">{t('capture.heading')}</span>
              <span className="qaction__sub">{t('capture.hint')}</span>
            </span>
          </Link>
        </section>

        {!signedIn && (
          <section className="home__signin">
            <div className="home__signin-text">
              <p className="home__signin-title">{t('fit.signInPrompt')}</p>
              <p className="home__signin-sub">{t('fit.signInReason')}</p>
            </div>
            <Link to="/settings" className="btn btn--primary btn--sm home__signin-btn">
              {t('action.signIn')}
            </Link>
          </section>
        )}
      </div>

      {/* Browsing surface: categories, curated rails, then recent activity
          last — a returning household's history is a bonus, not something
          that should interrupt discovery for everyone else. Evenly spaced as
          one continuous shelf rather than a stack of independent widgets. */}
      <div className="home__browse">
        <section aria-labelledby="cat-heading">
          <SectionHeader title={t('home.browseCategories')} />
          <div id="cat-heading" className="sr-only">{t('home.browseCategories')}</div>
          {categories.isPending && <CategoryRailSkeleton />}
          {categories.isSuccess && (
            <CategoryRail
              categories={categories.data.map((c) => ({ code: c.code, name: c.name }))}
            />
          )}
          {categories.isError && (
            <ErrorState error={categories.error} onRetry={() => void categories.refetch()} />
          )}
        </section>

        {catalogueUnavailable ? (
          <section>
            <ErrorState error={popular.error} onRetry={() => void popular.refetch()} />
          </section>
        ) : (
          <>
            <Rail
              title={t('home.railPopular')}
              subtitle={t('home.railPopularSub')}
              query={popular}
              seeAllTo="/search"
            />
            <Rail
              title={t('home.railBiscuits')}
              query={biscuits}
              seeAllTo="/search?category=BISCUITS"
            />
            <Rail
              title={t('home.railBeverages')}
              query={beverages}
              seeAllTo="/search?category=BEVERAGES"
            />
          </>
        )}

        {signedIn && recentProducts.length > 0 && (
          <section className="home__rail">
            <SectionHeader title={t('home.railRecent')} />
            <ProductRail loading={false} products={recentProducts.map(toCardData)} />
          </section>
        )}
      </div>

      <p className="home__disclaimer">{t('medical.notice')}</p>
    </div>
  );
}

/** A time-of-day greeting — a function of the visitor's own clock, not of any
 *  stored data about them. Shown only when signed in, and only as a small
 *  eyebrow above the hero title, never as a headline of its own — this is not
 *  personalisation (D-020/D-024), just a plain courtesy. */
function timeGreetingKey(): 'home.greetingMorning' | 'home.greetingAfternoon' | 'home.greetingEvening' {
  const hour = new Date().getHours();
  if (hour < 12) return 'home.greetingMorning';
  if (hour < 17) return 'home.greetingAfternoon';
  return 'home.greetingEvening';
}

/** One curated rail. Renders nothing at all when the catalogue has no match —
 *  an empty rail with a heading is worse than no rail. */
function Rail({
  title,
  subtitle,
  query,
  seeAllTo,
}: {
  title: string;
  subtitle?: string;
  // `data: T | undefined` rather than an optional property: with
  // exactOptionalPropertyTypes a `?` means "may be absent", which is not the same
  // as TanStack's "present and undefined until it resolves".
  query: { isPending: boolean; isSuccess: boolean; data: { items: ProductSummary[] } | undefined };
  seeAllTo: string;
}) {
  if (query.isSuccess && (query.data?.items.length ?? 0) === 0) {
    return null;
  }

  return (
    <section className="home__rail">
      <SectionHeader
        title={title}
        {...(subtitle ? { subtitle } : {})}
        actionLabel={t('home.seeAll')}
        actionTo={seeAllTo}
      />
      <ProductRail
        loading={query.isPending}
        products={(query.data?.items ?? []).map(toCardData)}
      />
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
