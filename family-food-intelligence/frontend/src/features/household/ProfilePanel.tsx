import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import type { HouseholdMember } from '@/lib/household';
import type { ConsentPolicy } from '@/lib/consent';
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
import { listGrants, listPolicies } from '@/lib/consent';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { memberInitial } from '@/design-system/HouseholdMemberCard';
import { Link } from 'react-router-dom';
import './FamilyScreen.css';

/**
 * Which purposes a guardian must grant before a child's profile may be stored.
 *
 * Derived from the policy list rather than hardcoded, using the same rule
 * `ConsentPanel` uses to decide which purposes to offer for a child: a purpose
 * that is not essential and requires verifiable consent is, by definition, the
 * child one. Today that resolves to exactly `CHILD_FOOD_SUITABILITY`; naming
 * that code here instead would have silently gated on a purpose that never
 * applies to a child — which is precisely the bug this replaced.
 */
function childPurposeCodes(policies: ConsentPolicy[]): string[] {
  return policies.filter((p) => !p.essential && p.requiresVerifiable).map((p) => p.purposeCode);
}

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
  const isChild = member.memberType === 'CHILD';

  /**
   * Consent before collection, for a child.
   *
   * "Add a child" promises: "We store an age band, allergens and dietary
   * preferences — nothing more — and only after you complete consent." The form
   * did not keep that promise: it saved a child's age band and allergens
   * immediately, and the product page then told the guardian "until then we
   * process nothing for them" — which was no longer true, because we had
   * already stored it.
   *
   * Consent already gated *computing a result*; this makes it gate *collecting
   * the data*, which is what the words say. An adult editing their own profile
   * is unaffected — they are the subject and the grantor.
   */
  const childConsent = useQuery({
    queryKey: ['consent', 'grants', member.id],
    queryFn: () => listGrants(member.id),
    enabled: isChild,
    retry: false,
  });

  const consentPolicies = useQuery({
    queryKey: ['consent', 'policies'],
    queryFn: listPolicies,
    enabled: isChild,
    staleTime: 30 * 60 * 1000,
  });

  const requiredPurposes = childPurposeCodes(consentPolicies.data ?? []);
  const activeGrants = new Set(
    (childConsent.data ?? []).filter((r) => r.status === 'ACTIVE').map((r) => r.purposeCode),
  );

  // Until both queries have answered we neither block nor allow on a guess:
  // `consentReady` keeps the form in its loading state rather than flashing a
  // gate at a guardian who has already given consent.
  const consentReady = !isChild || (childConsent.isSuccess && consentPolicies.isSuccess);
  const hasGuardianConsent =
    !isChild ||
    (consentReady &&
      requiredPurposes.length > 0 &&
      requiredPurposes.every((code) => activeGrants.has(code)));

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
    if (!hasGuardianConsent) {
      setValidationError(t('profile.childConsentRequired'));
      return;
    }
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

      <div className="profile__identity">
        <span
          className={[
            'family__avatar',
            member.memberType === 'CHILD' ? 'family__avatar--child' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          aria-hidden="true"
        >
          {memberInitial(member.displayLabel)}
        </span>
        <h1>
          {isChild
            ? t('household.profileOf', { name: member.displayLabel })
            : `${t('profile.heading')} · ${member.displayLabel}`}
        </h1>
      </div>

      {profileQuery.isPending && <Skeleton lines={5} label={t('loading.default')} />}

      {profileQuery.isError && !isNotFound && (
        <ErrorState error={profileQuery.error} onRetry={() => void profileQuery.refetch()} />
      )}

      {isChild && consentReady && !hasGuardianConsent && (
        <div className="profile__consent-gate" role="note">
          <p className="profile__consent-title">{t('profile.childConsentTitle')}</p>
          <p className="profile__consent-body">
            {t('profile.childConsentBody', { name: member.displayLabel })}
          </p>
          <Link to="/settings" className="btn btn--primary btn--sm">
            {t('profile.childConsentAction')}
          </Link>
        </div>
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

          <Button
            type="submit"
            fullWidth
            loading={saveMutation.isPending}
            disabled={!hasGuardianConsent}
          >
            {t('action.save')}
          </Button>
        </form>
      )}
    </div>
  );
}
