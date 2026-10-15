import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { searchProducts, type ProductSummary } from '@/lib/catalog';
import { formatAmount, isPlausible } from '@/lib/nutrition';
import { displayProductName, normaliseBrand } from '@/lib/productName';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { getCompare, type CompareItem, type CompareResult } from '@/lib/recommendation';
import { MAX_COMPARE, compareHref, setTray } from '@/lib/compareTray';
import { recordActivity } from '@/lib/activity';
import { ProductImage } from '@/design-system/ProductImage';
import { Card } from '@/design-system/Card';
import { ErrorState, Skeleton } from '@/design-system/States';
import { VerdictChip, type Verdict } from '@/design-system/VerdictChip';
import { Button } from '@/design-system/Button';
import { PageHeader } from '@/design-system/PageHeader';
import { Icon } from '@/design-system/Icon';
import './CompareScreen.css';

const REAL_VERDICTS = new Set(['EVERYDAY', 'OCCASIONAL', 'LIMIT', 'DOESNT_FIT']);

function parseIds(raw: string | null): string[] {
  if (!raw) return [];
  return [...new Set(raw.split(',').map((s) => s.trim()).filter(Boolean))].slice(0, MAX_COMPARE);
}

/**
 * Side-by-side comparison of 2–5 products (D-066). One request (`GET /compare`) returns every
 * column's label facts, the product-level rating, the caller's household fit when signed in,
 * and a deterministic "best overall" — which says "no winner" with the reason whenever the
 * data cannot separate the products. Products are added from any card or product page (the
 * compare tray) or from the search box here; the URL (`?ids=`) is the single source of truth,
 * so a comparison is shareable. Old `?a=&b=` links still open.
 */
export function CompareScreen() {
  useDocumentTitleKey('compare.heading');
  const [params, setParams] = useSearchParams();

  const legacyA = params.get('a');
  const legacyB = params.get('b');
  if (legacyA && !params.get('ids')) {
    return <Navigate to={compareHref([legacyA, ...(legacyB ? [legacyB] : [])])} replace />;
  }

  const ids = parseIds(params.get('ids'));

  function setIds(next: string[]) {
    const unique = [...new Set(next)].slice(0, MAX_COMPARE);
    setParams(unique.length > 0 ? { ids: unique.join(',') } : {}, { replace: true });
  }

  return (
    <div className="comparepage">
      <PageHeader title={t('compare.heading')} lede={t('compare.ledeMany', { max: MAX_COMPARE })} />
      <ComparePanel ids={ids} onChange={setIds} />
    </div>
  );
}

function ComparePanel({ ids, onChange }: { ids: string[]; onChange: (ids: string[]) => void }) {
  const signedIn = useIsSignedIn();
  const household = useMyHousehold({ enabled: signedIn });
  const householdId = signedIn ? household.householdId : null;
  const waitingForHousehold = signedIn && household.isPending;

  const comparison = useQuery({
    queryKey: ['compare', ids.join(','), householdId],
    queryFn: () => getCompare(ids, householdId),
    enabled: ids.length >= 2 && !waitingForHousehold,
  });

  const members = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: householdId !== null,
  });

  // Keep the tray in step with what is on screen, so leaving and coming back finds the same set.
  useEffect(() => {
    if (!comparison.data) return;
    setTray(comparison.data.items.map(({ product }) => ({
      id: product.id,
      brandName: product.brandName,
      name: product.name,
      imageUrl: product.imageUrl,
      categoryCode: product.categoryCode,
    })));
  }, [comparison.data]);

  // Best-effort, fire-and-forget — the first product compared against each of the others.
  useEffect(() => {
    if (!signedIn || !comparison.data) return;
    const [first, ...rest] = comparison.data.items;
    if (!first) return;
    rest.forEach(({ product }) => {
      recordActivity({ productId: first.product.id, alternativeProductId: product.id, eventType: 'COMPARED' }).catch(
        () => undefined,
      );
    });
  }, [signedIn, comparison.data]);

  return (
    <>
      {ids.length < 2 && (
        <p className="comparepage__need">{ids.length === 0 ? t('compare.startEmpty') : t('compare.needOneMore')}</p>
      )}

      {ids.length < MAX_COMPARE && <ProductPicker excludeIds={ids} onPick={(id) => onChange([...ids, id])} />}

      {ids.length >= 2 && comparison.isPending && <Skeleton lines={5} label={t('loading.product')} />}
      {comparison.isError && <ErrorState error={comparison.error} onRetry={() => void comparison.refetch()} />}

      {comparison.isSuccess && (
        <>
          <BestOverallCard result={comparison.data} />
          <ComparisonTable
            result={comparison.data}
            members={members.data ?? []}
            signedIn={signedIn}
            onRemove={(id) => onChange(ids.filter((x) => x !== id))}
          />
        </>
      )}
    </>
  );
}

