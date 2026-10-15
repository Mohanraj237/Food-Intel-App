import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { getProduct, type NutrientRow, type ProductDetail } from '@/lib/catalog';
import { formatAmount, isPlausible } from '@/lib/nutrition';
import { displayProductName, normaliseBrand } from '@/lib/productName';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { getHouseholdFit, getProductHealth, type PersonalFit } from '@/lib/scoring';
import { recordActivity } from '@/lib/activity';
import { toSavedProduct, toggleSaved, useIsSaved } from '@/lib/saved';
import { MAX_COMPARE, compareHref, toggleTray, useIsInTray, useTray } from '@/lib/compareTray';
import { ProductImage } from '@/design-system/ProductImage';
import { AllergenFlag } from '@/design-system/AllergenFlag';
import { NutritionSummary } from '@/design-system/NutritionSummary';
import { ErrorState, Skeleton } from '@/design-system/States';
import { ReasonList } from '@/design-system/ReasonList';
import { VerdictChip, type Verdict } from '@/design-system/VerdictChip';
import { Icon } from '@/design-system/Icon';
import { Avatar } from '@/design-system/Avatar';
import { Badge } from '@/design-system/Badge';
import { useToast } from '@/design-system/Toast';
import { HealthAssessmentSection } from './HealthAssessmentSection';
import { AlternativesSection } from './AlternativesSection';
import { OutcomeCapture } from './OutcomeCapture';
import './ProductScreen.css';

/**
 * Product detail. The information order is fixed (D-003, D-061):
 *
 *   1. what the product is — and Save / Compare / Share
 *   2. the allergen hard flag, if any — above everything, its own colour
 *   3. the per-person verdicts (the headline), with "why", then better options;
 *      signed out, the product's rating sentence (if rated) and the sign-in strip
 *   4. how the product itself was read (collapsed): rating, drivers, processing level
 *   5. the nutrition that decides it, at a glance
 *   6. where the data came from
 *   7. the full label; ingredients with additive names and the diet flag
 *   8. what you decided
 *
 * The numeric score is never the largest element and never appears above the verdict.
 */
