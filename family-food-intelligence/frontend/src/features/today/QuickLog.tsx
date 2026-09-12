import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { listMetricDefinitions, recordObservation, type MetricDefinition } from '@/lib/wellness';
import { Button } from '@/design-system/Button';
import './QuickLog.css';

/**
 * Logging one measurement, in as few taps as the data model allows.
 *
 * <p>The metric list comes from the server, including which metrics are adult-only, so this
 * form never hardcodes the age rules (D-036). Hardcoding them would drift from the domain
 * the moment a metric changed class, and the drift would be silent in the one direction that
 * matters — offering a child a field the server will refuse.
 *
 * The server refuses a disallowed write regardless of what this form shows. Filtering here
 * is about not asking a guardian for something we will then reject, not about enforcement.
 */
const QUICK_METRICS: Array<{ code: string; labelKey: MessageKey }> = [
  { code: 'WATER_ML', labelKey: 'quicklog.water' },
  { code: 'STEPS', labelKey: 'quicklog.steps' },
  { code: 'SLEEP_MINUTES', labelKey: 'quicklog.sleep' },
  { code: 'WEIGHT_KG', labelKey: 'quicklog.weight' },
];

export function QuickLog({ memberId, isChild }: { memberId: string; isChild: boolean }) {
  const queryClient = useQueryClient();
  const [metricCode, setMetricCode] = useState<string>('WATER_ML');
  const [value, setValue] = useState('');
  const [saved, setSaved] = useState<string | null>(null);

  const definitions = useQuery({
    queryKey: ['wellness', 'metrics'],
    queryFn: listMetricDefinitions,
    staleTime: 60 * 60 * 1000,
  });

  const byCode = useMemo(() => {
    const map = new Map<string, MetricDefinition>();
    for (const d of definitions.data ?? []) map.set(d.code, d);
    return map;
  }, [definitions.data]);

  /** Only what this subject may actually record. */
  const available = QUICK_METRICS.filter((m) => {
    const def = byCode.get(m.code);
    if (!def) return false;
    return !(isChild && def.adultOnly);
  });

  const selected = byCode.get(metricCode);

  const mutation = useMutation({
    mutationFn: () => recordObservation(memberId, { metricCode, value: Number(value) }),
    onSuccess: (observation) => {
      setValue('');
      setSaved(observation.metricCode);
      // Today's totals and any insight derived from them are now stale.
      void queryClient.invalidateQueries({ queryKey: ['wellness', 'observations', memberId] });
      void queryClient.invalidateQueries({ queryKey: ['insights', memberId] });
      window.setTimeout(() => setSaved(null), 4000);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric < 0) return;
    mutation.mutate();
  }

  if (definitions.isPending) {
    return <p className="quicklog__loading">{t('loading.default')}</p>;
  }

  // A child whose only offerable metrics are adult-only gets an explanation rather than an
  // empty form with a disabled button.
  if (available.length === 0) {
    return <p className="quicklog__empty">{t('quicklog.noneAvailable')}</p>;
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  return (
    <form className="quicklog" onSubmit={submit}>
      <div className="quicklog__row">
        <label className="field quicklog__metric">
          <span className="field__label">{t('quicklog.what')}</span>
          <select
            value={metricCode}
            onChange={(e) => {
              setMetricCode(e.target.value);
              setSaved(null);
            }}
          >
            {available.map((m) => (
              <option key={m.code} value={m.code}>
                {t(m.labelKey)}
              </option>
            ))}
          </select>
        </label>

        <label className="field quicklog__value">
          <span className="field__label">
            {t('quicklog.howMuch', { unit: selected?.unit ?? '' })}
          </span>
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="any"
            required
            value={value}
            onChange={(e) => setValue(e.target.value)}
          />
        </label>

        <Button type="submit" loading={mutation.isPending} disabled={value.trim() === ''}>
          {t('quicklog.save')}
        </Button>
      </div>

      {errorMessage && (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      )}
      {saved && (
        <p className="quicklog__saved" role="status">
          {t('quicklog.saved')}
        </p>
      )}
    </form>
  );
}
