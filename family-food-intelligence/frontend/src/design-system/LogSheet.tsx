import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { DASHBOARD_QUERY_KEYS, useFamilyOverview } from '@/lib/dashboard';
import { getMemberAccess } from '@/lib/access';
import {
  listMetricDefinitions,
  recordObservation,
  type MetricCategory,
  type MetricDefinition,
} from '@/lib/wellness';
import { completeHabit, listHabits, todayIso, type Habit } from '@/lib/goals';
import { BottomSheet } from './BottomSheet';
import { Button } from './Button';
import { ErrorState, Skeleton } from './States';
import { QuickActionChipGroup } from './QuickActionChip';
import { SubjectSwitcher, type SubjectOption } from './SubjectSwitcher';
import { Icon, type IconName } from './Icon';
import { useToast } from './Toast';
import './LogSheet.css';

/**
 * The Log sheet (plan §8.2/§18 1.2) — the center nav button's quick-entry surface, and the one
 * mechanism this whole file owns end to end (frontend-implementation-workstreams.md §3/§4.2):
 * mounted once by `AppShell`, opened from anywhere via {@link useLogSheet}, deep-linkable at
 * `/log/:kind` via {@link LogSheetRouteBridge}. No other workstream edits this file; they only
 * call `useLogSheet().open(...)`.
 */

export type LogKind = 'water' | 'activity' | 'sleep' | 'mood' | 'body' | 'readings' | 'habit' | 'meal';

const KIND_TO_CATEGORY: Record<Exclude<LogKind, 'habit' | 'meal'>, MetricCategory> = {
  water: 'HYDRATION',
  activity: 'ACTIVITY',
  sleep: 'SLEEP',
  mood: 'WELLBEING',
  body: 'BODY',
  readings: 'CLINICAL',
};

/** `/log/:kind`'s valid values (plan §3) — deliberately excludes `meal`: a meal deep link is
 *  `/meals/new` itself, never `/log/meal` (plan §18 1.2's "meal → /meals/new" note). */
const DEEP_LINK_KINDS: readonly LogKind[] = ['water', 'activity', 'sleep', 'mood', 'body', 'readings', 'habit'];

const KIND_ICONS: Record<LogKind, IconName> = {
  water: 'water',
  activity: 'steps',
  sleep: 'sleep',
  mood: 'mood',
  body: 'body',
  readings: 'readings',
  habit: 'habit',
  meal: 'meal',
};

const KIND_LABEL_KEYS: Record<LogKind, Parameters<typeof t>[0]> = {
  water: 'logSheet.kind.water',
  activity: 'logSheet.kind.activity',
  sleep: 'logSheet.kind.sleep',
  mood: 'logSheet.kind.mood',
  body: 'logSheet.kind.body',
  readings: 'logSheet.kind.readings',
  habit: 'logSheet.kind.habit',
  meal: 'logSheet.kind.meal',
};

/** Common preset amounts per category — a starting point, not a claim about any one metric's
 *  real unit; the chip label always states the subject metric's actual unit, so a preset never
 *  misrepresents what it will save. */
const PRESETS: Record<Exclude<LogKind, 'habit' | 'meal'>, readonly number[]> = {
  water: [150, 250, 500, 1000],
  activity: [10, 20, 30, 60],
  sleep: [6, 7, 7.5, 8, 9],
  mood: [1, 2, 3, 4, 5],
  body: [],
  readings: [],
};

interface LogSheetOpenOptions {
  /** Preselects a tile; omit to open on the tile grid. */
  kind?: LogKind;
  /** Defaults to the caller's own member id. */
  subjectId?: string;
}

interface LogSheetContextValue {
  open: (options?: LogSheetOpenOptions) => void;
  close: () => void;
}

const LogSheetContext = createContext<LogSheetContextValue | null>(null);

export function useLogSheet(): LogSheetContextValue {
  const ctx = useContext(LogSheetContext);
  if (!ctx) {
    throw new Error('useLogSheet must be used within LogSheetProvider');
  }
  return ctx;
}

interface LogSheetState {
  open: boolean;
  kind: LogKind | null;
  subjectId: string | null;
}

