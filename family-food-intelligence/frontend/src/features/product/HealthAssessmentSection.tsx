import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { getProductHealth } from '@/lib/scoring';
import type { NutrientRow } from '@/lib/catalog';
import { ReasonList } from '@/design-system/ReasonList';
import { ErrorState, Skeleton } from '@/design-system/States';
import './HealthAssessmentSection.css';

/**
 * The product-level, person-independent health assessment (D-003).
 *
 * Deliberately a collapsed `<details>`, below the per-person verdict strip,
 * never the largest element on the screen — the same discipline `ProductScreen`
 * already applies to the nutrition panel. A number here answers "how was this
 * product read overall", which is a different, narrower question than "does
 * this fit someone in my household", and must never crowd out that answer.
 *
 * `status === 'NOT_YET_SCORED'` is today's honest, universal result (D-024:
 * the active rule set carries zero score components) — rendered as a plain
 * fact, not an error.
 */
export function HealthAssessmentSection({
  productId,
  nutrients,
}: {
  productId: string;
  nutrients: NutrientRow[];
}) {
  const health = useQuery({
    queryKey: ['product-health', productId],
    queryFn: () => getProductHealth(productId),
  });

  return (
    <details className="health-assessment">
      <summary className="health-assessment__summary">{t('score.title')}</summary>
      <div className="health-assessment__body">
        {health.isPending && <Skeleton lines={2} label={t('loading.default')} />}
        {health.isError && (
          <ErrorState error={health.error} onRetry={() => void health.refetch()} />
        )}
        {health.isSuccess && (
          <>
            <p className="health-assessment__explainer">{t('score.explainer')}</p>
            {health.data.status === 'SCORED' && health.data.score != null ? (
              <p className="health-assessment__value">{t('score.value', { score: health.data.score })}</p>
            ) : (
              <p className="health-assessment__pending">{t('score.notYetScored')}</p>
            )}
            {health.data.reasons.length > 0 && (
              <>
                <p className="health-assessment__factors">{t('score.factors')}</p>
                <ReasonList reasons={health.data.reasons} nutrients={nutrients} />
              </>
            )}
            <p className="health-assessment__method">
              {t('score.methodology', { version: health.data.ruleSetVersion })}
            </p>
          </>
        )}
      </div>
    </details>
  );
}
