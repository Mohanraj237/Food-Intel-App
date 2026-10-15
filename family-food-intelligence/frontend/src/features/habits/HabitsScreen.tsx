import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { listMembers } from '@/lib/household';
import { createHabit, type HabitCadence } from '@/lib/goals';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import { HabitList } from '@/features/goals/HabitList';
import { useTrackedSubject } from '@/features/goals/useTrackedSubject';
import { CADENCES, CADENCE_KEYS } from '@/features/goals/goalOptions';
import './Habits.css';

/**
 * `/habits` and, via an optional `:memberId` route param, `/family/:memberId/habits` (plan §18
 * 1.3) — split out of the combined Goals/Habits screen, `GoalsScreen.tsx` is its goals-only twin.
 *
 * Same rule as Goals: which person this page is about comes from the URL, not an in-page
 * picker — a link to another person's habits only exists where the linking screen already
 * established the caller may see them (plan §7).
 */
// New key, staged in `locales/_pending.c4.json` pending the integration merge (see that file's
// header) — not yet in `en-IN.json`, so `MessageKey` needs the cast once, here, rather than at
// every one of this heading's several call sites below.
const SCREEN_HEADING = 'habit.screenHeading' as MessageKey;

export function HabitsScreen() {
  useDocumentTitleKey(SCREEN_HEADING);
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
      <div className="page habitspage">
        <PageHeader title={t(SCREEN_HEADING)} />
        <p className="habitspage__lede">{t('habit.signInPrompt' as MessageKey)}</p>
        <Link to="/you" className="btn btn--primary btn--md">{t('action.signIn')}</Link>
      </div>
    );
  }

  if (subject.status === 'loading') {
    return (
      <div className="page habitspage">
        <PageHeader title={t(SCREEN_HEADING)} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (subject.status === 'error') {
    return (
      <div className="page habitspage">
        <PageHeader title={t(SCREEN_HEADING)} />
        <ErrorState error={subject.error} onRetry={subject.retry} />
      </div>
    );
  }

  if (subject.status === 'not-shared') {
    return (
      <div className="page habitspage">
        <PageHeader title={t(SCREEN_HEADING)} />
        <p className="habitspage__note">
          {subject.displayLabel
            ? t('habit.notSharedNamed' as MessageKey, { name: subject.displayLabel })
            : t('habit.notShared' as MessageKey)}
        </p>
        <Link to="/family" className="btn btn--secondary btn--md">{t('nav.family')}</Link>
      </div>
    );
  }

  // HABITS is a real access scope (unlike goals, which ride the metric-category scopes) — a
  // caregiver's adult may share it without sharing every metric category, so this is checked
  // directly rather than inferred from anything else.
  const habitsVisible = subject.isSelf || subject.visibleScopes.includes('HABITS');

  return (
    <div className="page habitspage">
      <PageHeader
        title={t(SCREEN_HEADING)}
        lede={subject.isSelf ? t('habit.lede' as MessageKey) : t('habit.ledeForMember' as MessageKey, { name: subject.displayLabel })}
        backTo={subject.isSelf ? '/today' : `/family/${subject.memberId}`}
        backLabel={subject.isSelf ? t('nav.today') : subject.displayLabel}
      />

      {subject.isChild && <p className="habitspage__note">{t('habit.childNote' as MessageKey)}</p>}

      {!habitsVisible ? (
        <p className="habitspage__note">
          {t('habit.notSharedNamed' as MessageKey, { name: subject.displayLabel })}
        </p>
      ) : (
        <HabitsBlock key={subject.memberId} memberId={subject.memberId} timeZone={subject.timeZone} />
      )}

      {subject.isSelf && children.length > 0 && (
        <nav className="habitspage__also" aria-label={t('habit.alsoSetForLabel' as MessageKey)}>
          <p className="habitspage__alsoLede">{t('habit.alsoSetForLabel' as MessageKey)}</p>
          <ul className="habitspage__alsoList" role="list">
            {children.map((c) => (
              <li key={c.id}>
                <Link to={`/family/${c.id}/habits`} className="chip">{c.displayLabel}</Link>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </div>
  );
}

function HabitsBlock({ memberId, timeZone }: { memberId: string; timeZone: string }) {
  const [adding, setAdding] = useState(false);
  const [justAdded, setJustAdded] = useState(false);

  return (
    <>
      {justAdded && <p className="habitspage__success" role="status">{t('habit.saved')}</p>}
      <HabitList memberId={memberId} timeZone={timeZone} />
      {adding ? (
        <HabitForm
          memberId={memberId}
          onCancel={() => setAdding(false)}
          onSaved={() => { setAdding(false); setJustAdded(true); }}
        />
      ) : (
        <Button variant="secondary" size="sm" onClick={() => { setAdding(true); setJustAdded(false); }}>
          {t('habit.add')}
        </Button>
      )}
    </>
  );
}

function HabitForm({
  memberId,
  onCancel,
  onSaved,
}: {
  memberId: string;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [cadence, setCadence] = useState<HabitCadence>('DAILY');
  const [problem, setProblem] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => createHabit({ memberId, name: name.trim(), cadence }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['habits'] });
      onSaved();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === '') {
      setProblem(t('habit.validationName'));
      return;
    }
    setProblem(null);
    save.mutate();
  }

  const error = problem ?? (save.error instanceof ApiError ? save.error.message : null);

  return (
    <form className="habitform" onSubmit={submit} noValidate aria-label={t('habit.add')}>
      <label className="field">
        <span className="field__label">{t('habit.nameLabel')}</span>
        <input
          type="text"
          maxLength={80}
          placeholder={t('habit.namePlaceholder')}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <label className="field">
        <span className="field__label">{t('habit.cadenceLabel')}</span>
        <select value={cadence} onChange={(e) => setCadence(e.target.value as HabitCadence)}>
          {CADENCES.map((c) => (
            <option key={c} value={c}>{t(CADENCE_KEYS[c])}</option>
          ))}
        </select>
      </label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="habitform__actions">
        <Button type="submit" size="sm" loading={save.isPending}>{t('habit.save')}</Button>
        <Button variant="ghost" size="sm" onClick={onCancel}>{t('action.cancel')}</Button>
      </div>
    </form>
  );
}