const CLOSED_STATE: LogSheetState = { open: false, kind: null, subjectId: null };

/**
 * Mounts the sheet once (in `AppShell`) and provides {@link useLogSheet} to everything inside
 * it. Owns the `#log` history entry itself: opening pushes the hash, closing goes back to it —
 * so the Android/browser back button closes the sheet rather than leaving the screen underneath
 * (plan §18 1.2's accessibility requirement), and it stays in sync if the sheet is closed some
 * other way (Escape, the Close button) by trimming the hash back off on close.
 */
export function LogSheetProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<LogSheetState>(CLOSED_STATE);

  const open = useCallback((options?: LogSheetOpenOptions) => {
    setState({ open: true, kind: options?.kind ?? null, subjectId: options?.subjectId ?? null });
    if (window.location.hash !== '#log') {
      window.history.pushState(null, '', `${window.location.pathname}${window.location.search}#log`);
    }
  }, []);

  const close = useCallback(() => {
    setState(CLOSED_STATE);
    if (window.location.hash === '#log') {
      window.history.back();
    }
  }, []);

  // Back/forward navigation (including the back button closing our own #log entry) must close
  // the sheet even when nothing in this component called `close` — otherwise the URL and the
  // sheet's visible state disagree.
  useEffect(() => {
    function onHashChange() {
      if (window.location.hash !== '#log') {
        setState((s) => (s.open ? CLOSED_STATE : s));
      }
    }
    window.addEventListener('hashchange', onHashChange);
    window.addEventListener('popstate', onHashChange);
    return () => {
      window.removeEventListener('hashchange', onHashChange);
      window.removeEventListener('popstate', onHashChange);
    };
  }, []);

  // Inert, not just visually hidden: a screen-reader or keyboard user tabbing "through" the page
  // behind an aria-modal sheet is a real, reported failure mode of dialogs that rely on
  // aria-modal alone in some assistive-tech/browser combinations.
  useEffect(() => {
    const main = document.getElementById('main');
    if (!main) return undefined;
    if (state.open) {
      main.setAttribute('inert', '');
    } else {
      main.removeAttribute('inert');
    }
    return () => main.removeAttribute('inert');
  }, [state.open]);

  const value = useMemo<LogSheetContextValue>(() => ({ open, close }), [open, close]);

  return (
    <LogSheetContext.Provider value={value}>
      {children}
      <LogSheetPanel state={state} onClose={close} />
    </LogSheetContext.Provider>
  );
}

/**
 * The deep-link mechanism for `/log/:kind` (plan §3): opens the sheet for that kind, then
 * immediately replaces the URL with `/today` underneath it. The sheet's own state lives in
 * `LogSheetProvider`, a sibling of the routed `<Outlet>`, so it survives that redirect instead of
 * unmounting with the route it briefly rendered on.
 */
