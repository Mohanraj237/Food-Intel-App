import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useMe } from '@/lib/useMe';
import { DASHBOARD_QUERY_KEYS } from '@/lib/dashboard';
import { confirmMealPortion, listMemberMeals, type Meal, type MealType } from '@/lib/nutritionLog';
import { formatAmount } from '@/lib/nutrition';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { Card } from '@/design-system/Card';
import { PageHeader } from '@/design-system/PageHeader';
import { Icon } from '@/design-system/Icon';
import { Button } from '@/design-system/Button';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import './MealHistoryScreen.css';

const MEAL_TYPE_KEYS: Record<MealType, MessageKey> = {
  BREAKFAST: 'meal.type.BREAKFAST',
  LUNCH: 'meal.type.LUNCH',
  DINNER: 'meal.type.DINNER',
  SNACK: 'meal.type.SNACK',
};

/**
 * `/meals` — a plain feed of logged meals, newest first, not a table (plan §18 Journey 2.3,
 * list archetype per §5.3: a single readable column, no split-pane, `--layout-list-max`).
 *
 * <p>`?filter=awaiting` narrows the same feed to meal shares that are the caller's own and not
 * yet in their log (`inMemberLog === false`, D-041) — a one-tap "add to my log" list rather than
 * a second screen, since the underlying data is the same `GET /nutrition/members/{id}/meals`
 * the plain history already reads.
 */
export function MealHistoryScreen() {
  const [params] = useSearchParams();
  const awaitingOnly = params.get('filter') === 'awaiting';
  useDocumentTitleKey(awaitingOnly ? 'meal.historyAwaitingHeading' : 'meal.historyHeading');

  const me = useMe();
  const queryClient = useQueryClient();

  const meals = useQuery({
    queryKey: ['meals', 'member', me.memberId],
    queryFn: () => listMemberMeals(me.memberId as string),
    enabled: me.memberId !== null,
  });

  const confirm = useMutation({
    mutationFn: (mealId: string) => confirmMealPortion(mealId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['meals', 'member', me.memberId] });
      void queryClient.invalidateQueries({ queryKey: ['meals', 'detail'] });
      void queryClient.invalidateQueries({ queryKey: ['wellness'] });
      void queryClient.invalidateQueries({ queryKey: ['insights'] });
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
      void queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEYS.all });
    },
  });

  if (me.isPending || (me.memberId !== null && meals.isPending)) {
    return (
      <div className="page mealhistory layout-list">
        <PageHeader title={t(awaitingOnly ? 'meal.historyAwaitingHeading' : 'meal.historyHeading')} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (me.householdId === null || me.memberId === null) {
    return (
      <div className="page mealhistory layout-list">
        <PageHeader title={t('meal.historyHeading')} />
        <p className="mealpage__lede">{t('today.needsHousehold')}</p>
        <Link to="/family" className="btn btn--primary btn--md">{t('household.create')}</Link>
      </div>
    );
  }

  if (meals.isError) {
    return (
      <div className="page mealhistory layout-list">
        <PageHeader title={t(awaitingOnly ? 'meal.historyAwaitingHeading' : 'meal.historyHeading')} />
        <ErrorState error={meals.error} onRetry={() => void meals.refetch()} />
      </div>
    );
  }

  const myMemberId = me.memberId;
  const rows = sortNewestFirst(meals.data ?? []);
  const visible = awaitingOnly
    ? rows.filter((meal) => meal.portions.some((p) => p.householdMemberId === myMemberId && !p.inMemberLog))
    : rows;

  return (
    <div className="page mealhistory layout-list">
      <PageHeader
        title={t(awaitingOnly ? 'meal.historyAwaitingHeading' : 'meal.historyHeading')}
        backTo="/today"
        backLabel={t('nav.today')}
        actions={
          <Link to="/meals/new" className="btn btn--primary btn--sm">
            <Icon name="plus" size={16} />
            {t('today.logMeal')}
          </Link>
        }
      />

      <div className="mealhistory__top">
        {awaitingOnly ? (
          <Link to="/meals" className="mealhistory__awaitingLink">{t('meal.historyHeading')}</Link>
        ) : (
          <Link to="/meals?filter=awaiting" className="mealhistory__awaitingLink">
            {t('meal.historyAwaitingLink')}
          </Link>
        )}
      </div>

      {visible.length === 0 ? (
        <EmptyState
          title={t(awaitingOnly ? 'meal.historyAwaitingEmpty' : 'meal.historyEmpty')}
          action={
            <Link to="/meals/new" className="btn btn--primary btn--md">{t('today.logMeal')}</Link>
          }
        />
      ) : (
        <ul className="mealhistory__list" role="list">
          {visible.map((meal) => {
            const myPortion = meal.portions.find((p) => p.householdMemberId === myMemberId);
            const awaitingThisOne = myPortion !== undefined && !myPortion.inMemberLog;
            const energyKcal = meal.totalNutrition.energyKcal;
            return (
              <li key={meal.id}>
                <Card className="mealhistory__row">
                  <Link to={`/meals/${meal.id}`} className="mealhistory__link">
                    <span className="mealhistory__name">
                      {meal.name ?? t(MEAL_TYPE_KEYS[meal.mealType])}
                    </span>
                    <span className="mealhistory__meta">
                      {meal.name && <>{t(MEAL_TYPE_KEYS[meal.mealType])} · </>}
                      {new Date(meal.consumedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
                      {typeof energyKcal === 'number' && <> · {formatAmount(energyKcal)} kcal</>}
                    </span>
                  </Link>
                  {awaitingThisOne && (
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={confirm.isPending && confirm.variables === meal.id}
                      onClick={() => confirm.mutate(meal.id)}
                    >
                      {t('meal.addToMyLog')}
                    </Button>
                  )}
                </Card>
                {confirm.isError && confirm.variables === meal.id && confirm.error instanceof ApiError && (
                  <p className="form-error" role="alert">{confirm.error.message}</p>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/** Newest first, defensively — a screen a person scans top-to-bottom must not depend on
 *  whatever order the response happened to arrive in. */
function sortNewestFirst(meals: readonly Meal[]): Meal[] {
  return [...meals].sort((a, b) => Date.parse(b.consumedAt) - Date.parse(a.consumedAt));
}
