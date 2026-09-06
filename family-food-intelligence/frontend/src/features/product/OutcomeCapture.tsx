import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { recordActivity, type ActivityEventType } from '@/lib/activity';
import { useIsSignedIn } from '@/lib/useSession';
import { ErrorState } from '@/design-system/States';
import './OutcomeCapture.css';

type Outcome = 'BOUGHT_ORIGINAL' | 'BOUGHT_ALTERNATIVE' | 'BOUGHT_NEITHER' | 'SKIPPED';

const OUTCOME_TO_EVENT_TYPE: Record<Outcome, ActivityEventType> = {
  BOUGHT_ORIGINAL: 'BOUGHT',
  BOUGHT_ALTERNATIVE: 'BOUGHT_ALTERNATIVE',
  BOUGHT_NEITHER: 'BOUGHT_NEITHER',
  SKIPPED: 'SKIPPED',
};

const OPTIONS: ReadonlyArray<{ value: Outcome; labelKey: 'outcome.boughtOriginal' | 'outcome.boughtAlternative' | 'outcome.boughtNeither' | 'outcome.skip' }> = [
  { value: 'BOUGHT_ORIGINAL', labelKey: 'outcome.boughtOriginal' },
  { value: 'BOUGHT_ALTERNATIVE', labelKey: 'outcome.boughtAlternative' },
  { value: 'BOUGHT_NEITHER', labelKey: 'outcome.boughtNeither' },
  { value: 'SKIPPED', labelKey: 'outcome.skip' },
];

/**
 * "What did you decide?" (D-005) — the end of the core loop, one optional
 * confirmation, never a log a user has to manage.
 *
 * Now backed by a real `activity` endpoint (FR-012): selecting an option posts
 * `POST /api/v1/activity` and only ever shows `outcome.thanks` once the server
 * has actually confirmed persistence — never optimistically. A signed-out
 * visitor sees the same options but an honest sign-in prompt instead of a
 * network call that would only 401 (this mirrors `ProductScreen`'s own
 * `FitSection` sign-in boundary).
 */
export function OutcomeCapture({ productId }: { productId: string }) {
  const signedIn = useIsSignedIn();
  const [selected, setSelected] = useState<Outcome | null>(null);

  const mutation = useMutation({
    mutationFn: (outcome: Outcome) =>
      recordActivity({ productId, eventType: OUTCOME_TO_EVENT_TYPE[outcome] }),
  });

  function select(outcome: Outcome) {
    setSelected(outcome);
    mutation.mutate(outcome);
  }

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
              disabled={!signedIn}
              onChange={() => select(option.value)}
            />
            {t(option.labelKey)}
          </label>
        ))}
      </div>

      {!signedIn && selected === null && (
        <p className="outcome__note" role="status">{t('outcome.signInPrompt')}</p>
      )}
      {selected && mutation.isPending && (
        <p className="outcome__note" role="status">{t('outcome.saving')}</p>
      )}
      {selected && mutation.isSuccess && (
        <p className="outcome__note" role="status">{t('outcome.thanks')}</p>
      )}
      {selected && mutation.isError && (
        <div className="outcome__error">
          <ErrorState error={mutation.error} onRetry={() => mutation.mutate(selected)} />
        </div>
      )}
    </fieldset>
  );
}