export function LogSheetRouteBridge() {
  const { kind } = useParams<{ kind: string }>();
  const navigate = useNavigate();
  const { open } = useLogSheet();

  useEffect(() => {
    const validKind = DEEP_LINK_KINDS.includes(kind as LogKind) ? (kind as LogKind) : undefined;
    open(validKind ? { kind: validKind } : {});
    navigate('/today', { replace: true });
    // Intentionally once: this component exists only to bridge one navigation.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return null;
}

function LogSheetPanel({ state, onClose }: { state: LogSheetState; onClose: () => void }) {
  const navigate = useNavigate();
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: state.open && signedIn });
  const queryClient = useQueryClient();

  const selfMemberId = me.data?.member?.id ?? null;
  const householdId = me.data?.household?.id ?? null;
  const isFamily = me.data?.mode === 'FAMILY';

  const overview = useFamilyOverview(isFamily ? householdId : null, undefined);

  const subjects: SubjectOption[] = useMemo(() => {
    if (!selfMemberId || !me.data?.member) return [];
    const self: SubjectOption = { id: selfMemberId, label: t('subjectSwitcher.you') };
    if (!isFamily || !overview.data) return [self];
    const others = overview.data.members
      .filter((m) => !m.isYou && (m.relationship === 'GUARDIAN' || m.relationship === 'CAREGIVER'))
      .map((m) => ({ id: m.memberId, label: m.displayLabel }));
    return [self, ...others];
  }, [selfMemberId, me.data, isFamily, overview.data]);

  const [subjectId, setSubjectId] = useState<string | null>(null);
  useEffect(() => {
    if (!state.open) return;
    setSubjectId(state.subjectId ?? selfMemberId);
  }, [state.open, state.subjectId, selfMemberId]);

  const targetSubjectId = subjectId ?? selfMemberId;
  // Logging for someone else is gated on that scope's MANAGE level (plan §18 1.2) — never shown
  // then rejected. For self this is skipped: SELF always may, and a self-access check would just
  // describe the caller's own relationship (SELF) back to them.
  const isOtherSubject = subjects.length > 1 && subjects[0]?.id !== targetSubjectId;
  const access = useQuery({
    queryKey: ['access', 'member', targetSubjectId],
    queryFn: () => getMemberAccess(targetSubjectId as string),
    enabled: isOtherSubject && targetSubjectId !== null,
  });
  const manageableScopes: Set<string> | null = isOtherSubject
    ? new Set<string>((access.data?.scopes ?? []).filter((s) => s.level === 'MANAGE').map((s) => s.scope))
    : null;
  // The backend only ever hands out `GUARDIAN` for a child subject (`HealthAccessPolicyImpl`) —
  // a server-given fact, not a client guess from member type/age (D-046). A guardian's access
  // decision names every scope at MANAGE (D-045's "everything, at MANAGE"), which is a *scope*-
  // level answer and says nothing about which individual metrics within BODY/CLINICAL a child may
  // have (that is `MetricDefinition.adultOnly`, D-036) — so this subject-is-a-child flag is what
  // actually keeps the Log sheet from ever offering a BODY/CLINICAL tile for a child, rather than
  // showing one and letting the domain's age gate reject the save.
  const isChildSubject = access.data?.relationship === 'GUARDIAN';

  const [activeKind, setActiveKind] = useState<LogKind | null>(null);
  useEffect(() => {
    if (state.open) setActiveKind(state.kind);
  }, [state.open, state.kind]);

  const [status, setStatus] = useState('');
  const toast = useToast();
  useEffect(() => {
    if (state.open) setStatus('');
  }, [state.open]);

  function handleClose() {
    onClose();
  }

  function backToGrid() {
    setActiveKind(null);
  }

  function afterSave(message: string) {
    setStatus(message);
    toast.show(message);
    setActiveKind(null);
    if (subjectId) {
      void queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEYS.all });
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
      void queryClient.invalidateQueries({ queryKey: ['habits'] });
    }
  }

  let body: ReactNode;
  if (!signedIn) {
    body = <p>{t('logSheet.signInPrompt')}</p>;
  } else if (me.isPending || (isFamily && overview.isPending)) {
    body = <Skeleton lines={4} label={t('loading.default')} />;
  } else if (me.isError) {
    body = <ErrorState error={me.error} onRetry={() => void me.refetch()} />;
  } else if (!selfMemberId || !householdId) {
    body = <p>{t('logSheet.noHousehold')}</p>;
  } else if (activeKind && activeKind !== 'meal' && activeKind !== 'habit') {
    body = (
      <MetricForm
        kind={activeKind}
        subjectId={subjectId ?? selfMemberId}
        isChildSubject={isChildSubject}
        onBack={backToGrid}
        onSaved={afterSave}
      />
    );
  } else if (activeKind === 'habit') {
    body = <HabitTiles subjectId={subjectId ?? selfMemberId} onBack={backToGrid} onSaved={afterSave} />;
  } else {
    body = (
      <TileGrid
        subjectId={subjectId ?? selfMemberId}
        subjects={subjects}
        manageableScopes={manageableScopes}
        isChildSubject={isChildSubject}
        onSubjectChange={setSubjectId}
        onPick={(kind) => {
          if (kind === 'meal') {
            handleClose();
            navigate('/meals/new');
            return;
          }
          setActiveKind(kind);
        }}
      />
    );
  }

  return (
    <BottomSheet open={state.open} onClose={handleClose} title={t('logSheet.title')}>
      <p className="log-sheet__status" role="status" aria-live="polite">
        {status}
      </p>
      {body}
      <div className="log-sheet__closerow">
        <Button variant="ghost" onClick={handleClose}>
          {t('action.close')}
        </Button>
      </div>
    </BottomSheet>
  );
}

