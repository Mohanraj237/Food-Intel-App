import { useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery, type Capabilities } from '@/lib/me';
import { listMembers } from '@/lib/household';
import { listMetricDefinitions } from '@/lib/wellness';
import { createGoal, type GoalDirection, type GoalPeriod } from '@/lib/goals';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import { GoalProgressList } from './GoalProgressList';
import { offeredGoalMetrics, DIRECTION_KEYS, PERIOD_KEYS, type GoalMetricOption } from './goalOptions';
import { useTrackedSubject } from './useTrackedSubject';
import './Goals.css';

/**
 * Goals for one person: you, or (via `/family/:memberId/goals`) a child you look after.
 *
 * <p>Split out of the combined Goals/Habits screen (plan §18 1.3) — this file is goals only now;
 * `features/habits/HabitsScreen.tsx` is the habits twin. Which subject this page is about comes
 * from the URL, not an in-page picker: a link to another person's goals only ever exists where
 * the linking screen (the Family hub, a member view) already knows the caller may see them, so
 * there is nothing left for this screen to offer a switcher for (plan §7).
 */
export function GoalsScreen() {
  useDocumentTitleKey('goal.screenHeading');
  const signedIn = useIsSignedIn();
  const { memberId: routeMemberId } = useParams<{ memberId?: string }>();
  const subject = useTrackedSubject(routeMemberId);
  const me = useMeQuery({ enabled: signedIn && routeMemberId === undefined });
  const householdId = me.data?.household?.id ?? null;

  const roster = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: routeMemberId === undefined && householdId !== null,
  });
  const children = routeMemberId === undefined ? (roster.data ?? []).filter((m) => m.memberType === 'CHILD') : [];

  if (!signedIn) {
    return (
      <div className="page goalspage">
        <PageHeader title={t('goal.screenHeading')} />
        <p className="goalspage__lede">{t('goal.signInPrompt')}</p>
        <Link to="/you" className="btn btn--primary btn--md">{t('action.signIn')}</Link>
      </div>
    );
  }

  if (subject.status === 'loading') {
    return (
      <div className="page goalspage">
        <PageHeader title={t('goal.screenHeading')} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (subject.status === 'error') {
    return (
      <div className="page goalspage">
        <PageHeader title={t('goal.screenHeading')} />
        <ErrorState error={subject.error} onRetry={subject.retry} />
      </div>
    );
  }

  if (subject.status === 'not-shared') {
    return (
      <div className="page goalspage">
        <PageHeader title={t('goal.screenHeading')} />
        <p className="goalspage__note">
          {subject.displayLabel
            ? t('goal.notSharedNamed' as MessageKey, { name: subject.displayLabel })
            : t('goal.notShared' as MessageKey)}
        </p>
        <Link to="/family" className="btn btn--secondary btn--md">{t('nav.family')}</Link>
      </div>
    );
  }

  return (
    <div className="page goalspage">
      <PageHeader
        title={t('goal.screenHeading')}
        lede={subject.isSelf ? t('goal.lede') : t('goal.ledeForMember' as MessageKey, { name: subject.displayLabel })}
        backTo={subject.isSelf ? '/today' : `/family/${subject.memberId}`}
        backLabel={subject.isSelf ? t('nav.today') : subject.displayLabel}
      />

      {subject.isChild && <p className="goalspage__note">{t('goal.childNote')}</p>}

      <GoalsBlock key={subject.memberId} memberId={subject.memberId} capabilities={subject.capabilities} />

      {subject.isSelf && children.length > 0 && (
        <nav className="goalspage__also" aria-label={t('goal.alsoSetForLabel' as MessageKey)}>
          <p className="goalspage__alsoLede">{t('goal.alsoSetForLabel' as MessageKey)}</p>
          <ul className="goalspage__alsoList" role="list">
            {children.map((c) => (
              <li key={c.id}>
                <Link to={`/family/${c.id}/goals`} className="chip">{c.displayLabel}</Link>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </div>
  );
}

function GoalsBlock({ memberId, capabilities }: { memberId: string; capabilities: Capabilities }) {
  const [adding, setAdding] = useState(false);
  const [justAdded, setJustAdded] = useState(false);

  return (
    <>
      {justAdded && <p className="goalspage__success" role="status">{t('goal.saved')}</p>}
      <GoalProgressList memberId={memberId} />
      {adding ? (
        <GoalForm
          memberId={memberId}
          capabilities={capabilities}
          onCancel={() => setAdding(false)}
          onSaved={() => { setAdding(false); setJustAdded(true); }}
        />
      ) : (
        <Button variant="secondary" size="sm" onClick={() => { setAdding(true); setJustAdded(false); }}>
          {t('goal.add')}
        </Button>
      )}
    </>
  );
}

function GoalForm({
  memberId,
  capabilities,
  onCancel,
  onSaved,
}: {
  memberId: string;
  capabilities: Capabilities;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();
  const definitions = useQuery({
    queryKey: ['wellness', 'metrics'],
    queryFn: listMetricDefinitions,
    staleTime: 60 * 60 * 1000,
  });

  // The server's `/experience` answer for this subject, intersected with the metric catalogue —
  // never a client-side child/adult guess (plan §18 1.3, closes G-04).
  const options = useMemo(
    () => offeredGoalMetrics(definitions.data ?? [], capabilities),
    [definitions.data, capabilities],
  );

  const [metricCode, setMetricCode] = useState<string>('');
  const [direction, setDirection] = useState<GoalDirection | ''>('');
  const [period, setPeriod] = useState<GoalPeriod | ''>('');
  const [target, setTarget] = useState('');
  const [problem, setProblem] = useState<string | null>(null);

  const option = options.find((o) => o.code === metricCode) ?? options[0];
  const effectiveDirection = option && direction && option.directions.includes(direction) ? direction : option?.directions[0];
  const effectivePeriod = option && period && option.periods.includes(period) ? period : option?.periods[0];
  const unit = option?.unit ?? '';

  const save = useMutation({
    mutationFn: () =>
      createGoal({
        memberId,
        metricCode: (option as GoalMetricOption).code,
        direction: effectiveDirection as GoalDirection,
        targetValue: Number(target),
        period: effectivePeriod as GoalPeriod,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
      onSaved();
    },
  });

  if (definitions.isPending) return <Skeleton lines={3} label={t('loading.default')} />;
  if (definitions.isError) {
    return <ErrorState error={definitions.error} onRetry={() => void definitions.refetch()} />;
  }
  if (!option) return <p className="goalspage__note">{t('goal.noneAvailable')}</p>;

  function submit(event: FormEvent) {
    event.preventDefault();
    const value = Number(target);
    if (target.trim() === '' || !Number.isFinite(value) || value <= 0) {
      setProblem(t('goal.validationTarget'));
      return;
    }
    setProblem(null);
    save.mutate();
  }

  const error = problem ?? (save.error instanceof ApiError ? save.error.message : null);

  return (
    <form className="goalform" onSubmit={submit} noValidate aria-label={t('goal.add')}>
      <label className="field">
        <span className="field__label">{t('goal.metricLabel')}</span>
        <select value={option.code} onChange={(e) => setMetricCode(e.target.value)}>
          {options.map((o) => (
            <option key={o.code} value={o.code}>{t(o.labelKey)}</option>
          ))}
        </select>
      </label>
      <div className="goalform__row">
        <label className="field">
          <span className="field__label">{t('goal.directionLabel')}</span>
          <select value={effectiveDirection} onChange={(e) => setDirection(e.target.value as GoalDirection)}>
            {option.directions.map((d) => (
              <option key={d} value={d}>{t(DIRECTION_KEYS[d])}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">{t('goal.targetLabel', { unit })}</span>
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="any"
            value={target}
            onChange={(e) => setTarget(e.target.value)}
          />
        </label>
        {option.periods.length > 1 && (
          <label className="field">
            <span className="field__label">{t('goal.periodLabel')}</span>
            <select value={effectivePeriod} onChange={(e) => setPeriod(e.target.value as GoalPeriod)}>
              {option.periods.map((p) => (
                <option key={p} value={p}>{t(PERIOD_KEYS[p])}</option>
              ))}
            </select>
          </label>
        )}
      </div>
      <p className="goalform__hint">{t('goal.formHint')}</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="goalform__actions">
        <Button type="submit" size="sm" loading={save.isPending}>{t('goal.save')}</Button>
        <Button variant="ghost" size="sm" onClick={onCancel}>{t('action.cancel')}</Button>
      </div>
    </form>
  );
}
