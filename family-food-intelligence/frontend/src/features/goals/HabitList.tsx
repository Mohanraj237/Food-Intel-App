import { useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import {
  archiveHabit,
  completeHabit,
  habitConsistency,
  habitHistory,
  listHabits,
  reactivateHabit,
  uncompleteHabit,
  updateHabit,
  type Habit,
  type HabitCadence,
} from '@/lib/goals';
import { Button } from '@/design-system/Button';
import { ConfirmSheet } from '@/design-system/BottomSheet';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { dayOfMonth, isoDaysBefore, lastDaysInZone, longDayLabel, narrowDow, todayInZone } from './calendarDay';
import { CADENCES, CADENCE_KEYS } from './goalOptions';
import '../habits/Habits.css';

/** Five 7-day weeks — enough to read as "the last month" without pretending to be a real
 *  calendar month (which would need day-of-week alignment this simpler grid doesn't attempt). */
const MONTH_DAYS = 35;
const STRIP_DAYS = 7;

/**
 * Habits, and whether they happened.
 *
 * <p>What this deliberately does not show: a streak, a "days in a row" count, a flame, a
 * badge, or anything that treats an unticked day as a failure. D-036 forbids those for a
 * child, and for an adult they turn a record into a game they can lose. The one summary is a
 * count ("done on 4 of 7 expected days"), which the server computes and the screen repeats.
 *
 * <p>Days can be ticked after the fact, because "I did it yesterday and forgot to log it" is
 * the most common correction, and the record should be right, not punishing.
 *
 * <p>The day cells are a 7-day strip by default, with the full last-five-weeks grid one tap
 * away in a disclosure — both sized to the 44×44px touch target (plan §19 G-05: the previous
 * 14-equal-column strip was ~20px wide per cell).
 */
export function HabitList({
  memberId,
  timeZone,
  compact = false,
  emptyAction,
}: {
  memberId: string;
  /** The household's IANA zone (D-053) — "today" and the day strip are computed in this zone,
   *  never the device's own (plan §19 G-03). */
  timeZone: string;
  /** Today's summary: tick today only, no history, editing or archive. */
  compact?: boolean;
  emptyAction?: ReactNode;
}) {
  const includeArchived = !compact;
  const habits = useQuery({
    queryKey: ['habits', memberId, includeArchived],
    queryFn: () => listHabits(memberId, includeArchived),
  });

  if (habits.isPending) return <Skeleton lines={2} label={t('loading.default')} />;
  if (habits.isError) return <ErrorState error={habits.error} onRetry={() => void habits.refetch()} />;

  const active = habits.data.filter((h) => h.active);
  const archived = habits.data.filter((h) => !h.active);

  return (
    <>
      {active.length === 0 ? (
        <EmptyState title={compact ? t('habit.emptyCompact') : t('habit.empty')} action={emptyAction} />
      ) : (
        <ul className="habits" role="list">
          {active.map((habit) => (
            <HabitRow key={habit.id} habit={habit} timeZone={timeZone} compact={compact} />
          ))}
        </ul>
      )}

      {!compact && archived.length > 0 && (
        <details className="habits__archived">
          <summary>{t('habit.archivedHeading', { count: archived.length })}</summary>
          <ul className="habits" role="list">
            {archived.map((habit) => (
              <ArchivedHabitRow key={habit.id} habit={habit} />
            ))}
          </ul>
        </details>
      )}
    </>
  );
}

function HabitRow({ habit, timeZone, compact }: { habit: Habit; timeZone: string; compact: boolean }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const today = todayInZone(timeZone);
  const from = isoDaysBefore(today, MONTH_DAYS - 1);

  const history = useQuery({
    queryKey: ['habits', 'history', habit.id, from, today],
    queryFn: () => habitHistory(habit.id, from, today),
  });

  const consistency = useQuery({
    queryKey: ['habits', 'consistency', habit.id],
    queryFn: () => habitConsistency(habit.id),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['habits', 'history', habit.id] });
    void queryClient.invalidateQueries({ queryKey: ['habits', 'consistency', habit.id] });
  };

  const toggle = useMutation({
    mutationFn: ({ date, done }: { date: string; done: boolean }) =>
      done ? uncompleteHabit(habit.id, date) : completeHabit(habit.id, date),
    onSuccess: refresh,
  });

  const archive = useMutation({
    mutationFn: () => archiveHabit(habit.id),
    onSuccess: () => {
      setConfirmArchive(false);
      void queryClient.invalidateQueries({ queryKey: ['habits'] });
    },
  });

  const doneDates = new Set(history.data ?? []);
  const doneToday = doneDates.has(today);
  const error =
    (toggle.error instanceof ApiError && toggle.error.message) ||
    (archive.error instanceof ApiError && archive.error.message) ||
    null;

  const allDays = lastDaysInZone(today, MONTH_DAYS);
  const stripDays = allDays.slice(-STRIP_DAYS);

  return (
    <li className="habit">
      <div className="habit__head">
        <div className="habit__title">
          <span className="habit__name">{habit.name}</span>
          <span className="habit__cadence">{t(CADENCE_KEYS[habit.cadence])}</span>
        </div>
        <Button
          variant={doneToday ? 'primary' : 'secondary'}
          size="sm"
          aria-pressed={doneToday}
          disabled={history.isPending}
          loading={toggle.isPending && toggle.variables?.date === today}
          onClick={() => toggle.mutate({ date: today, done: doneToday })}
        >
          {doneToday ? t('habit.doneToday') : t('habit.markToday')}
        </Button>
      </div>

      {consistency.isSuccess && (
        <p className="habit__consistency">
          {consistency.data.expectedDays === 0
            ? t('habit.noneExpectedYet')
            : t('habit.consistency', {
                completed: consistency.data.completedDays,
                expected: consistency.data.expectedDays,
              })}
        </p>
      )}

      {!compact && history.isSuccess && (
        <>
          <DayGrid
            days={stripDays}
            doneDates={doneDates}
            disabled={toggle.isPending}
            onToggle={(date, done) => toggle.mutate({ date, done })}
            ariaLabel={t('habit.stripLabel' as MessageKey, { name: habit.name })}
          />

          <details className="habit__month">
            <summary>{t('habit.showMonth' as MessageKey)}</summary>
            <DayGrid
              days={allDays}
              doneDates={doneDates}
              disabled={toggle.isPending}
              onToggle={(date, done) => toggle.mutate({ date, done })}
              ariaLabel={t('habit.monthLabel' as MessageKey, { name: habit.name })}
              className="habit__month-grid"
            />
          </details>
        </>
      )}

      {error && <p className="form-error" role="alert">{error}</p>}

      {!compact && !editing && (
        <div className="habit__actions">
          <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>{t('habit.edit')}</Button>
          <Button variant="ghost" size="sm" onClick={() => setConfirmArchive(true)}>{t('habit.archive')}</Button>
        </div>
      )}

      {!compact && editing && <HabitEditForm habit={habit} onDone={() => setEditing(false)} />}

      <ConfirmSheet
        open={confirmArchive}
        onClose={() => setConfirmArchive(false)}
        onConfirm={() => archive.mutate()}
        title={t('habit.archiveConfirmTitle')}
        body={t('habit.archiveConfirmBody')}
        confirmLabel={t('habit.archive')}
        loading={archive.isPending}
      />
    </li>
  );
}

