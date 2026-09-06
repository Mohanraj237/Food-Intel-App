import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { getCategories } from '@/lib/catalog';
import { submitContribution } from '@/lib/capture';
import { ALLERGEN_CODES, type AllergenCode } from '@/lib/profile';
import { Button } from '@/design-system/Button';
import './ContributeProductForm.css';

/**
 * The seven "core" nutrient rows this form captures, per 100 g/ml and
 * (optionally) per serving. This is the exact same fixed set `ProductScreen`
 * calls `coreCodes` — kept identical so a contribution and a published
 * product show the same headline facts.
 *
 * These labels are technical nutrient-code labels, not narrative copy — the
 * same kind of thing a nutrition label itself prints. There is no public
 * "list nutrients" endpoint to source them from, and adding seven more locale
 * keys for a fixed, non-narrative vocabulary this form alone uses did not
 * seem worth it, so they are kept here as plain strings rather than `t()`
 * keys. (If that judgment call is wrong, moving them into the locale bundle
 * is a small change confined to this file.)
 */
const CORE_NUTRIENTS: ReadonlyArray<{ code: string; label: string; unit: string }> = [
  { code: 'ENERGY_KCAL', label: 'Energy', unit: 'kcal' },
  { code: 'PROTEIN_G', label: 'Protein', unit: 'g' },
  { code: 'TOTAL_SUGAR_G', label: 'Total sugar', unit: 'g' },
  { code: 'ADDED_SUGAR_G', label: 'Added sugar', unit: 'g' },
  { code: 'SAT_FAT_G', label: 'Saturated fat', unit: 'g' },
  { code: 'FIBRE_G', label: 'Fibre', unit: 'g' },
  { code: 'SODIUM_MG', label: 'Sodium', unit: 'mg' },
];

type AmountsByCode = Record<string, string>;

function emptyAmounts(): AmountsByCode {
  return Object.fromEntries(CORE_NUTRIENTS.map((n) => [n.code, '']));
}

function toNumberOrNull(raw: string): number | null {
  const trimmed = raw.trim();
  if (trimmed === '') return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
}

interface ContributeProductFormProps {
  /** The barcode that led here — read-only, always present for this flow. */
  gtin: string;
  /** Called when the user leaves this form, whether by cancelling or after a
   *  successful submission's "back to scan" link. Lets `ScanScreen` collapse
   *  back to its normal scanning panel. */
  onExit: () => void;
}

/**
 * "Add this product yourself" — a hand-entry form for a barcode the catalogue
 * does not yet have, submitted to a review queue. Never auto-published: a
 * human checks it before it appears in search (see `docs/data/product-data-policy.md`).
 */
