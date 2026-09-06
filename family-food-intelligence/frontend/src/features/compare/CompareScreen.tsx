import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { formatAmount, getProduct, searchProducts, type ProductDetail, type ProductSummary } from '@/lib/catalog';
import { getHouseholdFit, getProductHealth, type HouseholdFit } from '@/lib/scoring';
import { recordActivity } from '@/lib/activity';
import { categoryGlyph, categoryGradient } from '@/design-system/CategoryArt';
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
          <div className="comparepage__slot comparepage__slot--empty">
            <span className="comparepage__slot-label">{t('compare.addProduct')}</span>
          </div>
        ) : productB.isPending ? (
          <div className="comparepage__slot">
            <Skeleton lines={3} label={t('loading.product')} />
          </div>
        ) : productB.isError ? (
          <div className="comparepage__slot">
            <ErrorState error={productB.error} onRetry={() => void productB.refetch()} />
          </div>
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
    <div className="comparepage__slot">
      <div className="comparepage__card">
        <span
          className="comparepage__art"
          style={{ backgroundImage: categoryGradient(product.categoryCode, product.id + product.name) }}
          aria-hidden="true"
        >
          <span className="comparepage__glyph">{categoryGlyph(product.categoryCode)}</span>
        </span>
        <span className="comparepage__identity">
          <span className="comparepage__brand">{product.brandName}</span>
          <span className="comparepage__name">{product.name}</span>
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
    </div>
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
              <span
                className="comparepage__result-art"
                style={{ backgroundImage: categoryGradient(item.categoryCode, item.id + item.name) }}
                aria-hidden="true"
              >
                {categoryGlyph(item.categoryCode)}
              </span>
              <span className="comparepage__result-text">
                <span className="comparepage__result-brand">{item.brandName}</span>
                <span className="comparepage__result-name">{item.name}</span>
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
  return (
    <div className="scroll-x">
      <table className="comparetable">
        <caption className="sr-only">{t('compare.heading')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('compare.heading')}</th>
            <th scope="col">{a.name}</th>
            <th scope="col">{b.name}</th>
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
            <tr key={row.code}>
              <th scope="row">{t(row.labelKey)}</th>
              <td>{nutrientCell(a, row.code)}</td>
              <td>{nutrientCell(b, row.code)}</td>
            </tr>
          ))}
          <tr>
            <th scope="row">{t('compare.dimension.allergens')}</th>
            <td>{allergenCell(a)}</td>
            <td>{allergenCell(b)}</td>
          </tr>
          <tr>
            <th scope="row">{t('compare.dimension.ingredients')}</th>
            <td className="comparetable__ingredients">{ingredientsCell(a)}</td>
            <td className="comparetable__ingredients">{ingredientsCell(b)}</td>
          </tr>
        </tbody>
      </table>
      <p className="comparetable__note">{t('compare.basisNote')}</p>
    </div>
  );
}

const NUTRIENT_ROWS: ReadonlyArray<{ code: string; labelKey: 'compare.dimension.energy' | 'compare.dimension.protein' | 'compare.dimension.sugar' | 'compare.dimension.addedSugar' | 'compare.dimension.sodium' | 'compare.dimension.satFat' | 'compare.dimension.fibre' }> = [
  { code: 'ENERGY_KCAL', labelKey: 'compare.dimension.energy' },
  { code: 'PROTEIN_G', labelKey: 'compare.dimension.protein' },
  { code: 'TOTAL_SUGAR_G', labelKey: 'compare.dimension.sugar' },
  { code: 'ADDED_SUGAR_G', labelKey: 'compare.dimension.addedSugar' },
  { code: 'SODIUM_MG', labelKey: 'compare.dimension.sodium' },
  { code: 'SAT_FAT_G', labelKey: 'compare.dimension.satFat' },
  { code: 'FIBRE_G', labelKey: 'compare.dimension.fibre' },
];

function nutrientCell(product: ProductDetail, code: string) {
  const row = product.nutrients.find((n) => n.nutrientCode === code);
  if (!row || row.amountPer100 == null) {
    return <span className="nutri__missing">{t('product.notDeclared')}</span>;
  }
  return (
    <span className="nutri__value">
      {formatAmount(row.amountPer100)}
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

function ingredientsCell(product: ProductDetail) {
  if (product.ingredients.length === 0) {
    return <span className="nutri__missing">{t('product.notDeclared')}</span>;
  }
  return product.ingredients
    .slice()
    .sort((x, y) => x.position - y.position)
    .map((i) => i.rawText)
    .join(', ');
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

  if (myHousehold.isPending || members.isPending || fit.isPending) {
    return <span className="nutri__missing">{t('loading.fit')}</span>;
  }

  if (myHousehold.isError || members.isError || fit.isError || householdId === null) {
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
