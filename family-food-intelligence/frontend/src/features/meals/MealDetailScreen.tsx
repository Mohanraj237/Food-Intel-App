import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useMe } from '@/lib/useMe';
import { useAccountId } from '@/lib/useSession';
import { listMembers, type HouseholdMember } from '@/lib/household';
import { DASHBOARD_QUERY_KEYS } from '@/lib/dashboard';
import {
  confirmMealPortion,
  deleteMeal,
  getMeal,
  type MealPortion,
  type MealType,
  type NutritionAmounts,
} from '@/lib/nutritionLog';
import { formatAmount } from '@/lib/nutrition';
import { useDocumentTitle } from '@/lib/useDocumentTitle';
import { Button } from '@/design-system/Button';
import { ConfirmSheet } from '@/design-system/BottomSheet';
import { ErrorState, Skeleton } from '@/design-system/States';
import { Icon } from '@/design-system/Icon';
import './MealLogScreen.css';

const MEAL_TYPE_KEYS: Record<MealType, MessageKey> = {
  BREAKFAST: 'meal.type.BREAKFAST',
  LUNCH: 'meal.type.LUNCH',
  DINNER: 'meal.type.DINNER',
  SNACK: 'meal.type.SNACK',
};

/**
 * One logged meal, and whose record each share reached.
 *
 * <p>This is where D-041 becomes visible. Each person's share says plainly whether it is in
 * their own log yet. A share waiting on another adult is not an error and not a failure to
 * save — it is the product declining to write into someone's health record on somebody else's
 * behalf. If the person viewing is that adult, the screen offers them the one action that
 * resolves it.
 */