function TileGrid({
  subjectId,
  subjects,
  manageableScopes,
  isChildSubject,
  onSubjectChange,
  onPick,
}: {
  subjectId: string;
  subjects: readonly SubjectOption[];
  manageableScopes: ReadonlySet<string> | null;
  isChildSubject: boolean;
  onSubjectChange: (id: string) => void;
  onPick: (kind: LogKind) => void;
}) {
  const metricDefs = useQuery({
    queryKey: ['wellness', 'metrics'],
    queryFn: listMetricDefinitions,
    staleTime: 30 * 60 * 1000,
  });

  if (metricDefs.isPending) {
    return <Skeleton lines={4} label={t('loading.default')} />;
  }
  if (metricDefs.isError) {
    return <ErrorState error={metricDefs.error} onRetry={() => void metricDefs.refetch()} />;
  }

  const byCategory = new Map<MetricCategory, MetricDefinition[]>();
  for (const def of metricDefs.data) {
    // A child subject never gets a BODY/CLINICAL (or any adult-only) tile, full stop — the
    // subject's own eligibility (`MetricDefinition.adultOnly`, D-036), not the caller's scope
    // grant, which for a guardian is "everything, at MANAGE" and says nothing about age (see the
    // `isChildSubject` note where the sheet's callers compute it).
    if (isChildSubject && def.adultOnly) continue;
    const list = byCategory.get(def.category) ?? [];
    list.push(def);
    byCategory.set(def.category, list);
  }

  const metricKinds = (Object.keys(KIND_TO_CATEGORY) as Array<keyof typeof KIND_TO_CATEGORY>).filter((kind) => {
    const category = KIND_TO_CATEGORY[kind];
    if (manageableScopes && !manageableScopes.has(category)) return false;
    return (byCategory.get(category)?.length ?? 0) > 0;
  });

  const showHabit = manageableScopes ? manageableScopes.has('HABITS') : true;

  return (
    <div className="log-sheet__grid-wrap">
      {subjects.length > 1 && (
        <SubjectSwitcher subjects={subjects} value={subjectId} onChange={onSubjectChange} />
      )}
      <ul className="log-sheet__tiles" role="list">
        <li>
          <LogTile kind="meal" onPick={onPick} />
        </li>
        {metricKinds.map((kind) => (
          <li key={kind}>
            <LogTile kind={kind} onPick={onPick} />
          </li>
        ))}
        {showHabit && (
          <li>
            <LogTile kind="habit" onPick={onPick} />
          </li>
        )}
      </ul>
      {metricKinds.length === 0 && !showHabit && (
        <p className="log-sheet__empty">{t('logSheet.nothingToLog')}</p>
      )}
    </div>
  );
}

function LogTile({ kind, onPick }: { kind: LogKind; onPick: (kind: LogKind) => void }) {
  return (
    <button type="button" className={`log-sheet__tile log-sheet__tile--${kind}`} onClick={() => onPick(kind)}>
      <span className="log-sheet__tile-icon">
        <Icon name={KIND_ICONS[kind]} size={22} />
      </span>
      <span className="log-sheet__tile-label">{t(KIND_LABEL_KEYS[kind])}</span>
    </button>
  );
}