/* ---------------------------------------------------------------- picker */

/** An inline search that adds the picked product as a new column. */
function ProductPicker({ excludeIds, onPick }: { excludeIds: string[]; onPick: (id: string) => void }) {
  const [q, setQ] = useState('');
  const results = useQuery({
    queryKey: ['search', 'compare', q],
    queryFn: () => searchProducts({ q, size: 8 }),
    enabled: q.trim().length > 1,
  });
  const items = (results.data?.items ?? []).filter((p) => !excludeIds.includes(p.id));

  return (
    <div className="comparepage__picker">
      <label className="field">
        <span className="field__label">{t('compare.addProduct')}</span>
        <input
          type="search"
          placeholder={t('compare.searchPrompt')}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          autoComplete="off"
        />
      </label>

      {results.isError && <ErrorState error={results.error} onRetry={() => void results.refetch()} />}

      {q.trim().length > 1 && (
        <ul className="comparepage__results" role="list">
          {results.isPending && <Skeleton lines={3} label={t('loading.search')} />}
          {results.isSuccess && items.length === 0 && <li className="comparepage__result-empty">{t('search.noResults')}</li>}
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
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  onPick(item.id);
                  setQ('');
                }}
              >
                {t('compare.pick')}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/* ---------------------------------------------------------- best overall */

function BestOverallCard({ result }: { result: CompareResult }) {
  const best = result.bestOverall;
  const winner = result.items.find((i) => i.product.id === best.productId);
  return (
    <Card padding="md" className="comparepage__best" as="section" aria-labelledby="best-heading">
      <h2 id="best-heading" className="comparepage__best-title">
        {winner
          ? t('compare.best.winner', { name: `${normaliseBrand(winner.product.brandName)} ${displayProductName(winner.product).text}`.trim() })
          : t(`compare.best.${best.outcome}` as MessageKey)}
      </h2>
      <ul className="comparepage__best-reasons">
        {best.reasons.map((reason) => (
          <li key={reason}>{t(`compare.bestReason.${reason}` as MessageKey)}</li>
        ))}
      </ul>
    </Card>
  );
}

/* --------------------------------------------------------------- the table */

const NUTRIENT_ROWS: ReadonlyArray<{ code: string; labelKey: MessageKey; better: 'lower' | 'higher' | null }> = [
  { code: 'ENERGY_KCAL', labelKey: 'compare.dimension.energy', better: null },
  { code: 'PROTEIN_G', labelKey: 'compare.dimension.protein', better: 'higher' },
  { code: 'TOTAL_SUGAR_G', labelKey: 'compare.dimension.sugar', better: 'lower' },
  { code: 'ADDED_SUGAR_G', labelKey: 'compare.dimension.addedSugar', better: 'lower' },
  { code: 'SODIUM_MG', labelKey: 'compare.dimension.sodium', better: 'lower' },
  { code: 'SAT_FAT_G', labelKey: 'compare.dimension.satFat', better: 'lower' },
  { code: 'FIBRE_G', labelKey: 'compare.dimension.fibre', better: 'higher' },
];

function amount(item: CompareItem, code: string): { value: number; unit: string; estimated: boolean } | null {
  const row = item.nutrients.find((n) => n.code === code);
  if (!row || !isPlausible(row.per100, row.unit)) return null;
  return { value: row.per100, unit: row.unit, estimated: row.estimated };
}

/**
 * The best value in a nutrient row, marked quietly — only when at least two products declare
 * it and one value is strictly the best. Energy is never marked: neither direction is a goal
 * on its own. "Lower sodium" is a fact about numbers, not a verdict on the product.
 */
function bestIndex(items: CompareItem[], code: string, better: 'lower' | 'higher' | null): number | null {
  if (better === null) return null;
  const values = items.map((i) => amount(i, code)?.value ?? null);
  const declared = values.filter((v): v is number => v !== null);
  if (declared.length < 2) return null;
  const target = better === 'lower' ? Math.min(...declared) : Math.max(...declared);
  const winners = values.filter((v) => v === target).length;
  return winners === 1 ? values.indexOf(target) : null;
}

function ComparisonTable({
  result,
  members,
  signedIn,
  onRemove,
}: {
  result: CompareResult;
  members: HouseholdMember[];
  signedIn: boolean;
  onRemove: (id: string) => void;
}) {
  const items = result.items;
  const categories = new Set(items.map((i) => i.product.categoryCode).filter(Boolean));
  const memberName = useMemo(() => new Map(members.map((m) => [m.id, m.displayLabel])), [members]);

  return (
    <>
      {categories.size > 1 && (
        <p className="comparetable__mismatch" role="note">
          {t('compare.differentCategories')}
        </p>
      )}
      <div className="scroll-x">
        <table className="comparetable" style={{ ['--compare-cols' as string]: items.length }}>
          <caption className="sr-only">{t('compare.heading')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('compare.perColumn')}</th>
              {items.map(({ product }) => (
                <th scope="col" key={product.id} className={result.bestOverall.productId === product.id ? 'comparetable__col--best' : undefined}>
                  <ProductImage
                    imageUrl={product.imageUrl}
                    categoryCode={product.categoryCode}
                    seed={product.id + product.name}
                    alt=""
                    classNames={{ img: 'comparetable__img', art: 'comparetable__img comparetable__img--art', glyph: 'comparetable__glyph' }}
                  />
                  <Link to={`/product/${product.id}`} className="comparetable__col-link">
                    <span className="comparetable__col-brand">{normaliseBrand(product.brandName)}</span>
                    <span className="comparetable__col-name">{displayProductName(product).text}</span>
                  </Link>
                  <button type="button" className="comparetable__remove" onClick={() => onRemove(product.id)}>
                    <Icon name="close" size={14} />
                    <span>{t('compare.remove')}</span>
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row">{t('compare.dimension.health')}</th>
              {items.map((item) => (
                <td key={item.product.id}>
                  {item.rating.status === 'SCORED' && item.rating.stars != null ? (
                    <span className="nutri__value">{t('score.stars', { stars: formatAmount(item.rating.stars) })}</span>
                  ) : (
                    <span className="nutri__missing">{t('compare.notRated')}</span>
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row">{t('compare.dimension.familyFit')}</th>
              {items.map((item) => (
                <td key={item.product.id}>
                  {!signedIn ? (
                    <Link to="/you" className="comparetable__signin">{t('action.signIn')}</Link>
                  ) : (
                    <FitCell item={item} memberName={memberName} />
                  )}
                </td>
              ))}
            </tr>
            {NUTRIENT_ROWS.map((row) => {
              const best = bestIndex(items, row.code, row.better);
              return (
                <tr key={row.code}>
                  <th scope="row">{t(row.labelKey)}</th>
                  {items.map((item, index) => {
                    const value = amount(item, row.code);
                    return (
                      <td key={item.product.id} className={best === index ? 'comparetable__cell--better' : undefined}>
                        {value === null ? (
                          <span className="nutri__missing">{t('product.notDeclaredShort')}</span>
                        ) : (
                          <span className="nutri__value">
                            {formatAmount(value.value)} <span className="nutri__unit">{value.unit}</span>
                          </span>
                        )}
                        {best === index && (
                          <span className="comparetable__marker">
                            {row.better === 'lower' ? t('compare.lowestValue') : t('compare.highestValue')}
                          </span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
            <tr>
              <th scope="row">{t('compare.dimension.processing')}</th>
              {items.map((item) => (
                <td key={item.product.id}>
                  {item.novaGroup != null ? (
                    <span>{t('compare.nova', { group: item.novaGroup })}</span>
                  ) : (
                    <span className="nutri__missing">{t('product.notDeclaredShort')}</span>
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row">{t('compare.dimension.diet')}</th>
              {items.map((item) => (
                <td key={item.product.id}>
                  {item.dietFlag !== 'UNKNOWN' ? (
                    <span>
                      {t(`product.diet.${item.dietFlag}` as MessageKey)}
                      {item.dietFlagEstimated && <span className="comparetable__est"> ({t('product.estimated')})</span>}
                    </span>
                  ) : (
                    <span className="nutri__missing">{t('product.notDeclaredShort')}</span>
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row">{t('compare.dimension.allergens')}</th>
              {items.map((item) => {
                const declared = item.allergens.filter((a) => a.declarationType !== 'NOT_DECLARED');
                return (
                  <td key={item.product.id}>
                    {declared.length === 0 ? (
                      <span className="nutri__missing">{t('compare.noAllergens')}</span>
                    ) : (
                      <span>
                        {declared
                          .map((a) =>
                            a.declarationType === 'MAY_CONTAIN'
                              ? t('compare.mayContain', { allergen: t(`allergenCode.${a.code}` as MessageKey) })
                              : t(`allergenCode.${a.code}` as MessageKey),
                          )
                          .join(', ')}
                      </span>
                    )}
                  </td>
                );
              })}
            </tr>
            <tr>
              <th scope="row">{t('compare.dimension.ingredients')}</th>
              {items.map((item) => (
                <td key={item.product.id}>
                  {item.ingredients.length === 0 ? (
                    <span className="nutri__missing">{t('product.notDeclared')}</span>
                  ) : (
                    <span className="comparetable__ingredients">{item.ingredients.join(', ')}</span>
                  )}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
      <p className="comparetable__note">{t('compare.basisNote')}</p>
    </>
  );
}

/** The caller's own and guarded children's verdict chips (D-002) — never another adult's. */
function FitCell({ item, memberName }: { item: CompareItem; memberName: Map<string, string> }) {
  if (item.members.length === 0) return <span className="nutri__missing">{t('fit.noMembers')}</span>;
  const chips = item.members.filter((m) => m.status === 'ASSESSED' && m.verdict && REAL_VERDICTS.has(m.verdict));
  const flagged = item.members.filter((m) => m.allergenMatches.length > 0);
  return (
    <span className="comparetable__chips">
      {flagged.map((m) => (
        <span key={`a-${m.memberId}`} className="comparetable__allergen">
          <Icon name="alert" size={14} />
          {t('compare.allergenFor', { name: memberName.get(m.memberId) ?? '' })}
        </span>
      ))}
      {chips.map((m) => (
        <VerdictChip key={m.memberId} verdict={m.verdict as Verdict} memberLabel={memberName.get(m.memberId) ?? ''} size="sm" />
      ))}
      {chips.length === 0 && flagged.length === 0 && <span className="nutri__missing">{t('compare.noVerdictYet')}</span>}
    </span>
  );
}