/**
 * A row of equal day cells, each meeting the 44×44px minimum tap target (plan §14/2.5.8) —
 * used both for the always-visible 7-day strip and, inside a `<details>`, the last-five-weeks
 * grid. Seven columns either way, so the grid reads as whole weeks stacked rather than an
 * arbitrary block.
 */
function DayGrid({
  days,
  doneDates,
  disabled,
  onToggle,
  ariaLabel,
  className,
}: {
  days: readonly string[];
  doneDates: ReadonlySet<string>;
  disabled: boolean;
  onToggle: (date: string, done: boolean) => void;
  ariaLabel: string;
  className?: string;
}) {
  return (
    <div className={['habit__days', className].filter(Boolean).join(' ')} role="group" aria-label={ariaLabel}>
      {days.map((date) => {
        const done = doneDates.has(date);
        return (
          <button
            key={date}
            type="button"
            className={done ? 'habit__day habit__day--done' : 'habit__day'}
            aria-pressed={done}
            aria-label={t(done ? 'habit.dayDone' : 'habit.dayNotRecorded', { date: longDayLabel(date) })}
            title={longDayLabel(date)}
            disabled={disabled}
            onClick={() => onToggle(date, done)}
          >
            <span className="habit__dow" aria-hidden="true">{narrowDow(date)}</span>
            <span className="habit__dom" aria-hidden="true">{dayOfMonth(date)}</span>
          </button>
        );
      })}
    </div>
  );
}

function HabitEditForm({ habit, onDone }: { habit: Habit; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(habit.name);
  const [cadence, setCadence] = useState<HabitCadence>(habit.cadence);
  const [problem, setProblem] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => updateHabit(habit.id, { name: name.trim(), cadence }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['habits'] });
      onDone();
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
    <form className="habit__edit" onSubmit={submit} noValidate>
      <label className="field">
        <span className="field__label">{t('habit.nameLabel')}</span>
        <input type="text" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
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
      <div className="habit__actions">
        <Button type="submit" size="sm" loading={save.isPending}>{t('action.save')}</Button>
        <Button variant="ghost" size="sm" onClick={onDone}>{t('action.cancel')}</Button>
      </div>
    </form>
  );
}

function ArchivedHabitRow({ habit }: { habit: Habit }) {
  const queryClient = useQueryClient();
  const reactivate = useMutation({
    mutationFn: () => reactivateHabit(habit.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['habits'] }),
  });

  return (
    <li className="habit habit--archived">
      <div className="habit__head">
        <div className="habit__title">
          <span className="habit__name">{habit.name}</span>
          <span className="habit__cadence">{t(CADENCE_KEYS[habit.cadence])}</span>
        </div>
        <Button variant="secondary" size="sm" loading={reactivate.isPending} onClick={() => reactivate.mutate()}>
          {t('habit.reactivate')}
        </Button>
      </div>
      {reactivate.error instanceof ApiError && (
        <p className="form-error" role="alert">{reactivate.error.message}</p>
      )}
    </li>
  );
}
