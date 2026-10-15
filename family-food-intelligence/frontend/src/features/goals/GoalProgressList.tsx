import { useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { endGoal, goalProgress, type GoalProgress } from '@/lib/goals';
import { Button } from '@/design-system/Button';
import { ConfirmSheet } from '@/design-system/BottomSheet';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { GoalMeter } from './GoalMeter';
import './Goals.css';

/**
 * Where each active goal stands, from recorded data only.
 *
 * <p>The server returns `actual` only when something was logged in the window, and this
 * list keeps that distinction all the way to the screen: "nothing logged today" is a sentence,
 * never a zero on a bar. There is no score, ring, streak or badge. The only status words are
 * plain comparisons of the person's own number with the number they set.
 */
export function GoalProgressList({
  memberId,
  compact = false,
  emptyAction,
}: {
  memberId: string;
  /** Today's summary: no End action, shorter empty state. */
  compact?: boolean;
  emptyAction?: ReactNode;
}) {
  const queryClient = useQueryClient();
  const [ending, setEnding] = useState<GoalProgress | null>(null);

  const progress = useQuery({
    queryKey: ['goals', 'progress', memberId],
    queryFn: () => goalProgress(memberId),
  });

  const end = useMutation({
    mutationFn: (goalId: string) => endGoal(goalId),
    onSuccess: () => {
      setEnding(null);
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
    },
  });

  if (progress.isPending) return <Skeleton lines={2} label={t('loading.default')} />;
  if (progress.isError) {
    return <ErrorState error={progress.error} onRetry={() => void progress.refetch()} />;
  }
  if (progress.data.length === 0) {
    return <EmptyState title={compact ? t('goal.emptyCompact') : t('goal.empty')} action={emptyAction} />;
  }

  const endError = end.error instanceof ApiError ? end.error.message : null;

  return (
    <>
      <ul className="goals" role="list">
        {progress.data.map((p) => (
          <li key={p.goalId} className="goal">
            <GoalMeter progress={p} />
            {!compact && (
              <Button variant="ghost" size="sm" onClick={() => setEnding(p)}>
                {t('goal.end')}
              </Button>
            )}
          </li>
        ))}
      </ul>
      {endError && <p className="form-error" role="alert">{endError}</p>}
      <ConfirmSheet
        open={ending !== null}
        onClose={() => setEnding(null)}
        onConfirm={() => ending && end.mutate(ending.goalId)}
        title={t('goal.endConfirmTitle')}
        body={t('goal.endConfirmBody')}
        confirmLabel={t('goal.end')}
        loading={end.isPending}
      />
    </>
  );
}