export function MealDetailScreen() {
  const { id = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const me = useMe();
  const accountId = useAccountId();
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const justLogged = (location.state as { justLogged?: boolean } | null)?.justLogged === true;

  const meal = useQuery({ queryKey: ['meals', 'detail', id], queryFn: () => getMeal(id), enabled: id !== '' });

  const members = useQuery({
    queryKey: ['household', 'members', me.householdId],
    queryFn: () => listMembers(me.householdId as string),
    enabled: me.householdId !== null,
  });

  useDocumentTitle(meal.data?.name ?? (meal.data ? t(MEAL_TYPE_KEYS[meal.data.mealType]) : t('meal.detailHeading')));

  const confirm = useMutation({
    mutationFn: () => confirmMealPortion(id),
    onSuccess: (updated) => {
      queryClient.setQueryData(['meals', 'detail', id], updated);
      void queryClient.invalidateQueries({ queryKey: ['wellness'] });
      void queryClient.invalidateQueries({ queryKey: ['insights'] });
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
      void queryClient.invalidateQueries({ queryKey: ['meals', 'member'] });
      void queryClient.invalidateQueries({ queryKey: ['meals', 'household'] });
      // Confirming a share changes that member's day totals on /today and /overview too.
      void queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEYS.all });
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteMeal(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['wellness'] });
      void queryClient.invalidateQueries({ queryKey: ['insights'] });
      void queryClient.invalidateQueries({ queryKey: ['meals'] });
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
      void queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEYS.all });
      navigate('/today', { replace: true });
    },
  });

  if (meal.isPending) {
    return (
      <div className="page mealpage layout-list">
        <Skeleton lines={5} label={t('loading.default')} />
      </div>
    );
  }

  if (meal.isError) {
    return (
      <div className="page mealpage layout-list">
        <h1>{t('meal.detailHeading')}</h1>
        <ErrorState error={meal.error} onRetry={() => void meal.refetch()} />
        <Link to="/today" className="btn btn--secondary btn--sm">{t('meal.backToToday')}</Link>
      </div>
    );
  }

  const m = meal.data;
  const memberOf = (memberId: string) => members.data?.find((x) => x.id === memberId);
  const nameOf = (memberId: string) => memberOf(memberId)?.displayLabel ?? t('meal.someone');
  // A share that has not reached its log is waiting on one of two people: the adult
  // themselves, or — for a child — a guardian of that child. Naming the wrong one would
  // send someone to press a button they do not have.
  const pendingLabel = (memberId: string) =>
    memberOf(memberId)?.memberType === 'CHILD'
      ? t('meal.childNotInLog', { name: nameOf(memberId) })
      : t('meal.waitingForThem', { name: nameOf(memberId) });
  const myShare = m.portions.find((p) => p.householdMemberId === me.memberId);
  const canConfirm = myShare !== undefined && !myShare.inMemberLog;
  // Only the logger may change or remove a meal (D-041). Offering Delete to anyone else
  // would be a control that always fails.
  const loggedByMe = m.loggedByUserId === accountId;
  const confirmError = confirm.error instanceof ApiError ? confirm.error.message : null;
  const deleteError = remove.error instanceof ApiError ? remove.error.message : null;

  return (
    <div className="page mealpage layout-list">
      <nav className="mealpage__crumbs">
        <Link to="/today" className="page-head__back">
          <Icon name="arrow-left" size={18} />
          {t('meal.backToToday')}
        </Link>
      </nav>

      {justLogged && (
        <p className="mealpage__success" role="status">{t('meal.savedMessage')}</p>
      )}

      <h1>{m.name ?? t(MEAL_TYPE_KEYS[m.mealType])}</h1>
      <p className="mealpage__meta">
        {/* The type is the title when the meal has no name; repeating it here says nothing. */}
        {m.name && <>{t(MEAL_TYPE_KEYS[m.mealType])} · </>}
        {new Date(m.consumedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
      </p>

      {canConfirm && (
        <div className="mealpage__confirm" role="note">
          <p>{t('meal.yourShareWaiting')}</p>
          <Button onClick={() => confirm.mutate()} loading={confirm.isPending}>
            {t('meal.addToMyLog')}
          </Button>
          {confirmError && <p className="form-error" role="alert">{confirmError}</p>}
        </div>
      )}

      <section className="mealpage__section" aria-labelledby="meal-shares">
        <h2 id="meal-shares" className="mealpage__h2">{t('meal.sharesHeading')}</h2>
        <ul className="shares" role="list">
          {selfFirst(m.portions, me.memberId).map((p) => (
            <li key={p.id} className="share">
              <div className="share__head">
                <span className="share__name">{nameOf(p.householdMemberId)}</span>
                <span className="share__fraction">
                  {Number(p.portionFraction) === 1
                    ? t('meal.shareWhole')
                    : t('meal.shareOf', { fraction: fractionLabel(p.portionFraction) })}
                </span>
              </div>
              <ShareNutrition portion={p} member={memberOf(p.householdMemberId)} />
              <p className={p.inMemberLog ? 'share__status share__status--in' : 'share__status share__status--waiting'}>
                {p.inMemberLog ? t('meal.inTheirLog') : pendingLabel(p.householdMemberId)}
              </p>
            </li>
          ))}
        </ul>
      </section>

      <section className="mealpage__section" aria-labelledby="meal-items">
        <h2 id="meal-items" className="mealpage__h2">{t('meal.itemsHeading')}</h2>
        <ul className="mealitems" role="list">
          {m.items.map((item) => (
            <li key={item.id} className="mealitems__row">
              <span className="mealitems__name">{item.customName ?? t('meal.catalogueItem')}</span>
              <span className="mealitems__qty">{formatAmount(item.quantityG)} g</span>
            </li>
          ))}
        </ul>
        <p className="mealform__hint">{t('meal.totalHeading')}</p>
        <MealTotal portions={m.portions} totalNutrition={m.totalNutrition} />
      </section>

      {deleteError && <p className="form-error" role="alert">{deleteError}</p>}
      {loggedByMe ? (
        <div className="mealform__actions">
          <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>{t('meal.delete')}</Button>
        </div>
      ) : (
        <p className="mealform__hint">{t('meal.onlyLoggerCanChange')}</p>
      )}

      <ConfirmSheet
        open={confirmingDelete}
        onClose={() => setConfirmingDelete(false)}
        onConfirm={() => { setConfirmingDelete(false); remove.mutate(); }}
        title={t('meal.deleteConfirmTitle')}
        body={t('meal.deleteConfirmBody')}
        confirmLabel={t('meal.delete')}
        tone="danger"
        loading={remove.isPending}
      />
    </div>
  );
}

/**
 * A share's nutrition, shown only to someone who may see that person's nutrition.
 *
 * <p>Anyone else's share is their NUTRITION data (D-038), and owning the household grants
 * nothing. That includes a child's share when the viewer is not that child's guardian. The
 * server already decides this per portion — {@link MealPortion.nutritionWithheld} — so this
 * renders that decision rather than asking a second endpoint and risking a different answer.
 * A share without values, when the viewer is allowed to see them, is shown as "not known",
 * which is a different fact from "withheld".
 */
function ShareNutrition({
  portion,
  member,
}: {
  portion: MealPortion;
  member: HouseholdMember | undefined;
}) {
  if (portion.nutritionWithheld) {
    const name = member?.displayLabel ?? t('meal.someone');
    return (
      <p className="nutriline nutriline--none">
        {member?.memberType === 'CHILD'
          ? t('meal.shareGuardianOnly', { name })
          : t('meal.sharePrivate', { name })}
      </p>
    );
  }

  return <NutritionLine amounts={portion.nutrition} />;
}

/**
 * The whole meal's total — hidden the moment any single portion is withheld from this viewer
 * (§18 2.3, the C2 acceptance checklist). The server always returns a true `totalNutrition`
 * regardless of who may see which share; showing it next to every *visible* share would let a
 * viewer subtract the shares they can see and reconstruct the one that was withheld from them,
 * which defeats the withholding as surely as showing the withheld share directly.
 */
function MealTotal({
  portions,
  totalNutrition,
}: {
  portions: MealPortion[];
  totalNutrition: NutritionAmounts;
}) {
  const anyWithheld = portions.some((p) => p.nutritionWithheld);
  if (anyWithheld) {
    return <p className="nutriline nutriline--none">{t('meal.totalWithheld')}</p>;
  }
  return <NutritionLine amounts={totalNutrition} />;
}

/**
 * The nutrients a share came to. Unknown values are left out rather than shown as zero;
 * when nothing at all is known, that is said in words.
 */
function NutritionLine({ amounts }: { amounts: NutritionAmounts }) {
  const parts: Array<[MessageKey, number | null, string]> = [
    ['today.energy', amounts.energyKcal, 'kcal'],
    ['today.protein', amounts.proteinG, 'g'],
    ['meal.carbs', amounts.carbohydrateG, 'g'],
    ['today.fibre', amounts.fibreG, 'g'],
    ['today.sodium', amounts.sodiumMg, 'mg'],
  ];
  const known = parts.filter(([, value]) => value !== null && value !== undefined);

  if (known.length === 0) {
    return <p className="nutriline nutriline--none">{t('meal.nutritionUnknown')}</p>;
  }

  return (
    <dl className="nutriline">
      {known.map(([labelKey, value, unit]) => (
        <div key={labelKey} className="nutriline__item">
          <dt>{t(labelKey)}</dt>
          <dd>{formatAmount(value as number)} {unit}</dd>
        </div>
      ))}
    </dl>
  );
}

/** The viewer's own share first: it is the one they came to check. */
function selfFirst(portions: MealPortion[], memberId: string | null): MealPortion[] {
  return [...portions].sort(
    (a, b) => Number(b.householdMemberId === memberId) - Number(a.householdMemberId === memberId),
  );
}

/** 0.5 reads as "half", not "0.5000". */
function fractionLabel(value: number): string {
  const known: Record<string, string> = { '0.25': '¼', '0.5': '½', '0.75': '¾', '1': '1', '1.5': '1½' };
  return known[String(Number(value))] ?? String(Number(value));
}
