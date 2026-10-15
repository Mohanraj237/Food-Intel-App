import { useCallback, useEffect, useId, useRef, useState, type FormEvent, type ReactNode, type RefObject } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useIsSignedIn } from '@/lib/useSession';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { MY_HOUSEHOLDS_QUERY_KEY } from '@/lib/useMyHousehold';
import { createHousehold } from '@/lib/household';
import { ADULT_AGE_BANDS, SEX_OPTIONS, type AgeBand, type Sex } from '@/lib/profile';
import { grant as grantConsent, listPolicies, type ConsentPolicy } from '@/lib/consent';
import {
  ACTIVITY_LEVELS,
  ME_QUERY_KEY,
  provisionIndividual,
  useMeQuery,
  type ActivityLevel,
  type MeHousehold,
  type MeMember,
  type MeView,
} from '@/lib/me';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { onboardingProgress, resolveOnboardingStep, stepAfterProfile, type OnboardingStep } from './onboardingSteps';
import { buildProfileRequest, fetchExistingProfile, putProfileWithActivity } from './profileBasics';
import './OnboardingScreen.css';

/**
 * Welcome — intended at `/welcome` (D-043, D-046, D-048).
 *
 *   1. "Just me" or "Me and my family" — two equal choices; neither is the default.
 *   2. Your name, and for a family the household's name.
 *   3. Age band (adult bands only: this is your own profile), and optionally sex and how active
 *      you are. Nothing here ever becomes a calorie target.
 *   4. Health-tracking consent, shown in the policy's own words, with "Not now" exactly as
 *      prominent as agreeing.
 *
 * Where a visit starts comes from `GET /me` ({@link resolveOnboardingStep}): someone who
 * already has a household is never asked to choose again, which is how a second household
 * would otherwise get made by accident.
 */

type Mode = 'SOLO' | 'FAMILY';

type ReadyMe = MeView & { member: MeMember; household: MeHousehold };

function isReady(view: MeView | undefined): view is ReadyMe {
  return view !== undefined && view.member !== null && view.household !== null;
}

const DISPLAY_LABEL_MAX = 60;
const HOUSEHOLD_NAME_MAX = 80;

export function OnboardingScreen() {
  useDocumentTitleKey('onboarding.title');
  const signedIn = useIsSignedIn();
  const me = useMeQuery();
  const navigate = useNavigate();

  const [step, setStep] = useState<OnboardingStep | null>(null);
  const [startStep, setStartStep] = useState<OnboardingStep | null>(null);
  const [mode, setMode] = useState<Mode | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);

  const finish = useCallback(() => navigate('/', { replace: true }), [navigate]);

  // Where this visit starts is decided once, from the server's view. Later steps move on
  // explicitly, so a background refetch of /me can never yank someone back a step.
  useEffect(() => {
    if (step !== null || me.data === undefined) return;
    const initial = resolveOnboardingStep(me.data);
    setStartStep(initial);
    setStep(initial);
  }, [me.data, step]);

  useEffect(() => {
    if (step === 'DONE') finish();
  }, [step, finish]);

  // A new step replaces the whole form, so focus moves to its heading — otherwise a
  // screen-reader user is left on a button that no longer exists.
  const previousStep = useRef<OnboardingStep | null>(null);
  useEffect(() => {
    if (step === null) return;
    if (previousStep.current !== null && previousStep.current !== step) {
      headingRef.current?.focus();
    }
    previousStep.current = step;
  }, [step]);

  if (!signedIn) {
    return (
      <div className="page onboarding">
        <h1 className="onboarding__heading">{t('onboarding.title')}</h1>
        <p className="onboarding__lede">{t('onboarding.signInPrompt')}</p>
        <div>
          <Link to="/you" className="btn btn--primary btn--sm">
            {t('action.signIn')}
          </Link>
        </div>
      </div>
    );
  }

  if (step === null) {
    return (
      <div className="page onboarding">
        <h1 className="onboarding__heading">{t('onboarding.title')}</h1>
        {me.isError ? (
          <ErrorState error={me.error} onRetry={() => void me.refetch()} />
        ) : (
          <Skeleton lines={4} label={t('loading.default')} />
        )}
      </div>
    );
  }

  const progress = startStep ? onboardingProgress(startStep, step) : null;

  let content: ReactNode;
  if (step === 'MODE' || (step === 'NAME' && mode === null)) {
    content = (
      <ModeStep
        headingRef={headingRef}
        onChoose={(choice) => {
          setMode(choice);
          setStep('NAME');
        }}
      />
    );
  } else if (step === 'NAME' && mode !== null) {
    content = <NameStep headingRef={headingRef} mode={mode} onBack={() => setStep('MODE')} onDone={setStep} />;
  } else if (step === 'PROFILE' || step === 'CONSENT') {
    const view = me.data;
    if (!isReady(view)) {
      // Just after a household was made, /me is being refetched; a failed refetch is shown as
      // such and retried — never answered by offering to make the household again.
      content = me.isFetching ? (
        <Skeleton lines={4} label={t('loading.default')} />
      ) : (
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      );
    } else if (step === 'PROFILE') {
      content = <ProfileStep headingRef={headingRef} me={view} onDone={setStep} />;
    } else {
      content = <ConsentStep headingRef={headingRef} me={view} onFinish={finish} />;
    }
  } else {
    content = <Skeleton lines={2} label={t('loading.default')} />;
  }

  return (
    <div className="page onboarding">
      {progress && (
        <p className="onboarding__progress">{t('onboarding.progress', { current: progress.current, total: progress.total })}</p>
      )}
      {content}
    </div>
  );
}

