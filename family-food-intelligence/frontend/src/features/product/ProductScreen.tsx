import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { getProduct, type NutrientRow } from '@/lib/catalog';
import { getHouseholdFit, type PersonalFit } from '@/lib/scoring';
import { recordActivity } from '@/lib/activity';
import { ProductImage } from '@/design-system/ProductImage';
import { AllergenFlag } from '@/design-system/AllergenFlag';
import { NutritionSummary } from '@/design-system/NutritionSummary';
import { ErrorState, Skeleton } from '@/design-system/States';
import { ReasonList } from '@/design-system/ReasonList';
import { VerdictChip, type Verdict } from '@/design-system/VerdictChip';
import { HealthAssessmentSection } from './HealthAssessmentSection';
import { AlternativesSection } from './AlternativesSection';
import { OutcomeCapture } from './OutcomeCapture';
import './ProductScreen.css';

/**
 * Product detail.
 *
 * The information order is fixed and is the most consequential design decision
 * on the screen (Pack 10 §2, D-003):
 *
 *   1. what the product is
 *   2. the allergen hard flag, if any — above everything, its own colour
 *   3. the per-person verdict strip
 *   4. why, in plain language, naming the nutrient and the standard
 *   5. better options
 *   6. where the data came from and when it was checked
 *   7. the full panel and ingredients
 *
 * The numeric score is not in that list. It lives inside a collapsed panel, is
 * never the largest element, and never appears above the verdict — because a
 * headline number invites exactly the "good food / bad food" reading the product
 * exists to replace.
 */
