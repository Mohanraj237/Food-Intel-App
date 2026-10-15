import { useEffect, useState } from 'react';
import { t, type MessageKey } from '@/lib/i18n';
import { NUMERIC_FILTER_KEYS, type NumericFilterKey, type SearchFilters } from '@/lib/catalog';
import { ALLERGEN_CODES, FILTER_UNITS } from '@/lib/searchParams';
import { BottomSheet } from '@/design-system/BottomSheet';
import { Button } from '@/design-system/Button';
import { Chip } from '@/design-system/Rails';
import './FilterSheet.css';

function without(filters: SearchFilters, key: keyof SearchFilters): SearchFilters {
  const next = { ...filters };
  delete next[key];
  return next;
}

const NUMERIC_LABEL: Record<NumericFilterKey, MessageKey> = {
  maxTotalSugar: 'search.filter.maxTotalSugar',
  maxAddedSugar: 'search.filter.maxAddedSugar',
  maxSodium: 'search.filter.maxSodium',
  maxSatFat: 'search.filter.maxSatFat',
  maxEnergy: 'search.filter.maxEnergy',
  minProtein: 'search.filter.minProtein',
  minFibre: 'search.filter.minFibre',
};

/**
 * Nutrient limits, allergens to hide, a dietary choice and a processing level (D-061). Every
 * number is the person's own: the fields start empty and nothing is pre-filled with a value
 * this product chose. Changes apply only on "Show results", so the grid does not jump while
 * someone is still deciding.
 */
export function FilterSheet({
  open,
  onClose,
  value,
  onApply,
}: {
  open: boolean;
  onClose: () => void;
  value: SearchFilters;
  onApply: (next: SearchFilters) => void;
}) {
  const [draft, setDraft] = useState<SearchFilters>(value);

  useEffect(() => {
    if (open) setDraft(value);
  }, [open, value]);

  function setNumber(key: NumericFilterKey, raw: string) {
    const next = { ...draft };
    const parsed = Number(raw);
    if (raw.trim() === '' || !Number.isFinite(parsed) || parsed < 0) delete next[key];
    else next[key] = parsed;
    setDraft(next);
  }

  function toggleAllergen(code: string) {
    const current = new Set(draft.excludeAllergens ?? []);
    if (current.has(code)) current.delete(code);
    else current.add(code);
    setDraft({ ...draft, excludeAllergens: [...current] });
  }

  return (
    <BottomSheet open={open} onClose={onClose} title={t('search.filtersTitle')}>
      <div className="filtersheet">
        <fieldset className="filtersheet__group">
          <legend className="filtersheet__legend">{t('search.filter.nutrients')}</legend>
          <p className="filtersheet__hint">{t('search.filter.nutrientsHint')}</p>
          <div className="filtersheet__numbers">
            {NUMERIC_FILTER_KEYS.map((key) => (
              <label key={key} className="filtersheet__number">
                <span className="filtersheet__number-label">{t(NUMERIC_LABEL[key])}</span>
                <span className="filtersheet__number-field">
                  <input
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="any"
                    value={draft[key] ?? ''}
                    onChange={(e) => setNumber(key, e.target.value)}
                    aria-describedby={`unit-${key}`}
                  />
                  <span id={`unit-${key}`} className="filtersheet__unit">
                    {FILTER_UNITS[key]}
                  </span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="filtersheet__group">
          <legend className="filtersheet__legend">{t('search.filter.allergens')}</legend>
          <p className="filtersheet__hint">{t('search.filter.allergensHint')}</p>
          <div className="filtersheet__chips">
            {ALLERGEN_CODES.map((code) => (
              <Chip key={code} active={draft.excludeAllergens?.includes(code) ?? false} onClick={() => toggleAllergen(code)}>
                {t(`allergenCode.${code}` as MessageKey)}
              </Chip>
            ))}
          </div>
        </fieldset>

        <fieldset className="filtersheet__group">
          <legend className="filtersheet__legend">{t('search.filter.diet')}</legend>
          <div className="filtersheet__chips">
            <Chip active={!draft.dietary} onClick={() => setDraft(without(draft, 'dietary'))}>
              {t('search.filter.any')}
            </Chip>
            <Chip active={draft.dietary === 'VEG'} onClick={() => setDraft({ ...draft, dietary: 'VEG' })}>
              {t('search.chip.diet.VEG')}
            </Chip>
            <Chip active={draft.dietary === 'VEGAN'} onClick={() => setDraft({ ...draft, dietary: 'VEGAN' })}>
              {t('search.chip.diet.VEGAN')}
            </Chip>
          </div>
          <p className="filtersheet__hint">{t('search.filter.dietHint')}</p>
        </fieldset>

        <fieldset className="filtersheet__group">
          <legend className="filtersheet__legend">{t('search.filter.processing')}</legend>
          <div className="filtersheet__chips">
            <Chip active={draft.maxNova == null} onClick={() => setDraft(without(draft, 'maxNova'))}>
              {t('search.filter.any')}
            </Chip>
            {[3, 2, 1].map((n) => (
              <Chip key={n} active={draft.maxNova === n} onClick={() => setDraft({ ...draft, maxNova: n })}>
                {t('search.chip.maxNova', { value: n })}
              </Chip>
            ))}
          </div>
          <p className="filtersheet__hint">{t('search.filter.processingHint')}</p>
        </fieldset>

        <div className="filtersheet__actions">
          <Button
            variant="ghost"
            onClick={() => setDraft(draft.category ? { category: draft.category } : {})}
          >
            {t('search.filtersClear')}
          </Button>
          <Button
            variant="primary"
            onClick={() => {
              onApply(draft);
              onClose();
            }}
          >
            {t('search.filtersApply')}
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}