export function ContributeProductForm({ gtin, onExit }: ContributeProductFormProps) {
  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 30 * 60 * 1000,
  });

  const [productName, setProductName] = useState('');
  const [brandName, setBrandName] = useState('');
  const [categoryCode, setCategoryCode] = useState('');
  const [packSizeValue, setPackSizeValue] = useState('');
  const [packSizeUnit, setPackSizeUnit] = useState('');
  const [servingDeclaredText, setServingDeclaredText] = useState('');
  const [per100, setPer100] = useState<AmountsByCode>(emptyAmounts);
  const [perServing, setPerServing] = useState<AmountsByCode>(emptyAmounts);
  const [ingredientsRawText, setIngredientsRawText] = useState('');
  const [allergens, setAllergens] = useState<Set<AllergenCode>>(new Set());

  const mutation = useMutation({
    mutationFn: submitContribution,
  });

  function toggleAllergen(code: AllergenCode) {
    setAllergens((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!productName.trim()) return;

    mutation.mutate({
      gtin: gtin.trim() || null,
      productName: productName.trim(),
      brandName: brandName.trim() || null,
      categoryCode: categoryCode || null,
      packSizeValue: toNumberOrNull(packSizeValue),
      packSizeUnit: packSizeUnit || null,
      servingDeclaredText: servingDeclaredText.trim() || null,
      nutrients: CORE_NUTRIENTS.map((n) => ({
        nutrientCode: n.code,
        amountPer100: toNumberOrNull(per100[n.code] ?? ''),
        amountPerServing: toNumberOrNull(perServing[n.code] ?? ''),
      })),
      ingredientsRawText: ingredientsRawText.trim() || null,
      allergenCodes: Array.from(allergens),
    });
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  if (mutation.isSuccess) {
    return (
      <section className="contribute contribute--success" aria-labelledby="contribute-success-title">
        <h1 id="contribute-success-title">{t('contribute.successHeading')}</h1>
        <p>{t('contribute.successBody')}</p>
        <Link to="/scan" className="btn btn--primary btn--md" onClick={onExit}>
          {t('contribute.backToScan')}
        </Link>
      </section>
    );
  }

  return (
    <section className="contribute" aria-labelledby="contribute-title">
      <Button variant="ghost" size="sm" onClick={onExit}>
        {t('action.back')}
      </Button>

      <h1 id="contribute-title">{t('contribute.heading')}</h1>
      <p className="field__hint">{t('contribute.explainer')}</p>

      <form className="contribute__form" onSubmit={submit}>
        <label className="field">
          <span className="field__label">{t('contribute.gtinLabel')}</span>
          <input readOnly value={gtin} />
        </label>

        <label className="field">
          <span className="field__label">{t('contribute.nameLabel')}</span>
          <input
            required
            maxLength={200}
            value={productName}
            onChange={(e) => setProductName(e.target.value)}
          />
        </label>

        <label className="field">
          <span className="field__label">
            {t('contribute.brandLabel')} <span className="field__optional">{t('contribute.brandOptional')}</span>
          </span>
          <input maxLength={120} value={brandName} onChange={(e) => setBrandName(e.target.value)} />
        </label>

        <label className="field">
          <span className="field__label">
            {t('contribute.categoryLabel')}{' '}
            <span className="field__optional">{t('contribute.categoryOptional')}</span>
          </span>
          <select
            value={categoryCode}
            disabled={categoriesQuery.isPending}
            onChange={(e) => setCategoryCode(e.target.value)}
          >
            <option value="">{t('profile.notSpecified')}</option>
            {(categoriesQuery.data ?? []).map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <div className="contribute__row">
          <label className="field">
            <span className="field__label">{t('contribute.packSizeLabel')}</span>
            <input
              type="number"
              inputMode="decimal"
              min="0"
              step="any"
              value={packSizeValue}
              onChange={(e) => setPackSizeValue(e.target.value)}
            />
          </label>
          <label className="field">
            <span className="field__label">{t('contribute.packSizeUnitLabel')}</span>
            <select value={packSizeUnit} onChange={(e) => setPackSizeUnit(e.target.value)}>
              <option value="">{t('profile.notSpecified')}</option>
              <option value="g">g</option>
              <option value="ml">ml</option>
              <option value="kg">kg</option>
              <option value="l">l</option>
            </select>
          </label>
        </div>

        <label className="field">
          <span className="field__label">
            {t('contribute.servingDeclaredLabel')}{' '}
            <span className="field__optional">{t('contribute.servingDeclaredOptional')}</span>
          </span>
          <input
            maxLength={80}
            value={servingDeclaredText}
            onChange={(e) => setServingDeclaredText(e.target.value)}
          />
        </label>

        <fieldset className="field">
          <legend className="field__label">{t('contribute.nutrientsHeading')}</legend>
          <div className="contribute__nutrient-grid">
            {CORE_NUTRIENTS.map((n) => (
              <label key={n.code} className="contribute__nutrient-row">
                <span className="contribute__nutrient-label">{n.label}</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="any"
                  value={per100[n.code]}
                  onChange={(e) => setPer100((prev) => ({ ...prev, [n.code]: e.target.value }))}
                />
                <span className="contribute__nutrient-unit">{n.unit}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="field">
          <legend className="field__label">
            {t('contribute.nutrientsServingHeading')}{' '}
            <span className="field__optional">{t('contribute.nutrientsServingOptional')}</span>
          </legend>
          <div className="contribute__nutrient-grid">
            {CORE_NUTRIENTS.map((n) => (
              <label key={n.code} className="contribute__nutrient-row">
                <span className="contribute__nutrient-label">{n.label}</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="any"
                  value={perServing[n.code]}
                  onChange={(e) => setPerServing((prev) => ({ ...prev, [n.code]: e.target.value }))}
                />
                <span className="contribute__nutrient-unit">{n.unit}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="field">
          <span className="field__label">
            {t('contribute.ingredientsLabel')}{' '}
            <span className="field__optional">{t('contribute.ingredientsOptional')}</span>
          </span>
          <textarea
            rows={4}
            value={ingredientsRawText}
            onChange={(e) => setIngredientsRawText(e.target.value)}
          />
        </label>

        <fieldset className="field">
          <legend className="field__label">{t('contribute.allergensLabel')}</legend>
          <div className="contribute__checks">
            {ALLERGEN_CODES.map((code) => (
              <label key={code} className="contribute__check">
                <input
                  type="checkbox"
                  checked={allergens.has(code)}
                  onChange={() => toggleAllergen(code)}
                />
                {t(`allergenCode.${code}`)}
              </label>
            ))}
          </div>
        </fieldset>

        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}

        <Button type="submit" fullWidth loading={mutation.isPending}>
          {t('contribute.submit')}
        </Button>
      </form>
    </section>
  );
}
