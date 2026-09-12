import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { getProduct, searchProducts, type ProductDetail, type ProductSummary } from '@/lib/catalog';
import { formatAmount, isPlausible } from '@/lib/nutrition';
import { displayProductName, normaliseBrand } from '@/lib/productName';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { getHouseholdFit, getProductHealth, type HouseholdFit } from '@/lib/scoring';
import { recordActivity } from '@/lib/activity';
import { ProductImage } from '@/design-system/ProductImage';
import { Card } from '@/design-system/Card';
import { ErrorState, Skeleton } from '@/design-system/States';
import { VerdictChip, type Verdict } from '@/design-system/VerdictChip';
import { Button } from '@/design-system/Button';
import './CompareScreen.css';

/**
 * Side-by-side product comparison.
 *
 * No comparison API exists on the backend (`docs/product/requirements-traceability.md`
 * FR-007/PX-2, `recommendation` module `NOT_STARTED`). This screen is deliberately
 * built as a client-side composition of two already-loaded, real products — each
 * row reuses an endpoint that already exists (`getProduct`, `getProductHealth`,
 * `getHouseholdFit`) rather than inventing a comparison response. A row is shown
 * only when the underlying fact is real; nothing here is computed by guessing.
 */
export function CompareScreen() {
  useDocumentTitleKey('compare.heading');
  const [params, setParams] = useSearchParams();
  const aId = params.get('a');
  const bId = params.get('b');

  function setB(id: string) {
    const next = new URLSearchParams(params);
    next.set('b', id);
    setParams(next, { replace: true });
  }

  function removeB() {
    const next = new URLSearchParams(params);
    next.delete('b');
    setParams(next, { replace: true });
  }

  function swap() {
    if (!aId || !bId) return;
    const next = new URLSearchParams(params);
    next.set('a', bId);
    next.set('b', aId);
    setParams(next, { replace: true });
  }

  if (!aId) {
    return (
      <div className="page">
        <h1>{t('compare.heading')}</h1>
        <p className="field__hint">{t('compare.startFromProduct')}</p>
        <Link to="/search" className="btn btn--primary btn--md">
          {t('nav.search')}
        </Link>
      </div>
    );
  }

  return (
    <div className="comparepage">
      <h1 className="comparepage__heading">{t('compare.heading')}</h1>
      <ComparePanel aId={aId} bId={bId} onPickB={setB} onRemoveB={removeB} onSwap={swap} />
    </div>
  );
}

function ComparePanel({
  aId,
  bId,
  onPickB,
  onRemoveB,
  onSwap,
}: {
  aId: string;
  bId: string | null;
  onPickB: (id: string) => void;
  onRemoveB: () => void;
  onSwap: () => void;
}) {
  const signedIn = useIsSignedIn();
  const productA = useQuery({ queryKey: ['product', aId], queryFn: () => getProduct(aId) });
  const productB = useQuery({
    queryKey: ['product', bId],
    queryFn: () => getProduct(bId as string),
    enabled: bId !== null,
  });

  // Best-effort, fire-and-forget — same posture as ProductScreen's VIEWED write.
  useEffect(() => {
    if (signedIn && productA.isSuccess && productB.isSuccess) {
      recordActivity({
        productId: productA.data.id,
        alternativeProductId: productB.data.id,
        eventType: 'COMPARED',
      }).catch(() => undefined);
    }
  }, [signedIn, productA.isSuccess, productA.data?.id, productB.isSuccess, productB.data?.id]);

  if (productA.isPending) {
    return <Skeleton lines={4} label={t('loading.product')} />;
  }
  if (productA.isError) {
    return <ErrorState error={productA.error} onRetry={() => void productA.refetch()} />;
  }

  return (
    <>
      <div className="comparepage__cards">
        <ProductHeaderCard product={productA.data} />
        <span className="comparepage__vs" aria-hidden="true">
          {t('compare.heading')}
        </span>
        {bId === null ? (
          <Card padding="sm" className="comparepage__slot comparepage__slot--empty">
            <span className="comparepage__slot-plus" aria-hidden="true">+</span>
            <span className="comparepage__slot-label">{t('compare.addProduct')}</span>
          </Card>
        ) : productB.isPending ? (
          <Card padding="sm" className="comparepage__slot">
            <Skeleton lines={3} label={t('loading.product')} />
          </Card>
        ) : productB.isError ? (
          <Card padding="sm" className="comparepage__slot">
            <ErrorState error={productB.error} onRetry={() => void productB.refetch()} />
          </Card>
        ) : (
          <ProductHeaderCard product={productB.data} onRemove={onRemoveB} />
        )}
      </div>

      {bId !== null && productB.isSuccess && (
        <div className="comparepage__actions">
          <Button variant="ghost" size="sm" onClick={onSwap}>
            {t('compare.swap')}
          </Button>
        </div>
      )}

      {bId === null && <ProductPicker excludeId={aId} onPick={onPickB} />}

      {bId !== null && productB.isSuccess && (
        <ComparisonTable a={productA.data} b={productB.data} />
      )}
    </>
  );
}