export function ProductScreen() {
  const { id = '' } = useParams();
  const signedIn = useIsSignedIn();

  const product = useQuery({
    queryKey: ['product', id],
    queryFn: () => getProduct(id),
    enabled: id !== '',
  });

  // Best-effort, fire-and-forget: a failed VIEWED write must never affect what
  // the visitor sees on the product page itself (FR-012 is additive, not a
  // gate). Not a household-scoped fact, so this fires regardless of whether a
  // household exists — it is simply "this account looked at this product."
  useEffect(() => {
    if (signedIn && product.isSuccess) {
      recordActivity({ productId: product.data.id, eventType: 'VIEWED' }).catch(() => undefined);
    }
  }, [signedIn, product.isSuccess, product.data?.id]);

  if (product.isPending) {
    return (
      <div className="pdp">
        <div className="pdp__hero pdp__hero--loading" />
        <div className="pdp__section">
          <Skeleton lines={4} label={t('loading.product')} />
        </div>
      </div>
    );
  }

  if (product.isError) {
    return (
      <div className="pdp pdp__section">
        <ErrorState error={product.error} onRetry={() => void product.refetch()} />
        <p className="pdp__notfound-help">{t('search.noResultsAction')}</p>
        <Link to="/scan?mode=label" className="btn btn--primary">
          {t('capture.heading')}
        </Link>
      </div>
    );
  }

  const p = product.data;
  const declaredAllergens = p.allergens.filter((a) => a.declarationType === 'CONTAINS');
  const mayContain = p.allergens.filter((a) => a.declarationType === 'MAY_CONTAIN');
  const version = p.currentVersion;

  return (
    <div className="pdp">
      {/* Below 1024px these two wrappers are transparent to layout (the
          existing single-column stack is unchanged). At 1024px and up they
          become the two columns: the image/identity stay in view on the left
          (1) while everything from the allergen flag downward (2-7) scrolls
          on the right, in the same fixed order — see the file-header comment
          for why that order may never change. */}
      <div className="pdp__layout">
        <div className="pdp__col-left">
          <header className="pdp__hero">
            <ProductImage
              imageUrl={p.imageUrl}
              categoryCode={p.categoryCode}
              seed={p.id + p.name}
              alt=""
              classNames={{
                img: 'pdp__art pdp__art--photo',
                art: 'pdp__art pdp__art--fallback',
                glyph: 'pdp__glyph',
              }}
            />
            <div className="pdp__identity">
              <p className="pdp__brand">{p.brandName}</p>
              <h1 className="pdp__name">{p.name}</h1>
              <p className="pdp__meta">
                {p.packSize?.value != null && `${p.packSize.value} ${p.packSize.unit ?? ''}`}
                {p.categoryName && ` · ${p.categoryName}`}
              </p>
            </div>
          </header>
        </div>

        <div className="pdp__col-right">
          {/* 2 — allergen flag, above everything else it could be confused with */}
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
            <h2 id="fit-heading" className="pdp__h2">{t('fit.heading')}</h2>
            {signedIn ? (
              <FitSection productId={p.id} nutrients={p.nutrients} />
            ) : (
              <div className="pdp__signin">
                <p className="pdp__signin-title">{t('fit.signInPrompt')}</p>
                <p className="pdp__signin-sub">{t('fit.signInReason')}</p>
                <Link to="/settings" className="btn btn--primary btn--sm">
                  {t('action.signIn')}
                </Link>
              </div>
            )}
          </section>

          {/* Compare — a natural next step once a shopper has a fit result. Local
              state only: the comparison itself is composed client-side from two
              already-loaded products (no comparison API exists yet). */}
          <section className="pdp__section">
            <Link to={`/compare?a=${encodeURIComponent(p.id)}`} className="btn btn--secondary btn--sm">
              {t('action.compare')}
            </Link>
          </section>

          {/* Product-level health assessment (D-003) — collapsed, below the
              per-person verdict, never the headline of the screen. */}
          <section className="pdp__section">
            <HealthAssessmentSection productId={p.id} nutrients={p.nutrients} />
          </section>

          {/* 6 — provenance, stated before the numbers rather than buried under them */}
          <section className="pdp__section">
            <div className="provenance">
              <div className={`provenance__badge provenance__badge--${quality(version.dataQuality)}`}>
                {t(`dataQuality.${version.dataQuality}` as Parameters<typeof t>[0])}
              </div>
              <p className="provenance__line">
                {t('product.lastVerified', {
                  date: formatDate(version.verifiedAt ?? version.capturedAt),
                })}
              </p>
              {version.versionNo > 1 && (
                <p className="provenance__line">
                  {t('product.reformulated', { date: formatDate(version.effectiveFrom) })}
                </p>
              )}
              {p.hasOpenConflict && (
                <p className="provenance__conflict">{t('product.dataNeedsVerification')}</p>
              )}
              {version.captureMethod === 'OFF_IMPORT' && (
                <p className="provenance__line">
                  <a href="https://world.openfoodfacts.org" target="_blank" rel="noreferrer">
                    {t('product.openFoodFactsAttribution')}
                  </a>
                </p>
              )}
            </div>
          </section>

          {/* 7 — the panel. Both bases side by side, always (D-010). */}
          <section className="pdp__section" aria-labelledby="nutrition-heading">
            <h2 id="nutrition-heading" className="pdp__h2">{t('product.nutrition')}</h2>
            <NutritionSummary
              nutrients={p.nutrients}
              per100Unit={p.packSize?.unit === 'ml' ? 'ml' : 'g'}
              servingDeclaredText={version.servingDeclaredText}
              servingImplausible={version.servingPlausible === false}
            />
          </section>

          <section className="pdp__section" aria-labelledby="ingredients-heading">
            <h2 id="ingredients-heading" className="pdp__h2">{t('product.ingredients')}</h2>
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
          </section>

          <section className="pdp__section">
            <OutcomeCapture productId={p.id} />
          </section>

          <section className="pdp__section">
            <Link to={`/product/${p.id}/report`} className="pdp__report">
              {t('action.reportData')}
            </Link>
            <p className="pdp__disclaimer">{t('medical.notice')}</p>
          </section>
        </div>
      </div>
    </div>
  );
}