function StepHeading({ headingRef, id, children }: { headingRef: RefObject<HTMLHeadingElement>; id?: string; children: ReactNode }) {
  return (
    <h1 ref={headingRef} id={id} tabIndex={-1} className="onboarding__heading">
      {children}
    </h1>
  );
}

function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'AGE_BAND_NOT_VALID_FOR_MEMBER') return t('onboarding.error.ageBand');
    return error.message;
  }
  return t('error.internal');
}

/* --------------------------------------------------------------------- 1: mode */

function ModeStep({ headingRef, onChoose }: { headingRef: RefObject<HTMLHeadingElement>; onChoose: (mode: Mode) => void }) {
  const headingId = useId();
  return (
    <>
      <StepHeading headingRef={headingRef} id={headingId}>
        {t('onboarding.mode.heading')}
      </StepHeading>
      <p className="onboarding__lede">{t('onboarding.mode.lede')}</p>

      {/* Same component, same size, same weight: the product has no preference here. */}
      <div className="onboarding__choices" role="group" aria-labelledby={headingId}>
        <ChoiceButton
          title={t('onboarding.mode.justMe')}
          body={t('onboarding.mode.justMeBody')}
          onClick={() => onChoose('SOLO')}
        />
        <ChoiceButton
          title={t('onboarding.mode.family')}
          body={t('onboarding.mode.familyBody')}
          onClick={() => onChoose('FAMILY')}
        />
      </div>

      <p className="onboarding__note">{t('onboarding.mode.note')}</p>
      <p className="onboarding__note">
        {t('onboarding.mode.haveInvite')} <Link to="/you/household">{t('onboarding.mode.joinLink')}</Link>
      </p>
    </>
  );
}

function ChoiceButton({ title, body, onClick }: { title: string; body: string; onClick: () => void }) {
  const titleId = useId();
  const bodyId = useId();
  return (
    <button
      type="button"
      className="onboarding-choice"
      aria-labelledby={titleId}
      aria-describedby={bodyId}
      onClick={onClick}
    >
      <span id={titleId} className="onboarding-choice__title">
        {title}
      </span>
      <span id={bodyId} className="onboarding-choice__body">
        {body}
      </span>
      <span className="onboarding-choice__chevron" aria-hidden="true">
        ›
      </span>
    </button>
  );
}

/* --------------------------------------------------------------------- 2: name */