export function ProductScreen() {
  const { id = '' } = useParams();
  const signedIn = useIsSignedIn();

  const product = useQuery({
    queryKey: ['product', id],
    queryFn: () => getProduct(id),
    enabled: id !== '',
  });

  useEffect(() => {
    if (signedIn && product.isSuccess) {
      recordActivity({ productId: product.data.id, eventType: 'VIEWED' }).catch(() => undefined);
    }
  }, [signedIn, product.isSuccess, product.data?.id]);

  useDocumentTitle(
    product.data ? `${normaliseBrand(product.data.brandName)} ${displayProductName(product.data).text}`.trim() : null,
  );

  if (product.isPending) {
    return (
      <div className="pdp">
        <div className="pdp__layout">
          <div className="pdp__col-left">
            <div className="pdp__hero pdp__hero--loading" />
          </div>
          <div className="pdp__col-right">
            <div className="pdp__section">
              <Skeleton lines={4} label={t('loading.product')} />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (product.isError) {
    return (
      <div className="pdp pdp__section">
        <ErrorState error={product.error} onRetry={() => void product.refetch()} />
        <p className="pdp__notfound-help">{t('search.noResultsAction')}</p>
        <div className="inline-actions">
          <Link to="/scan?mode=label" className="btn btn--primary">
            {t('capture.heading')}
          </Link>
          <Link to="/search" className="btn btn--ghost">
            {t('nav.search')}
          </Link>
        </div>
      </div>
    );
  }

  const p = product.data;
  const brand = normaliseBrand(p.brandName);
  const displayName = displayProductName({
    name: p.name,
    brandName: p.brandName,
    packSize: p.packSize,
    categoryName: p.categoryName,
  });
  const metaParts = [
    p.packSize?.value != null ? `${formatAmount(p.packSize.value)} ${p.packSize.unit ?? ''}`.trim() : '',
    p.categoryName ?? '',
  ].filter((part) => part.length > 0);
  const declaredAllergens = p.allergens.filter((a) => a.declarationType === 'CONTAINS');
  const mayContain = p.allergens.filter((a) => a.declarationType === 'MAY_CONTAIN');
  const version = p.currentVersion;
  const per100Unit: 'g' | 'ml' = p.packSize?.unit === 'ml' ? 'ml' : 'g';

  return (
    <div className="pdp">
      <div className="pdp__layout">
        <div className="pdp__col-left">
          <nav className="pdp__crumbs" aria-label={t('product.breadcrumbAll')}>
            <Link to="/search" className="pdp__crumb">
              <Icon name="arrow-left" size={16} />
              {t('product.breadcrumbAll')}
            </Link>
            {p.categoryName && p.categoryCode && (
              <>
                <span className="pdp__crumb-sep" aria-hidden="true">
                  /
                </span>
                <Link to={`/search?category=${encodeURIComponent(p.categoryCode)}`} className="pdp__crumb">
                  {p.categoryName}
                </Link>
              </>
            )}
          </nav>

          <header className="pdp__hero">
            <div className="pdp__media">
              <ProductImage
                imageUrl={p.imageUrl}
                categoryCode={p.categoryCode}
                seed={p.id + p.name}
                alt=""
                classNames={{
                  img: 'pdp__art pdp__art--photo',
                  art: `pdp__art pdp__art--fallback${p.imageUrl ? '' : ' pdp__art--placeholder'}`,
                  glyph: 'pdp__glyph',
                }}
              />
            </div>
            <div className="pdp__identity">
              <p className="pdp__brand">{brand}</p>
              <h1 className={displayName.isPlaceholder ? 'pdp__name pdp__name--placeholder' : 'pdp__name'}>
                {displayName.text}
              </h1>
              {metaParts.length > 0 && <p className="pdp__meta">{metaParts.join(' · ')}</p>}
              {displayName.isPlaceholder && <p className="pdp__meta">{t('product.nameMissing')}</p>}
            </div>
            <ProductActions product={p} />
          </header>
        </div>

        <div className="pdp__col-right">
          {/* 2 — allergen flag */}
          {declaredAllergens.length > 0 && (
            <div className="pdp__section">
              <AllergenFlag allergens={declaredAllergens.map((a) => a.displayName)} />
            </div>
          )}
          {mayContain.length > 0 && declaredAllergens.length === 0 && (
            <div className="pdp__section">
              <AllergenFlag allergens={mayContain.map((a) => a.displayName)} mayContain />
            </div>
          )}

          {/* 3 — per-person verdicts, or the sign-in boundary that explains itself */}
          <section className="pdp__section" aria-labelledby="fit-heading">
            <h2 id="fit-heading" className="pdp__h2">
              {t('fit.heading')}
            </h2>
            {signedIn ? (
              <FitSection productId={p.id} nutrients={p.nutrients} />
            ) : (
              <>
              <ProductRatingLine productId={p.id} />
              <AlternativesSection householdId={null} productId={p.id} members={[]} />
              <div className="pdp__signin">
                <span className="pdp__signin-icon">
                  <Icon name="family" size={22} />
                </span>
                <div>
                  <p className="pdp__signin-title">{t('fit.signInPrompt')}</p>
                  <p className="pdp__signin-sub">{t('fit.signInReason')}</p>
                </div>
                <Link to="/you" className="btn btn--primary btn--sm">
                  {t('action.signIn')}
                </Link>
              </div>
              </>
            )}
          </section>

          {/* 4 — why: how the product itself was read (collapsed; never above the verdicts) */}
          <section className="pdp__section">
            <HealthAssessmentSection productId={p.id} nutrients={p.nutrients} />
          </section>

          {/* 5 — the nutrition that decides it, at a glance */}
          <section className="pdp__section" aria-labelledby="glance-heading">
            <div className="pdp__section-head">
              <h2 id="glance-heading" className="pdp__h2">
                {t('product.atAGlance')}
              </h2>
              <span className="pdp__basis">{per100Unit === 'ml' ? t('product.per100ml') : t('product.per100g')}</span>
            </div>
            <KeyFacts nutrients={p.nutrients} />
          </section>

          {/* 6 — where the data came from */}
          <section className="pdp__section" aria-labelledby="source-heading">
            <h2 id="source-heading" className="pdp__h2">
              {t('product.sourceHeading')}
            </h2>
            <div className="provenance">
              <div className="provenance__row">
                <Badge tone={qualityTone(version.dataQuality)} icon={qualityIcon(version.dataQuality)} size="md">
                  {t(`dataQuality.${version.dataQuality}` as Parameters<typeof t>[0])}
                </Badge>
                <span className="provenance__line">
                  {t('product.lastVerified', { date: formatDate(version.verifiedAt ?? version.capturedAt) })}
                </span>
              </div>
              {version.versionNo > 1 && (
                <p className="provenance__line">{t('product.reformulated', { date: formatDate(version.effectiveFrom) })}</p>
              )}
              {p.hasOpenConflict && <p className="provenance__conflict">{t('product.dataNeedsVerification')}</p>}
              {version.captureMethod === 'OFF_IMPORT' && (
                <p className="provenance__line">
                  <a href="https://world.openfoodfacts.org" target="_blank" rel="noreferrer">
                    {t('product.openFoodFactsAttribution')}
                  </a>
                </p>
              )}
              <p className="provenance__note">{t('product.readThePack')}</p>
            </div>
          </section>

          {/* 6 — the full label */}
          <section className="pdp__section" aria-labelledby="nutrition-heading">
            <details className="pdp__details">
              <summary className="pdp__details-summary">
                <span id="nutrition-heading" className="pdp__h2">
                  {t('product.fullLabel')}
                </span>
                <Icon name="chevron-down" size={20} className="pdp__details-chev" />
              </summary>
              <div className="pdp__details-body">
                <NutritionSummary
                  nutrients={p.nutrients}
                  per100Unit={per100Unit}
                  servingDeclaredText={version.servingDeclaredText}
                  servingImplausible={version.servingPlausible === false}
                />
              </div>
            </details>
          </section>

          <section className="pdp__section" aria-labelledby="ingredients-heading">
            <h2 id="ingredients-heading" className="pdp__h2">
              {t('product.ingredients')}
            </h2>
            {p.ingredients.length === 0 ? (
              <p className="pdp__pending">{t('product.notDeclared')}</p>
            ) : (
              <p className="pdp__ingredients">
                {p.ingredients
                  .slice()
                  .sort((a, b) => a.position - b.position)
                  .map((i) => i.rawText)
                  .join(', ')}
              </p>
            )}
            {version.dietFlag && version.dietFlag !== 'UNKNOWN' && (
              <p className="pdp__diet">
                <Badge tone="neutral" icon="leaf" size="md">
                  {t(`product.diet.${version.dietFlag}` as Parameters<typeof t>[0])}
                </Badge>
                {version.dietFlagEstimated && <span className="pdp__diet-note">{t('product.dietEstimated')}</span>}
              </p>
            )}
            <AdditiveList ingredients={p.ingredients} />
          </section>

          {/* 7 — what you decided */}
          <section className="pdp__section">
            <OutcomeCapture productId={p.id} />
          </section>

          <section className="pdp__section pdp__section--last">
            <p className="pdp__disclaimer">{t('medical.notice')}</p>
          </section>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------- rating line */

/**
 * For a visitor who is not signed in, the product-level rating is the only result there is,
 * so it gets one plain sentence above the sign-in strip — when, and only when, the product is
 * actually rated. Always attributed to the draft method it comes from (D-058).
 */
function ProductRatingLine({ productId }: { productId: string }) {
  const health = useQuery({ queryKey: ['product-health', productId], queryFn: () => getProductHealth(productId) });
  if (!health.isSuccess || health.data.status !== 'SCORED' || health.data.stars == null) return null;
  return <p className="pdp__rating">{t('product.ratingLine', { stars: formatAmount(health.data.stars) })}</p>;
}

/* -------------------------------------------------------------- additives */

/**
 * Additives the label names by INS/E number, with their Codex name and functional class —
 * what they are, never whether they are good or bad (V15, D-059).
 */
function AdditiveList({ ingredients }: { ingredients: ProductDetail['ingredients'] }) {
  const named = ingredients
    .filter((i) => i.isAdditive && i.additiveCode && i.additiveName)
    .sort((a, b) => a.position - b.position);
  if (named.length === 0) return null;
  return (
    <div className="pdp__additives">
      <p className="pdp__additives-heading">{t('product.additivesHeading')}</p>
      <ul className="pdp__additives-list">
        {named.map((i) => (
          <li key={`${i.position}-${i.additiveCode}`}>
            {t('product.additiveLine', {
              code: i.additiveCode ?? '',
              name: i.additiveName ?? '',
              function: (i.additiveFunction ?? '').toLowerCase(),
            })}
          </li>
        ))}
      </ul>
    </div>
  );
}

/* --------------------------------------------------------------- actions */

function ProductActions({ product }: { product: ProductDetail }) {
  const saved = useIsSaved(product.id);
  const inTray = useIsInTray(product.id);
  const tray = useTray();
  const toast = useToast();

  function toggleCompare() {
    const result = toggleTray({
      id: product.id,
      brandName: product.brandName,
      name: product.name,
      imageUrl: product.imageUrl,
      categoryCode: product.categoryCode,
    });
    if (result === 'full') toast.show(t('compare.trayFull', { max: MAX_COMPARE }), 'info');
  }
  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  function save() {
    const nowSaved = toggleSaved(toSavedProduct(product));
    toast.show(nowSaved ? t('saved.added') : t('saved.removed'), nowSaved ? 'heart-filled' : 'check');
  }

  async function share() {
    try {
      await navigator.share({
        title: `${normaliseBrand(product.brandName)} ${displayProductName(product).text}`.trim(),
        url: window.location.href,
      });
    } catch {
      // Dismissed, or not permitted: nothing to report.
    }
  }

  return (
    <div className="pdp__actions">
      <button type="button" className={saved ? 'btn btn--secondary btn--sm pdp__save pdp__save--on' : 'btn btn--secondary btn--sm pdp__save'} aria-pressed={saved} onClick={save}>
        <Icon name={saved ? 'heart-filled' : 'heart'} size={18} />
        {saved ? t('saved.savedLabel') : t('saved.saveLabel')}
      </button>
      <button
        type="button"
        className={inTray ? 'btn btn--secondary btn--sm pdp__save pdp__save--on' : 'btn btn--secondary btn--sm'}
        aria-pressed={inTray}
        onClick={toggleCompare}
      >
        <Icon name={inTray ? 'check' : 'compare'} size={18} />
        {inTray ? t('compare.inTray', { count: tray.length }) : t('compare.addToTray')}
      </button>
      {inTray && tray.length >= 2 && (
        <Link to={compareHref(tray.map((i) => i.id))} className="btn btn--primary btn--sm">
          {t('compare.trayOpen', { count: tray.length })}
        </Link>
      )}
      {canShare && (
        <button type="button" className="icon-btn" aria-label={t('product.share')} onClick={() => void share()}>
          <Icon name="share" size={18} />
        </button>
      )}
    </div>
  );
}

/* --------------------------------------------------------------- key facts */

const KEY_FACTS: ReadonlyArray<{ codes: readonly string[]; labelKey: Parameters<typeof t>[0] }> = [
  { codes: ['ENERGY_KCAL'], labelKey: 'today.energy' },
  { codes: ['ADDED_SUGAR_G', 'TOTAL_SUGAR_G'], labelKey: 'product.fact.sugar' },
  { codes: ['SODIUM_MG'], labelKey: 'today.sodium' },
  { codes: ['PROTEIN_G'], labelKey: 'today.protein' },
  { codes: ['FIBRE_G'], labelKey: 'today.fibre' },
  { codes: ['SAT_FAT_G'], labelKey: 'product.fact.satFat' },
];

/**
 * Six numbers per 100 g, the ones that most often decide a result. A missing value is said
 * plainly, never shown as zero; a figure that cannot be true is withheld (lib/nutrition.ts).
 */
function KeyFacts({ nutrients }: { nutrients: NutrientRow[] }) {
  if (nutrients.length === 0) {
    return <p className="pdp__pending">{t('error.product_data_insufficient')}</p>;
  }

  return (
    <ul className="facts" role="list">
      {KEY_FACTS.map((fact) => {
        const row = fact.codes.map((code) => nutrients.find((n) => n.nutrientCode === code)).find((n) => n !== undefined);
        const label = row && fact.codes[0] !== row.nutrientCode ? row.displayName : t(fact.labelKey);
        const value = row && isPlausible(row.amountPer100, row.unit) ? row.amountPer100 : null;
        return (
          <li key={fact.codes[0]} className={value === null ? 'facts__item facts__item--empty' : 'facts__item'}>
            <span className="facts__label">{label}</span>
            {value === null ? (
              <span className="facts__missing">{t('product.notDeclaredShort')}</span>
            ) : (
              <span className="facts__value">
                {formatAmount(value)}
                <span className="facts__unit">{row?.unit}</span>
              </span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/* ------------------------------------------------------------ family fit */

function FitSection({ productId, nutrients }: { productId: string; nutrients: NutrientRow[] }) {
  const myHousehold = useMyHousehold();
  const householdId = myHousehold.householdId;

  const members = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: householdId !== null,
  });

  const fit = useQuery({
    queryKey: ['household-fit', householdId, productId],
    queryFn: () => getHouseholdFit(householdId as string, productId),
    enabled: householdId !== null,
  });

  if (myHousehold.isPending) return <Skeleton lines={2} label={t('loading.fit')} />;
  if (myHousehold.isError) return <ErrorState error={myHousehold.error} onRetry={() => void myHousehold.refetch()} />;
  if (householdId === null) return <NoHouseholdMembers />;
  if (members.isPending || fit.isPending) return <Skeleton lines={2} label={t('loading.fit')} />;
  if (members.isError) return <ErrorState error={members.error} onRetry={() => void members.refetch()} />;
  if (fit.isError) return <ErrorState error={fit.error} onRetry={() => void fit.refetch()} />;
  if (members.data.length === 0) return <NoHouseholdMembers />;

  const fitByMemberId = new Map(fit.data.members.map((m) => [m.memberId, m]));

  return (
    <>
      <ul className="fit">
        {members.data.map((member: HouseholdMember) => {
          const memberFit = fitByMemberId.get(member.id);
          if (!memberFit) return null;
          return <MemberFitRow key={member.id} member={member} fit={memberFit} nutrients={nutrients} />;
        })}
      </ul>

      <AlternativesSection householdId={householdId} productId={productId} members={members.data} />
    </>
  );
}

function MemberFitRow({ member, fit, nutrients }: { member: HouseholdMember; fit: PersonalFit; nutrients: NutrientRow[] }) {
  const isChild = member.memberType === 'CHILD';

  return (
    <li className="fit__row">
      <Avatar name={member.displayLabel} child={isChild} />
      <div className="fit__body">
        <span className="fit__name">{member.displayLabel}</span>
        {fit.status === 'ASSESSED' && fit.verdict && (
          <>
            <VerdictChip verdict={fit.verdict as Verdict} size="sm" />
            {fit.allergenMatches.length > 0 && (
              <p className="fit__allergen">
                <Icon name="alert" size={14} />
                {t('allergen.heading', {
                  allergen: fit.allergenMatches
                    .map((code) => t(`allergenCode.${code}` as Parameters<typeof t>[0]))
                    .join(', '),
                })}
              </p>
            )}
            {fit.reasons.length > 0 && (
              <details className="fit__why">
                <summary>{t('action.seeWhy')}</summary>
                <ReasonList reasons={fit.reasons} nutrients={nutrients} />
              </details>
            )}
          </>
        )}
        {fit.status === 'NO_PROFILE' && (
          <p className="fit__note">
            {t('fit.noProfileShort')}{' '}
            <Link to={isChild ? `/family/${member.id}/profile` : '/you/profile'}>{t('profile.setUpAction')}</Link>
          </p>
        )}
        {(fit.status === 'CONSENT_REQUIRED' || fit.status === 'CONSENT_WITHDRAWN') && (
          <p className="fit__note">
            {fit.status === 'CONSENT_WITHDRAWN'
              ? t('fit.consentWithdrawn')
              : isChild
                ? t('fit.childConsentRequired')
                : t('fit.consentRequired')}
          </p>
        )}
      </div>
    </li>
  );
}

function NoHouseholdMembers() {
  return (
    <div className="pdp__nomembers">
      <p className="pdp__pending">{t('fit.noMembers')}</p>
      <Link to="/welcome" className="btn btn--secondary btn--sm">
        {t('household.create')}
      </Link>
    </div>
  );
}

/* ------------------------------------------------------------------ misc */

function qualityTone(dataQuality: string): 'quality-good' | 'quality-warn' | 'quality-stale' | 'neutral' {
  switch (dataQuality) {
    case 'VERIFIED':
      return 'quality-good';
    case 'CONFLICTING':
    case 'NEEDS_REVIEW':
      return 'quality-warn';
    case 'STALE':
      return 'quality-stale';
    default:
      return 'neutral';
  }
}

function qualityIcon(dataQuality: string): 'shield' | 'alert' | 'clock' | 'info' {
  switch (dataQuality) {
    case 'VERIFIED':
      return 'shield';
    case 'CONFLICTING':
    case 'NEEDS_REVIEW':
      return 'alert';
    case 'STALE':
      return 'clock';
    default:
      return 'info';
  }
}

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(iso));
  } catch {
    return iso.slice(0, 10);
  }
}
