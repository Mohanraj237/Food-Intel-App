import { useState } from 'react';
import { t } from '@/lib/i18n';
import './OutcomeCapture.css';

type Outcome = 'BOUGHT_ORIGINAL' | 'BOUGHT_ALTERNATIVE' | 'BOUGHT_NEITHER' | 'SKIPPED';

const OPTIONS: ReadonlyArray<{ value: Outcome; labelKey: 'outcome.boughtOriginal' | 'outcome.boughtAlternative' | 'outcome.boughtNeither' | 'outcome.skip' }> = [
  { value: 'BOUGHT_ORIGINAL', labelKey: 'outcome.boughtOriginal' },
  { value: 'BOUGHT_ALTERNATIVE', labelKey: 'outcome.boughtAlternative' },
  { value: 'BOUGHT_NEITHER', labelKey: 'outcome.boughtNeither' },
  { value: 'SKIPPED', labelKey: 'outcome.skip' },
];

/**
 * "What did you decide?" (D-005) — deliberately the end of the core loop, not
 * a tracker: one optional confirmation, never a log.
 *
 * There is no `activity` backend yet (`docs/product/requirements-traceability.md`
 * FR-012, `NOT_STARTED`) — nothing here calls an API, and the confirmation
 * copy says plainly that the choice is not saved. This is a real, isolated UI
 * for a future integration point, not a mock dressed up as a working feature:
 * once an outcome-capture endpoint exists, only the `onSelect` body changes.
 */
export function OutcomeCapture() {
  const [selected, setSelected] = useState<Outcome | null>(null);

  return (
    <fieldset className="outcome">
      <legend className="outcome__legend">{t('outcome.heading')}</legend>
      <div className="outcome__options" role="radiogroup" aria-label={t('outcome.heading')}>
        {OPTIONS.map((option) => (
          <label
            key={option.value}
            className={
              selected === option.value ? 'outcome__option outcome__option--selected' : 'outcome__option'
            }
          >
            <input
              type="radio"
              name="outcome"
              value={option.value}
              checked={selected === option.value}
              onChange={() => setSelected(option.value)}
            />
            {t(option.labelKey)}
          </label>
        ))}
      </div>
      {selected && (
        <p className="outcome__note" role="status">
          {t('outcome.notPersisted')}
        </p>
      )}
    </fieldset>
  );
}
