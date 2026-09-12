import { useState } from 'react';
import { t } from '@/lib/i18n';
import { type NutrientRow } from '@/lib/catalog';
import { formatAmount, isPlausible } from '@/lib/nutrition';
import './NutritionSummary.css';

const CORE_CODES = [
  'ENERGY_KCAL',
  'ADDED_SUGAR_G',
  'TOTAL_SUGAR_G',
  'SODIUM_MG',
  'SAT_FAT_G',
  'PROTEIN_G',
  'FIBRE_G',
];

interface NutritionSummaryProps {
  nutrients: NutrientRow[];
  /** Determines whether the per-100 column reads "per 100 g" or "per 100 ml". */
  per100Unit?: 'g' | 'ml';
  servingDeclaredText?: string | null;
  servingImplausible?: boolean;
  /** Hides the "show everything" toggle — used by Compare, where every row is
   *  already the same small core set for both products. */
  allowExpand?: boolean;
}

/**
 * The nutrition panel: per-serving and per-100 side by side, always (D-010).
 * Previously a hand-duplicated `<table className="nutri">` in ProductScreen,
 * CompareScreen and OpsReviewScreen — one definition, same markup everywhere
 * a nutrition panel needs to appear.
 */
export function NutritionSummary({
  nutrients,
  per100Unit = 'g',
  servingDeclaredText,
  servingImplausible = false,
  allowExpand = true,
}: NutritionSummaryProps) {
  const [showAll, setShowAll] = useState(!allowExpand);

  if (nutrients.length === 0) {
    return <p className="nutri__pending">{t('error.product_data_insufficient')}</p>;
  }

  const shown = showAll ? nutrients : nutrients.filter((n) => CORE_CODES.includes(n.nutrientCode));

  // A column of eight "Not declared on the label" cells says one thing eight
  // times and takes a third of the table's width to do it. When no row in the
  // panel has a per-serving figure at all, drop the column and say it once.
  const hasAnyPerServing = nutrients.some((n) => isPlausible(n.amountPerServing, n.unit));

  return (
    <>
      {servingDeclaredText && (
        <p className="nutri__serving">{t('product.servingDeclared', { serving: servingDeclaredText })}</p>
      )}
      {servingImplausible && <p className="nutri__serving-warn">{t('product.servingImplausible')}</p>}

      <div className="scroll-x">
        <table className="nutri">
          <thead>
            <tr>
              <th scope="col">{t('product.nutrition')}</th>
              {hasAnyPerServing && <th scope="col">{t('product.perServing')}</th>}
              <th scope="col">{per100Unit === 'ml' ? t('product.per100ml') : t('product.per100g')}</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((row) => (
              <NutrientLine key={row.nutrientCode} row={row} showPerServing={hasAnyPerServing} />
            ))}
          </tbody>
        </table>
      </div>

      {!hasAnyPerServing && (
        <p className="nutri__serving">{t('product.noPerServingDeclared')}</p>
      )}

      {/* Two-way. Expanding used to remove the control, so a reader who opened
          the full panel had no way to fold it back — a one-way door in a
          component whose whole purpose is progressive disclosure. */}
      {allowExpand && (nutrients.length > shown.length || showAll) && (
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          aria-expanded={showAll}
          onClick={() => setShowAll((open) => !open)}
        >
          {showAll ? t('product.showFewerNutrients') : t('product.showAllNutrients')}
        </button>
      )}
    </>
  );
}

/** A nutrient row. An absent value renders as "not declared", never as a
 *  zero — the distinction between "this product contains no added sugar" and
 *  "the label does not say" is exactly the kind of thing this must not blur. */
function NutrientLine({ row, showPerServing }: { row: NutrientRow; showPerServing: boolean }) {
  return (
    <tr>
      <th scope="row">
        {row.displayName}
        {row.isEstimated && <span className="nutri__est">{t('product.estimated')}</span>}
      </th>
      {showPerServing && <td>{cell(row.amountPerServing, row.unit)}</td>}
      <td>{cell(row.amountPer100, row.unit)}</td>
    </tr>
  );
}

/**
 * A nutrient cell.
 *
 * Three outcomes, not two. An absent value renders as "not declared", never as
 * a zero — the distinction between "this product contains no added sugar" and
 * "the label does not say" is exactly the kind of thing this must not blur.
 * And a value that cannot be true (a sodium figure of 1,000,000 mg per 100 g
 * claims a kilogram of sodium inside 100 g of food) is withheld rather than
 * printed: a reader cannot tell a typo from a reading, and one absurd figure
 * discredits every honest number beside it. See `lib/nutrition.ts`.
 */
function cell(value: number | null, unit: string) {
  if (value == null) {
    return <span className="nutri__missing">{t('product.notDeclared')}</span>;
  }
  if (!isPlausible(value, unit)) {
    return (
      <span className="nutri__implausible" title={t('product.valueImplausible')}>
        {t('product.valueImplausibleShort')}
      </span>
    );
  }
  return (
    <span className="nutri__value">
      {formatAmount(value)}{' '}
      <span className="nutri__unit">{unit}</span>
    </span>
  );
}
