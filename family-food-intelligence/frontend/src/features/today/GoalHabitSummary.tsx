import { Link } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import type { GoalProgress } from '@/lib/goals';
import type { HabitsDay } from '@/lib/dashboard';
import { EmptyState } from '@/design-system/States';
import { Icon } from '@/design-system/Icon';
import { GoalMeter, goalMetricLabel } from '@/features/goals/GoalMeter';
import './GoalHabitSummary.css';

/**
 * Today's read-only "what this person is working towards" card — fed straight from the `/today`
 * payload, with zero queries of its own. Each goal renders through the shared `GoalMeter`, so a
 * goal reads exactly the same here, on the Goals screen and on a Family card. No End / mark-done
 * action lives here; those are the full screens, one tap away via `goalsHref`/`habitsHref`.
 */

interface GoalHabitSummaryProps {
  goals: readonly GoalProgress[];
  habits: HabitsDay | null;
  goalsHref: string;
  habitsHref: string;
}

export function GoalHabitSummary({ goals, habits, goalsHref, habitsHref }: GoalHabitSummaryProps) {
  const hasGoals = goals.length > 0;
  const hasHabits = habits !== null && habits.active > 0;

  if (!hasGoals && !hasHabits) {
    return (
      <EmptyState
        title={t('today.goalHabitEmpty' as MessageKey)}
        action={
          <Link to={goalsHref} className="btn btn--secondary btn--sm">
            {t('goal.add')}
          </Link>
        }
      />
    );
  }

  return (
    <div className="goal-habit-summary">
      {hasGoals && (
        <div className="goal-habit-summary__group">
          <ul className="goal-habit-summary__list" role="list">
            {goals.map((goal) => (
              <li key={goal.goalId} className="goal-habit-summary__goal">
                <span className="goal-habit-summary__goal-head">
                  <Icon name="goal" size={16} />
                  <span className="goal-habit-summary__goal-label">{goalMetricLabel(goal.metricCode)}</span>
                </span>
                <GoalMeter progress={goal} variant="compact" />
              </li>
            ))}
          </ul>
          <Link to={goalsHref} className="goal-habit-summary__more">
            {t('goal.seeAll' as MessageKey)}
          </Link>
        </div>
      )}

      {hasHabits && habits && (
        <div className="goal-habit-summary__group">
          <p className="goal-habit-summary__line">
            <Icon name="habit" size={16} />
            {t('habit.summaryToday' as MessageKey, { done: habits.doneToday, due: habits.dueToday })}
          </p>
          <Link to={habitsHref} className="goal-habit-summary__more">
            {t('habit.seeAll' as MessageKey)}
          </Link>
        </div>
      )}
    </div>
  );
}

