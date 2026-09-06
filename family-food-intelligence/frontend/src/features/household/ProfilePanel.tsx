import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import type { HouseholdMember } from '@/lib/household';
import {
  ADULT_AGE_BANDS,
  ALLERGEN_CODES,
  CHILD_AGE_BANDS,
  DIETARY_PREFERENCE_OPTIONS,
  GOAL_CODES,
  SEX_OPTIONS,
  getProfile,
  putProfile,
  type AgeBand,
  type AllergenCode,
  type DietaryPreference,
  type GoalCode,
  type Sex,
} from '@/lib/profile';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import './FamilyScreen.css';

interface ProfilePanelProps {
  householdId: string;
  member: HouseholdMember;
  onBack: () => void;
}

/**
 * Age band, sex, dietary preference, goals, allergens and conditions for one
 * household member. A 404 from `getProfile` means "no profile yet" — an empty
 * form waiting to be filled in for the first time, not an error (see
 * `lib/profile.ts`). Any other failure is a real `ErrorState`.
 */
export function ProfilePanel({ householdId, member, onBack }: ProfilePanelProps) {
  const queryClient = useQueryClient();
  const queryKey = ['profile', householdId, member.id] as const;

  const profileQuery = useQuery({
    queryKey,
    queryFn: () => getProfile(householdId, member.id),
    retry: false,
  });

  const isNotFound =
    profileQuery.isError && profileQuery.error instanceof ApiError && profileQuery.error.status === 404;

  // Local form state is seeded once, the first time we know what it should
  // contain (an existing profile, or confirmation there is none yet). After
  // that this component owns the values until the user saves.
  const [initialized, setInitialized] = useState(false);
  const [ageBand, setAgeBand] = useState<AgeBand | ''>('');
  const [sex, setSex] = useState<Sex | ''>('');
  const [dietaryPreference, setDietaryPreference] = useState<DietaryPreference | ''>('');
  const [goals, setGoals] = useState<Set<GoalCode>>(new Set());
  const [allergens, setAllergens] = useState<Set<AllergenCode>>(new Set());
  const [conditionsText, setConditionsText] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [savedNotice, setSavedNotice] = useState(false);

  useEffect(() => {
    if (initialized) return;
    if (profileQuery.isSuccess) {
      const p = profileQuery.data;
      setAgeBand(p.ageBand);
      setSex(p.sex ?? '');
      setDietaryPreference(p.dietaryPreference ?? '');
      setGoals(new Set(p.goals));
      setAllergens(new Set(p.allergens));
      setConditionsText(p.conditions.join(', '));
      setInitialized(true);
    } else if (isNotFound) {
      setInitialized(true);
    }
  }, [initialized, profileQuery.isSuccess, profileQuery.data, isNotFound]);

  const saveMutation = useMutation({
    mutationFn: () =>
      putProfile(householdId, member.id, {
        ageBand: ageBand as AgeBand,
        sex: sex === '' ? null : sex,
        dietaryPreference: dietaryPreference === '' ? null : dietaryPreference,
        goals: Array.from(goals),
        allergens: Array.from(allergens),
        conditions: conditionsText
          .split(',')
          .map((c) => c.trim())
          .filter((c) => c.length > 0),
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKey, saved);
      setSavedNotice(true);
      window.setTimeout(() => setSavedNotice(false), 3000);
    },
  });

  function toggleGoal(goal: GoalCode) {
    setGoals((prev) => {
      const next = new Set(prev);
      if (next.has(goal)) next.delete(goal);
      else next.add(goal);
      return next;
    });
  }

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
    if (ageBand === '') {
      setValidationError(t('profile.ageBandRequired'));
      return;
    }
    setValidationError(null);
    saveMutation.mutate();
  }

  const ageBandOptions = member.memberType === 'CHILD' ? CHILD_AGE_BANDS : ADULT_AGE_BANDS;
  const saveErrorMessage = saveMutation.error instanceof ApiError ? saveMutation.error.message : null;

  return (
    <div className="page">
      <Button variant="ghost" size="sm" onClick={onBack}>
        {t('action.back')}
      </Button>

      <h1>
        {t('profile.heading')} · {member.displayLabel}
      </h1>

      {profileQuery.isPending && <Skeleton lines={5} label={t('loading.default')} />}

      {profileQuery.isError && !isNotFound && (
        <ErrorState error={profileQuery.error} onRetry={() => void profileQuery.refetch()} />
      )}

      {initialized && (
        <form className="family__form" onSubmit={submit}>
          {isNotFound && <p className="field__hint">{t('profile.noProfileYet')}</p>}

          <label className="field">
            <span className="field__label">{t('profile.ageBand')}</span>
            <select required value={ageBand} onChange={(e) => setAgeBand(e.target.value as AgeBand)}>
              <option value="">{t('profile.notSpecified')}</option>
              {ageBandOptions.map((band) => (
                <option key={band} value={band}>
                  {t(`ageBand.${band}`)}
                </option>
              ))}
            </select>
            <span className="field__hint">{t('profile.ageBandExplainer')}</span>
          </label>

          <label className="field">
            <span className="field__label">
              {t('profile.sex')} <span className="field__optional">{t('profile.sexOptional')}</span>
            </span>
            <select value={sex} onChange={(e) => setSex(e.target.value as Sex)}>
              <option value="">{t('profile.notSpecified')}</option>
              {SEX_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {t(`sex.${s}`)}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span className="field__label">{t('profile.dietaryPreference')}</span>
            <select
              value={dietaryPreference}
              onChange={(e) => setDietaryPreference(e.target.value as DietaryPreference)}
            >
              <option value="">{t('profile.notSpecified')}</option>
              {DIETARY_PREFERENCE_OPTIONS.map((d) => (
                <option key={d} value={d}>
                  {t(`diet.${d}`)}
                </option>
              ))}
            </select>
          </label>

          <fieldset className="field">
            <legend className="field__label">{t('profile.goals')}</legend>
            <p className="field__hint">{t('profile.goalsExplainer')}</p>
            <div className="profile__checks">
              {GOAL_CODES.map((g) => (
                <label key={g} className="profile__check">
                  <input type="checkbox" checked={goals.has(g)} onChange={() => toggleGoal(g)} />
                  {t(`goal.${g}`)}
                </label>
              ))}
            </div>
          </fieldset>

          <fieldset className="field">
            <legend className="field__label">{t('profile.allergens')}</legend>
            <div className="profile__checks">
              {ALLERGEN_CODES.map((code) => (
                <label key={code} className="profile__check">
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

          <label className="field">
            <span className="field__label">
              {t('profile.conditions')}{' '}
              <span className="field__optional">{t('profile.conditionsOptional')}</span>
            </span>
            <input
              type="text"
              value={conditionsText}
              placeholder={t('profile.conditionsPlaceholder')}
              onChange={(e) => setConditionsText(e.target.value)}
            />
            <span className="field__hint">{t('profile.conditionsHint')}</span>
          </label>

          {validationError && (
            <p className="form-error" role="alert">
              {validationError}
            </p>
          )}
          {saveErrorMessage && (
            <p className="form-error" role="alert">
              {saveErrorMessage}
            </p>
          )}
          {savedNotice && (
            <p className="profile__saved" role="status">
              {t('profile.savedNotice')}
            </p>
          )}

          <Button type="submit" fullWidth loading={saveMutation.isPending}>
            {t('action.save')}
          </Button>
        </form>
      )}
    </div>
  );
}