/**
 * The signed-in branch of the "for your family" section.
 *
 * Real, deterministic per-person fit (Phase 4): the backend is the sole authority on the
 * verdict, the reasons behind it, and every honest reason one might be missing (no profile
 * yet, consent not granted or withdrawn, not authorized). This never fabricates a verdict —
 * `VerdictChip` already renders `INSUFFICIENT_DATA` as "Not enough data yet" for the (today,
 * universal) case where no scoring rule is active yet.
 */
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

  // The household lookup itself is loading state too, not just members/fit —
  // don't fire either query before the id is known, and don't claim "no
  // household" before that lookup has actually resolved.
  if (myHousehold.isPending) {
    return <Skeleton lines={2} label={t('loading.fit')} />;
  }

  if (myHousehold.isError) {
    return <ErrorState error={myHousehold.error} onRetry={() => void myHousehold.refetch()} />;
  }

  if (householdId === null) {
    return <NoHouseholdMembers />;
  }

  if (members.isPending || fit.isPending) {
    return <Skeleton lines={2} label={t('loading.fit')} />;
  }

  if (members.isError) {
    return <ErrorState error={members.error} onRetry={() => void members.refetch()} />;
  }

  if (fit.isError) {
    return <ErrorState error={fit.error} onRetry={() => void fit.refetch()} />;
  }

  if (members.data.length === 0) {
    return <NoHouseholdMembers />;
  }

  const fitByMemberId = new Map(fit.data.members.map((m) => [m.memberId, m]));

  return (
    <>
      <ul className="pdp__fit-list">
        {members.data.map((member: HouseholdMember) => {
          const memberFit = fitByMemberId.get(member.id);
          if (!memberFit) {
            // Not in the response at all: either another adult (D-002 — their fit is
            // theirs alone to see) or a defensive NOT_AUTHORIZED edge case. Either way,
            // nothing honest to show, so nothing is shown — never a placeholder.
            return null;
          }
          return <MemberFitRow key={member.id} member={member} fit={memberFit} nutrients={nutrients} />;
        })}
      </ul>

      {/* 5 — better options, built from the same per-member fit data above (D-013) */}
      <AlternativesSection householdId={householdId} productId={productId} members={members.data} />
    </>
  );
}

function MemberFitRow({
  member,
  fit,
  nutrients,
}: {
  member: HouseholdMember;
  fit: PersonalFit;
  nutrients: NutrientRow[];
}) {
  const isChild = member.memberType === 'CHILD';

  return (
    <li className="pdp__fit-row">
      {fit.status === 'ASSESSED' && fit.verdict && (
        <>
          <VerdictChip verdict={fit.verdict as Verdict} memberLabel={member.displayLabel} size="sm" />
          {fit.allergenMatches.length > 0 && (
            <p className="pdp__fit-allergen">
              {t('allergen.heading', {
                allergen: fit.allergenMatches
                  .map((code) => t(`allergenCode.${code}` as Parameters<typeof t>[0]))
                  .join(', '),
              })}
            </p>
          )}
          {fit.reasons.length > 0 && (
            <details className="pdp__fit-why">
              <summary>{t('action.seeWhy')}</summary>
              <ReasonList reasons={fit.reasons} nutrients={nutrients} />
            </details>
          )}
        </>
      )}
      {fit.status === 'NO_PROFILE' && (
        <p className="pdp__fit-note">{t('fit.noProfileFor', { name: member.displayLabel })}</p>
      )}
      {(fit.status === 'CONSENT_REQUIRED' || fit.status === 'CONSENT_WITHDRAWN') && (
        <p className="pdp__fit-note">
          <strong>{member.displayLabel}</strong>{' — '}
          {fit.status === 'CONSENT_WITHDRAWN'
            ? t('fit.consentWithdrawn')
            : isChild
              ? t('fit.childConsentRequired')
              : t('fit.consentRequired')}
        </p>
      )}
    </li>
  );
}

function NoHouseholdMembers() {
  return (
    <div className="pdp__nomembers">
      <p className="pdp__pending">{t('fit.noMembers')}</p>
      <Link to="/family" className="btn btn--secondary btn--sm">
        {t('nav.family')}
      </Link>
    </div>
  );
}

function quality(dataQuality: string): string {
  switch (dataQuality) {
    case 'VERIFIED':
      return 'good';
    case 'CONFLICTING':
    case 'NEEDS_REVIEW':
      return 'warn';
    case 'STALE':
      return 'stale';
    default:
      return 'neutral';
  }
}

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(iso));
  } catch {
    return iso.slice(0, 10);
  }
}