function NameStep({
  headingRef,
  mode,
  onBack,
  onDone,
}: {
  headingRef: RefObject<HTMLHeadingElement>;
  mode: Mode;
  onBack: () => void;
  onDone: (next: OnboardingStep) => void;
}) {
  const queryClient = useQueryClient();
  const [displayLabel, setDisplayLabel] = useState('');
  const [householdName, setHouseholdName] = useState('');
  const [localError, setLocalError] = useState<MessageKey | null>(null);
  const displayHintId = useId();
  const householdHintId = useId();
  const errorId = useId();

  const create = useMutation({
    mutationFn: async (): Promise<MeView | null> => {
      if (mode === 'SOLO') {
        // Idempotent server-side: a retry after a dropped response never makes a second household.
        return provisionIndividual(displayLabel.trim());
      }
      await createHousehold(householdName.trim(), displayLabel.trim());
      return null;
    },
    onSuccess: async (view) => {
      if (view) {
        queryClient.setQueryData(ME_QUERY_KEY, view);
      } else {
        await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
      }
      // Screens still on the older hooks read these; they must not keep saying "no household".
      void queryClient.invalidateQueries({ queryKey: MY_HOUSEHOLDS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ['household'] });

      const fresh = queryClient.getQueryData<MeView>(ME_QUERY_KEY);
      // The household exists now, whatever a failed refetch left in the cache: never go back to
      // MODE from here (that path makes a second household).
      const next = fresh && fresh.mode !== 'NONE' ? resolveOnboardingStep(fresh) : 'PROFILE';
      onDone(next === 'MODE' ? 'PROFILE' : next);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (displayLabel.trim().length === 0) {
      setLocalError('onboarding.name.required');
      return;
    }
    if (mode === 'FAMILY' && householdName.trim().length === 0) {
      setLocalError('onboarding.name.householdRequired');
      return;
    }
    setLocalError(null);
    create.mutate();
  }

  const serverError = create.error ? errorText(create.error) : null;
  const hasError = localError !== null || serverError !== null;

  return (
    <form className="onboarding__form" onSubmit={submit} noValidate>
      <StepHeading headingRef={headingRef}>
        {mode === 'SOLO' ? t('onboarding.name.headingSolo') : t('onboarding.name.headingFamily')}
      </StepHeading>

      <label className="field">
        <span className="field__label">{t('onboarding.name.displayLabel')}</span>
        <input
          type="text"
          value={displayLabel}
          maxLength={DISPLAY_LABEL_MAX}
          autoComplete="nickname"
          required
          aria-invalid={localError === 'onboarding.name.required' || undefined}
          aria-describedby={hasError ? `${displayHintId} ${errorId}` : displayHintId}
          onChange={(e) => setDisplayLabel(e.target.value)}
        />
        <span id={displayHintId} className="field__hint">
          {t('onboarding.name.displayLabelHint')}
        </span>
      </label>

      {mode === 'FAMILY' && (
        <label className="field">
          <span className="field__label">{t('onboarding.name.householdName')}</span>
          <input
            type="text"
            value={householdName}
            maxLength={HOUSEHOLD_NAME_MAX}
            autoComplete="off"
            required
            placeholder={t('onboarding.name.householdPlaceholder')}
            aria-invalid={localError === 'onboarding.name.householdRequired' || undefined}
            aria-describedby={hasError ? `${householdHintId} ${errorId}` : householdHintId}
            onChange={(e) => setHouseholdName(e.target.value)}
          />
          <span id={householdHintId} className="field__hint">
            {t('onboarding.name.householdNameHint')}
          </span>
        </label>
      )}

      {hasError && (
        <p id={errorId} className="form-error" role="alert">
          {localError ? t(localError) : serverError}
        </p>
      )}

      <div className="onboarding__actions">
        <Button type="submit" loading={create.isPending}>
          {t('action.continue')}
        </Button>
        <Button variant="ghost" onClick={onBack} disabled={create.isPending}>
          {t('action.back')}
        </Button>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------------ 3: profile */

const ACTIVITY_LABEL_KEYS: Record<ActivityLevel, MessageKey> = {
  SEDENTARY: 'onboarding.activity.SEDENTARY',
  LIGHTLY_ACTIVE: 'onboarding.activity.LIGHTLY_ACTIVE',
  MODERATELY_ACTIVE: 'onboarding.activity.MODERATELY_ACTIVE',
  VERY_ACTIVE: 'onboarding.activity.VERY_ACTIVE',
};

const ACTIVITY_HINT_KEYS: Record<ActivityLevel, MessageKey> = {
  SEDENTARY: 'onboarding.activityHint.SEDENTARY',
  LIGHTLY_ACTIVE: 'onboarding.activityHint.LIGHTLY_ACTIVE',
  MODERATELY_ACTIVE: 'onboarding.activityHint.MODERATELY_ACTIVE',
  VERY_ACTIVE: 'onboarding.activityHint.VERY_ACTIVE',
};

function ProfileStep({
  headingRef,
  me,
  onDone,
}: {
  headingRef: RefObject<HTMLHeadingElement>;
  me: ReadyMe;
  onDone: (next: OnboardingStep) => void;
}) {
  const queryClient = useQueryClient();
  const householdId = me.household.id;
  const memberId = me.member.id;

  // A PUT replaces the whole profile, so an existing one is read first and kept intact apart
  // from the three answers asked here. Its own key: the shared ['profile', …] key holds a
  // query that throws on 404, and must not find `null` in its cache.
  const existing = useQuery({
    queryKey: ['onboarding', 'profile', householdId, memberId],
    queryFn: () => fetchExistingProfile(householdId, memberId),
    enabled: me.profile.exists,
    retry: false,
  });
  const ready = !me.profile.exists || existing.isSuccess;

  const [initialized, setInitialized] = useState(false);
  const [ageBand, setAgeBand] = useState<AgeBand | null>(null);
  const [sex, setSex] = useState<Sex | null>(null);
  const [activity, setActivity] = useState<ActivityLevel | null>(null);
  const [localError, setLocalError] = useState<MessageKey | null>(null);

  useEffect(() => {
    if (initialized || !ready) return;
    const current = existing.data ?? null;
    if (current) {
      setAgeBand(ADULT_AGE_BANDS.includes(current.ageBand) ? current.ageBand : null);
      setSex(current.sex ?? null);
      setActivity(current.activityLevel ?? null);
    } else if (me.profile.activityLevel) {
      setActivity(me.profile.activityLevel);
    }
    setInitialized(true);
  }, [initialized, ready, existing.data, me.profile.activityLevel]);

  const save = useMutation({
    mutationFn: () =>
      putProfileWithActivity(
        householdId,
        memberId,
        buildProfileRequest(existing.data ?? null, { ageBand: ageBand as AgeBand, sex, activityLevel: activity }),
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ['profile'] }),
        queryClient.invalidateQueries({ queryKey: ['onboarding', 'profile'] }),
      ]);
      onDone(stepAfterProfile(queryClient.getQueryData<MeView>(ME_QUERY_KEY) ?? me));
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (ageBand === null) {
      setLocalError('onboarding.profile.ageBandRequired');
      return;
    }
    setLocalError(null);
    save.mutate();
  }

  if (me.profile.exists && existing.isError) {
    // Saving without the current profile would wipe allergens or a diet someone recorded.
    return (
      <>
        <StepHeading headingRef={headingRef}>{t('onboarding.profile.heading')}</StepHeading>
        <ErrorState error={existing.error} onRetry={() => void existing.refetch()} />
      </>
    );
  }

  if (!initialized) {
    return (
      <>
        <StepHeading headingRef={headingRef}>{t('onboarding.profile.heading')}</StepHeading>
        <Skeleton lines={4} label={t('loading.default')} />
      </>
    );
  }

  const errorMessage = localError ? t(localError) : save.error ? errorText(save.error) : null;

  return (
    <form className="onboarding__form" onSubmit={submit} noValidate>
      <StepHeading headingRef={headingRef}>{t('onboarding.profile.heading')}</StepHeading>
      <p className="onboarding__lede">{t('onboarding.profile.lede')}</p>

      <fieldset className="onboarding__fieldset">
        <legend className="field__label">{t('onboarding.profile.ageBand')}</legend>
        <p className="field__hint">{t('profile.ageBandExplainer')}</p>
        <div className="onboarding-pills">
          {ADULT_AGE_BANDS.map((band) => (
            <label key={band} className="onboarding-pill">
              <input
                type="radio"
                name="onboarding-age-band"
                value={band}
                checked={ageBand === band}
                onChange={() => {
                  setAgeBand(band);
                  setLocalError(null);
                }}
              />
              <span>{t(`ageBand.${band}`)}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="onboarding__fieldset">
        <legend className="field__label">
          {t('profile.sex')} <span className="field__optional">{t('profile.sexOptional')}</span>
        </legend>
        <div className="onboarding-pills">
          {SEX_OPTIONS.map((option) => (
            <label key={option} className="onboarding-pill">
              <input
                type="radio"
                name="onboarding-sex"
                value={option}
                checked={sex === option}
                onChange={() => setSex(option)}
              />
              <span>{t(`sex.${option}`)}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="onboarding__fieldset">
        <legend className="field__label">
          {t('onboarding.profile.activity')} <span className="field__optional">{t('onboarding.profile.optional')}</span>
        </legend>
        <p className="field__hint">{t('onboarding.profile.activityNote')}</p>
        <div className="onboarding-options">
          {ACTIVITY_LEVELS.map((level) => (
            <label key={level} className="onboarding-option">
              <input
                type="radio"
                name="onboarding-activity"
                value={level}
                checked={activity === level}
                onChange={() => setActivity(level)}
              />
              <span className="onboarding-option__text">
                <span className="onboarding-option__label">{t(ACTIVITY_LABEL_KEYS[level])}</span>
                <span className="onboarding-option__hint">{t(ACTIVITY_HINT_KEYS[level])}</span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>

      {errorMessage && (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      )}

      <div className="onboarding__actions">
        <Button type="submit" loading={save.isPending}>
          {t('action.continue')}
        </Button>
        <Button variant="ghost" onClick={() => onDone(stepAfterProfile(me))} disabled={save.isPending}>
          {t('onboarding.profile.skip')}
        </Button>
      </div>
      <p className="onboarding__note">{t('onboarding.profile.skipNote')}</p>
    </form>
  );
}

/* ------------------------------------------------------------------ 4: consent */

function ConsentStep({
  headingRef,
  me,
  onFinish,
}: {
  headingRef: RefObject<HTMLHeadingElement>;
  me: ReadyMe;
  onFinish: () => void;
}) {
  const queryClient = useQueryClient();
  const purposeCode = me.trackingConsent.purposeCode;
  const memberId = me.member.id;

  const policies = useQuery({
    queryKey: ['consent', 'policies'],
    queryFn: listPolicies,
    staleTime: 30 * 60 * 1000,
  });
  const policy = policies.data?.find((p) => p.purposeCode === purposeCode) ?? null;

  const grantMutation = useMutation({
    mutationFn: () => grantConsent(memberId, purposeCode),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ['consent', 'grants', memberId] }),
      ]);
      onFinish();
    },
  });

  if (policies.isPending) {
    return (
      <>
        <StepHeading headingRef={headingRef}>{t('onboarding.consent.heading')}</StepHeading>
        <Skeleton lines={4} label={t('loading.default')} />
      </>
    );
  }

  // Without the real text there is nothing honest to agree to, so no agree button is offered.
  if (policies.isError || policy === null) {
    return (
      <>
        <StepHeading headingRef={headingRef}>{t('onboarding.consent.heading')}</StepHeading>
        {policies.isError ? (
          <ErrorState error={policies.error} onRetry={() => void policies.refetch()} />
        ) : (
          <p className="onboarding__lede">{t('onboarding.consent.unavailable')}</p>
        )}
        <div className="onboarding__actions">
          <Button variant="secondary" onClick={onFinish}>
            {t('onboarding.consent.continueWithout')}
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <StepHeading headingRef={headingRef}>{policy.purposeName}</StepHeading>
      <PolicyText policy={policy} />

      {grantMutation.error && (
        <p className="form-error" role="alert">
          {errorText(grantMutation.error)}
        </p>
      )}

      {/* Equal weight, on purpose: same variant, same size, side by side (design system,
          ConsentBanner — "Decline is as prominent as grant"). */}
      <div className="onboarding__pair">
        <Button variant="secondary" fullWidth loading={grantMutation.isPending} onClick={() => grantMutation.mutate()}>
          {t('onboarding.consent.grant')}
        </Button>
        <Button variant="secondary" fullWidth disabled={grantMutation.isPending} onClick={onFinish}>
          {t('onboarding.consent.notNow')}
        </Button>
      </div>
      <p className="onboarding__note">{t('onboarding.consent.notNowExplainer')}</p>
    </>
  );
}

/** The purpose exactly as the policy states it: description, summary, version, full notice. */
function PolicyText({ policy }: { policy: ConsentPolicy }) {
  const internal = policy.documentUri.startsWith('/');
  return (
    <div className="onboarding__policy">
      <p>{policy.purposeDescription}</p>
      <p>{policy.summary}</p>
      <p className="onboarding__policy-meta">
        {t('consent.policyVersion', { version: policy.policyVersion })}
        {' · '}
        {internal ? (
          <Link to={policy.documentUri}>{t('onboarding.consent.readNotice')}</Link>
        ) : (
          <a href={policy.documentUri} target="_blank" rel="noreferrer">
            {t('onboarding.consent.readNotice')}
          </a>
        )}
      </p>
    </div>
  );
}