function ProductHeaderCard({
  product,
  onRemove,
}: {
  product: ProductDetail;
  onRemove?: () => void;
}) {
  return (
    <Card padding="sm" className="comparepage__slot">
      <div className="comparepage__card">
        <ProductImage
          imageUrl={product.imageUrl}
          categoryCode={product.categoryCode}
          seed={product.id + product.name}
          alt=""
          classNames={{
            img: 'comparepage__art comparepage__art--photo',
            art: 'comparepage__art comparepage__art--fallback',
            glyph: 'comparepage__glyph',
          }}
        />
        <span className="comparepage__identity">
          <span className="comparepage__brand">{normaliseBrand(product.brandName)}</span>
          <span className="comparepage__name">{displayProductName(product).text}</span>
        </span>
      </div>
      <div className="comparepage__slot-actions">
        <Link to={`/product/${product.id}`} className="comparepage__view">
          {t('compare.viewProduct')}
        </Link>
        {onRemove && (
          <Button variant="ghost" size="sm" onClick={onRemove}>
            {t('compare.remove')}
          </Button>
        )}
      </div>
    </Card>
  );
}

/** An inline search, scoped to this screen — picking a second product to
 *  compare is local state, not a new route (matches the app's existing
 *  preference for local state over route proliferation). */
function ProductPicker({ excludeId, onPick }: { excludeId: string; onPick: (id: string) => void }) {
  const [q, setQ] = useState('');
  const results = useQuery({
    queryKey: ['search', 'compare', q],
    queryFn: () => searchProducts({ q, size: 8 }),
    enabled: q.trim().length > 0,
  });

  const items = (results.data?.items ?? []).filter((p) => p.id !== excludeId);

  return (
    <div className="comparepage__picker">
      <label className="field">
        <span className="field__label sr-only">{t('compare.searchPrompt')}</span>
        <input
          type="search"
          placeholder={t('compare.searchPrompt')}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          autoComplete="off"
        />
      </label>

      {results.isError && <ErrorState error={results.error} onRetry={() => void results.refetch()} />}

      {q.trim() !== '' && (
        <ul className="comparepage__results" role="list">
          {results.isPending && <Skeleton lines={3} label={t('loading.search')} />}
          {results.isSuccess && items.length === 0 && (
            <li className="comparepage__result-empty">{t('search.noResults')}</li>
          )}
          {items.map((item: ProductSummary) => (
            <li key={item.id} className="comparepage__result">
              <ProductImage
                imageUrl={item.imageUrl}
                categoryCode={item.categoryCode}
                seed={item.id + item.name}
                alt=""
                classNames={{
                  img: 'comparepage__result-art comparepage__result-art--photo',
                  art: 'comparepage__result-art comparepage__result-art--fallback',
                  glyph: 'comparepage__result-glyph',
                }}
              />
              <span className="comparepage__result-text">
                <span className="comparepage__result-brand">{normaliseBrand(item.brandName)}</span>
                <span className="comparepage__result-name">{displayProductName(item).text}</span>
                {item.packSize?.value != null && item.packSize.unit && (
                  <span className="comparepage__result-size">
                    {formatAmount(item.packSize.value)} {item.packSize.unit}
                  </span>
                )}
              </span>
              <Button variant="secondary" size="sm" onClick={() => onPick(item.id)}>
                {t('compare.pick')}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/* --------------------------------------------------------------- the table */

function ComparisonTable({ a, b }: { a: ProductDetail; b: ProductDetail }) {
  // Comparing instant noodles against dark chocolate per 100 g is arithmetically
  // valid and practically meaningless. We still allow it — a shopper may have a
  // real reason — but we say so, rather than letting "compared per 100 g so the
  // products line up" imply the two are interchangeable.
  const differentCategories =
    a.categoryCode !== null && b.categoryCode !== null && a.categoryCode !== b.categoryCode;

  return (
    <>
      {differentCategories && (
        <p className="comparetable__mismatch" role="note">
          {t('compare.differentCategories')}
        </p>
      )}
      <div className="scroll-x">
        <table className="comparetable">
          <caption className="sr-only">{t('compare.heading')}</caption>
          <thead>
            <tr>
              {/* This column holds the row labels, not a comparison. It was
                  headed "Compare", which made the word mean three different
                  things on one screen. */}
              <th scope="col">{t('compare.perColumn')}</th>
              <th scope="col">
                <span className="comparetable__col-brand">{normaliseBrand(a.brandName)}</span>
                <span className="comparetable__col-name">{displayProductName(a).text}</span>
              </th>
              <th scope="col">
                <span className="comparetable__col-brand">{normaliseBrand(b.brandName)}</span>
                <span className="comparetable__col-name">{displayProductName(b).text}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row">{t('compare.dimension.health')}</th>
              <td><HealthCell productId={a.id} /></td>
              <td><HealthCell productId={b.id} /></td>
            </tr>
            <tr>
              <th scope="row">{t('compare.dimension.familyFit')}</th>
              <td><FamilyFitCell product={a} /></td>
              <td><FamilyFitCell product={b} /></td>
            </tr>
            {NUTRIENT_ROWS.map((row) => (
              <NutrientComparisonRow key={row.code} a={a} b={b} row={row} />
            ))}
            <tr>
              <th scope="row">{t('compare.dimension.allergens')}</th>
              <td>{allergenCell(a)}</td>
              <td>{allergenCell(b)}</td>
            </tr>
            <tr>
              <th scope="row">{t('compare.dimension.ingredients')}</th>
              <td>{ingredientsCell(a)}</td>
              <td>{ingredientsCell(b)}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p className="comparetable__note">{t('compare.basisNote')}</p>
    </>
  );
}

/**
 * One nutrient row, with the more favourable of the two values marked.
 *
 * A comparison that styles 45.1 g and 2.9 g of sugar identically leaves the
 * reader to do the arithmetic the screen exists to do for them. The marking is
 * deliberately restrained — a quiet label and a weight change, not a red/green
 * verdict — because "lower sodium" is a fact about two numbers, not a judgement
 * about which product someone should buy.
 *
 * Direction matters: less is better for sugar, sodium and saturated fat; more
 * is better for protein and fibre; energy is left unmarked because neither
 * direction is a goal on its own.
 */
function NutrientComparisonRow({
  a,
  b,
  row,
}: {
  a: ProductDetail;
  b: ProductDetail;
  row: (typeof NUTRIENT_ROWS)[number];
}) {
  const left = comparableAmount(a, row.code);
  const right = comparableAmount(b, row.code);

  let better: 'a' | 'b' | null = null;
  if (row.better !== null && left !== null && right !== null && left !== right) {
    const leftWins = row.better === 'lower' ? left < right : left > right;
    better = leftWins ? 'a' : 'b';
  }

  const marker = row.better === 'lower' ? t('compare.betterValue') : t('compare.betterValueHigher');

  return (
    <tr>
      <th scope="row">{t(row.labelKey)}</th>
      <td className={better === 'a' ? 'comparetable__cell--better' : undefined}>
        {nutrientCell(a, row.code)}
        {better === 'a' && <span className="comparetable__marker">{marker}</span>}
      </td>
      <td className={better === 'b' ? 'comparetable__cell--better' : undefined}>
        {nutrientCell(b, row.code)}
        {better === 'b' && <span className="comparetable__marker">{marker}</span>}
      </td>
    </tr>
  );
}

/** The per-100 amount, or `null` when it is missing or cannot be true. */
function comparableAmount(product: ProductDetail, code: string): number | null {
  const row = product.nutrients.find((n) => n.nutrientCode === code);
  if (!row || !isPlausible(row.amountPer100, row.unit)) return null;
  return row.amountPer100;
}

const NUTRIENT_ROWS: ReadonlyArray<{
  code: string;
  labelKey:
    | 'compare.dimension.energy'
    | 'compare.dimension.protein'
    | 'compare.dimension.sugar'
    | 'compare.dimension.addedSugar'
    | 'compare.dimension.sodium'
    | 'compare.dimension.satFat'
    | 'compare.dimension.fibre';
  /** Which direction is more favourable, or `null` where neither is a goal. */
  better: 'lower' | 'higher' | null;
}> = [
  { code: 'ENERGY_KCAL', labelKey: 'compare.dimension.energy', better: null },
  { code: 'PROTEIN_G', labelKey: 'compare.dimension.protein', better: 'higher' },
  { code: 'TOTAL_SUGAR_G', labelKey: 'compare.dimension.sugar', better: 'lower' },
  { code: 'ADDED_SUGAR_G', labelKey: 'compare.dimension.addedSugar', better: 'lower' },
  { code: 'SODIUM_MG', labelKey: 'compare.dimension.sodium', better: 'lower' },
  { code: 'SAT_FAT_G', labelKey: 'compare.dimension.satFat', better: 'lower' },
  { code: 'FIBRE_G', labelKey: 'compare.dimension.fibre', better: 'higher' },
];

function nutrientCell(product: ProductDetail, code: string) {
  const row = product.nutrients.find((n) => n.nutrientCode === code);
  if (!row || row.amountPer100 == null) {
    return <span className="nutri__missing">{t('product.notDeclared')}</span>;
  }
  // Same rule as the product panel: a figure that cannot be true is withheld
  // rather than compared. See lib/nutrition.ts.
  if (!isPlausible(row.amountPer100, row.unit)) {
    return (
      <span className="nutri__implausible" title={t('product.valueImplausible')}>
        {t('product.valueImplausibleShort')}
      </span>
    );
  }
  return (
    <span className="nutri__value">
      {formatAmount(row.amountPer100)}{' '}
      <span className="nutri__unit">{row.unit}</span>
    </span>
  );
}

function allergenCell(product: ProductDetail) {
  const declared = product.allergens.filter((a) => a.declarationType === 'CONTAINS');
  if (declared.length === 0) {
    return <span className="nutri__missing">{t('compare.noAllergens')}</span>;
  }
  return <span>{declared.map((a) => a.displayName).join(', ')}</span>;
}

/**
 * The clamp lives on an inner element, never on the `<td>`.
 *
 * `.comparetable__ingredients` set `display: -webkit-box` directly on the cell,
 * which takes it out of table layout altogether — the sibling cell then fell
 * below this one instead of sitting beside it, breaking row alignment on the
 * one row where two products differ most.
 */
function ingredientsCell(product: ProductDetail) {
  if (product.ingredients.length === 0) {
    return <span className="nutri__missing">{t('product.notDeclared')}</span>;
  }
  const text = product.ingredients
    .slice()
    .sort((x, y) => x.position - y.position)
    .map((i) => i.rawText)
    .join(', ');
  return <span className="comparetable__ingredients">{text}</span>;
}

function HealthCell({ productId }: { productId: string }) {
  const health = useQuery({ queryKey: ['product-health', productId], queryFn: () => getProductHealth(productId) });

  if (health.isPending) return <span className="nutri__missing">{t('loading.default')}</span>;
  if (health.isError) return <span className="nutri__missing">{t('error.title')}</span>;
  if (health.data.status === 'SCORED' && health.data.score != null) {
    return <span className="nutri__value">{t('score.value', { score: health.data.score })}</span>;
  }
  return <span className="nutri__missing">{t('score.notYetScored')}</span>;
}

/**
 * Caller-scoped, same as `ProductScreen`'s `FitSection` (D-002/D-024 pt.4): only
 * the members the signed-in caller is authorized to see. A comparison cell has
 * no room for the full per-person explanation `ProductScreen` gives — it shows
 * the verdict chips only, with the product page one tap away for the reasons.
 */
function FamilyFitCell({ product }: { product: ProductDetail }) {
  const signedIn = useIsSignedIn();
  const myHousehold = useMyHousehold({ enabled: signedIn });
  const householdId = myHousehold.householdId;

  const members = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: householdId !== null,
  });

  const fit = useQuery({
    queryKey: ['household-fit', householdId, product.id],
    queryFn: () => getHouseholdFit(householdId as string, product.id),
    enabled: householdId !== null,
  });

  if (!signedIn) {
    return (
      <Link to="/settings" className="comparetable__signin">
        {t('action.signIn')}
      </Link>
    );
  }

  // Order matters, and getting it wrong is what made this cell spin forever.
  //
  // `members` and `fit` are disabled until a household id exists, and a disabled
  // React Query stays `isPending` indefinitely — it is not loading, it has
  // simply never been asked to run. Testing `isPending` first therefore showed
  // "Working out results…" permanently to every signed-in user without a
  // household, and made the `householdId === null` branch below unreachable.
  // Resolve the household first, then distinguish a real in-flight fetch
  // (`isLoading`) from a query that is merely idle.
  if (myHousehold.isError) {
    return <span className="nutri__missing">{t('fit.noMembers')}</span>;
  }

  if (myHousehold.isPending) {
    return <span className="nutri__missing">{t('loading.fit')}</span>;
  }

  if (householdId === null) {
    return <span className="nutri__missing">{t('fit.noMembers')}</span>;
  }

  if (members.isError || fit.isError) {
    // A failure is not the same as "no members" — say so, and let the reader
    // retry from the product page rather than staring at a spinner.
    return <span className="nutri__missing">{t('compare.fitUnavailable')}</span>;
  }

  if (members.isLoading || fit.isLoading) {
    return <span className="nutri__missing">{t('loading.fit')}</span>;
  }

  if (!members.isSuccess || !fit.isSuccess) {
    return <span className="nutri__missing">{t('fit.noMembers')}</span>;
  }

  return <FamilyFitChips members={members.data} fit={fit.data} />;
}

function FamilyFitChips({ members, fit }: { members: HouseholdMember[]; fit: HouseholdFit }) {
  const fitByMemberId = new Map(fit.members.map((m) => [m.memberId, m]));
  const rows = members
    .map((member) => ({ member, memberFit: fitByMemberId.get(member.id) }))
    .filter((row): row is { member: HouseholdMember; memberFit: NonNullable<typeof row.memberFit> } =>
      row.memberFit !== undefined && row.memberFit.status === 'ASSESSED' && row.memberFit.verdict !== null,
    );

  if (rows.length === 0) {
    return <span className="nutri__missing">{t('fit.noMembers')}</span>;
  }

  return (
    <span className="comparetable__chips">
      {rows.map(({ member, memberFit }) => (
        <VerdictChip
          key={member.id}
          verdict={memberFit.verdict as Verdict}
          memberLabel={member.displayLabel}
          size="sm"
        />
      ))}
    </span>
  );
}
