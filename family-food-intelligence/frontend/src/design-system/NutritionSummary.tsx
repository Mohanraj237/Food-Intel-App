import { useState } from 'react';
import { t } from '@/lib/i18n';
import { formatAmount, type NutrientRow } from '@/lib/catalog';
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
              <th scope="col">{t('product.perServing')}</th>
              <th scope="col">{per100Unit === 'ml' ? t('product.per100ml') : t('product.per100g')}</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((row) => (
              <NutrientLine key={row.nutrientCode} row={row} />
            ))}
          </tbody>
        </table>
      </div>

      {allowExpand && nutrients.length > shown.length && (
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setShowAll(true)}>
          {t('product.showAllNutrients')}
        </button>
      )}
    </>
  );
}

/** A nutrient row. An absent value renders as "not declared", never as a
 *  zero — the distinction between "this product contains no added sugar" and
 *  "the label does not say" is exactly the kind of thing this must not blur. */
function NutrientLine({ row }: { row: NutrientRow }) {
  return (
    <tr>
      <th scope="row">
        {row.displayName}
        {row.isEstimated && <span className="nutri__est">{t('product.estimated')}</span>}
      </th>
      <td>{cell(row.amountPerServing, row.unit)}</td>
      <td>{cell(row.amountPer100, row.unit)}</td>
    </tr>
  );
}

function cell(value: number | null, unit: string) {
  if (value == null) {
    return <span className="nutri__missing">{t('product.notDeclared')}</span>;
  }
  return (
    <span className="nutri__value">
      {formatAmount(value)}
      <span className="nutri__unit">{unit}</span>
    </span>
  );
}