function MetricForm({
  kind,
  subjectId,
  isChildSubject,
  onBack,
  onSaved,
}: {
  kind: Exclude<LogKind, 'habit' | 'meal'>;
  subjectId: string;
  isChildSubject: boolean;
  onBack: () => void;
  onSaved: (message: string) => void;
}) {
  const category = KIND_TO_CATEGORY[kind];
  const metricDefs = useQuery({
    queryKey: ['wellness', 'metrics'],
    queryFn: listMetricDefinitions,
    staleTime: 30 * 60 * 1000,
  });
  // Belt-and-suspenders alongside `TileGrid`'s own filter above: this form never lands on an
  // adult-only metric for a child subject even if it were ever reached some other way (D-036).
  const metric =
    metricDefs.data?.find((m) => m.category === category && (!isChildSubject || !m.adultOnly)) ?? null;

  const [preset, setPreset] = useState<string | null>(null);
  const [customValue, setCustomValue] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  const save = useMutation({
    mutationFn: (value: number) => recordObservation(subjectId, { metricCode: metric?.code as string, value }),
    onSuccess: () => onSaved(t('logSheet.saved', { metric: t(KIND_LABEL_KEYS[kind]) })),
  });

  if (metricDefs.isPending) {
    return <Skeleton lines={3} label={t('loading.default')} />;
  }
  if (!metric) {
    return <ErrorState error={metricDefs.error ?? new Error('metric unavailable')} onRetry={() => void metricDefs.refetch()} />;
  }

  const presets = PRESETS[kind];
  const value = preset ?? customValue;
  const numeric = Number(value);
  const canSave = value.trim().length > 0 && Number.isFinite(numeric) && numeric > 0;

  return (
    <form
      className="log-sheet__form"
      onSubmit={(e) => {
        e.preventDefault();
        if (canSave) save.mutate(numeric);
      }}
    >
      <button type="button" className="log-sheet__back" onClick={onBack}>
        <Icon name="chevron-left" size={18} /> {t('action.back')}
      </button>
      <h3 className="log-sheet__form-title">
        <span className="log-sheet__tile-icon">
          <Icon name={KIND_ICONS[kind]} size={20} />
        </span>
        {t(KIND_LABEL_KEYS[kind])}
      </h3>

      {presets.length > 0 && (
        <QuickActionChipGroup
          legend={t('logSheet.amount')}
          options={presets.map((p) => ({ value: String(p), label: `${p} ${metric.unit}` }))}
          value={preset}
          onChange={(v) => {
            setPreset(v);
            setCustomValue('');
          }}
        />
      )}

      <label className="field">
        <span className="field__label">{t('logSheet.customAmount', { unit: metric.unit })}</span>
        <input
          ref={inputRef}
          type="number"
          inputMode="decimal"
          min="0"
          step="any"
          value={customValue}
          onChange={(e) => {
            setCustomValue(e.target.value);
            setPreset(null);
          }}
        />
      </label>

      {save.isError && (
        <p className="form-error" role="alert">
          {save.error instanceof ApiError ? save.error.message : t('error.internal')}
        </p>
      )}

      <Button type="submit" fullWidth disabled={!canSave} loading={save.isPending}>
        {t('action.save')}
      </Button>
    </form>
  );
}

function HabitTiles({
  subjectId,
  onBack,
  onSaved,
}: {
  subjectId: string;
  onBack: () => void;
  onSaved: (message: string) => void;
}) {
  const habits = useQuery({
    queryKey: ['habits', subjectId, 'active'],
    queryFn: () => listHabits(subjectId, false),
  });

  const [savingId, setSavingId] = useState<string | null>(null);

  async function markDone(habit: Habit) {
    setSavingId(habit.id);
    try {
      await completeHabit(habit.id, todayIso());
      onSaved(t('logSheet.habitSaved', { habit: habit.name }));
    } finally {
      setSavingId(null);
    }
  }

  return (
    <div className="log-sheet__form">
      <button type="button" className="log-sheet__back" onClick={onBack}>
        <Icon name="chevron-left" size={18} /> {t('action.back')}
      </button>
      <h3 className="log-sheet__form-title">{t(KIND_LABEL_KEYS.habit)}</h3>

      {habits.isPending ? (
        <Skeleton lines={3} label={t('loading.default')} />
      ) : habits.isError ? (
        <ErrorState error={habits.error} onRetry={() => void habits.refetch()} />
      ) : habits.data.length === 0 ? (
        <p className="log-sheet__empty">{t('logSheet.noHabits')}</p>
      ) : (
        <ul className="log-sheet__habit-list" role="list">
          {habits.data.map((habit) => (
            <li key={habit.id} className="log-sheet__habit-row">
              <span>{habit.name}</span>
              <Button
                variant="secondary"
                size="sm"
                loading={savingId === habit.id}
                onClick={() => void markDone(habit)}
              >
                {t('logSheet.markDone')}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
