import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { getProductHealth, type ProductHealth, type ScoreDriver } from '@/lib/scoring';
import { formatAmount, type NutrientRow } from '@/lib/catalog';
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
 * Under the v2 points method (D-059) the star rating is the primary derived
 * number and the 0–100 figure the clearly-labelled secondary rescale; both are
 * introduced as "based on the FSSAI draft Indian Nutrition Rating method", never
 * as the regulator's own rating (D-058). `NOT_YET_SCORED` (no active rule
 * content, D-024) and `INSUFFICIENT_DATA` (a label value is missing) are
 * rendered as plain facts, not errors.
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
        {health.isSuccess && <AssessmentBody health={health.data} nutrients={nutrients} />}
      </div>
    </details>
  );
}

function AssessmentBody({ health, nutrients }: { health: ProductHealth; nutrients: NutrientRow[] }) {
  const isV2 = health.ratingScheme === 'FSSAI_INR_DRAFT_2022_DERIVED';
  const drivers = health.drivers.filter((d) => !d.assumed || d.maxPoints > 0);
  // NO_ACTIVE_RULES is already said by the "not yet scored" line; its person-level copy
  // ("for this person") does not belong in this product-level section.
  const v1Reasons = health.reasons.filter((r) => r.code !== 'NO_ACTIVE_RULES');

  return (
    <>
      <p className="health-assessment__explainer">{t('score.explainer')}</p>

      {health.status === 'SCORED' && health.score != null && (
        <div className="health-assessment__result">
          {health.stars != null ? (
            <>
              <p className="health-assessment__value">{t('score.stars', { stars: formatAmount(health.stars) })}</p>
              <p className="health-assessment__secondary">{t('score.scoreOutOf100', { score: health.score })}</p>
            </>
          ) : (
            <p className="health-assessment__value">{t('score.scoreOutOf100', { score: health.score })}</p>
          )}
        </div>
      )}
      {health.status === 'NOT_YET_SCORED' && (
        <p className="health-assessment__pending">{t('score.notYetScored')}</p>
      )}
      {health.status === 'INSUFFICIENT_DATA' && (
        <p className="health-assessment__pending">
          {health.coverage.missing.length > 0
            ? t('score.insufficient', { missing: missingLabels(health.coverage.missing, nutrients) })
            : t('score.notApplicable')}
        </p>
      )}

      {drivers.length > 0 && (
        <>
          <p className="health-assessment__factors">{t('score.drivers')}</p>
          <ul className="health-assessment__drivers">
            {drivers.map((d) => (
              <li key={d.code} className="health-assessment__driver">
                {describeDriver(d, nutrients)}
              </li>
            ))}
          </ul>
        </>
      )}

      {!isV2 && v1Reasons.length > 0 && (
        <>
          <p className="health-assessment__factors">{t('score.factors')}</p>
          <ReasonList reasons={v1Reasons} nutrients={nutrients} />
        </>
      )}
      {isV2 && health.reasons.some((r) => r.code === 'SCORE_NOT_APPLICABLE' || r.code === 'MISSING_NUTRIENT_DATA') && (
        <ReasonList
          reasons={health.reasons.filter((r) => r.code === 'SCORE_NOT_APPLICABLE' || r.code === 'MISSING_NUTRIENT_DATA')}
          nutrients={nutrients}
        />
      )}

      {health.processingLevel && (
        <p className="health-assessment__processing">
          {t('score.processing', { group: health.processingLevel.novaGroup })}
          {health.processingLevel.isEstimated ? ` (${t('product.estimated')})` : ''}
        </p>
      )}

      <p className="health-assessment__method">
        {isV2 ? `${t('score.basedOn')} · ` : ''}
        {t('score.methodology', { version: health.ruleSetVersion })}
      </p>
    </>
  );
}

function describeDriver(driver: ScoreDriver, nutrients: NutrientRow[]): string {
  const row = nutrients.find((n) => n.nutrientCode === driver.nutrientCode);
  const name = row?.displayName ?? driver.nutrientCode;
  if (driver.assumed || driver.value == null) {
    return t('score.driverAssumed', { nutrient: name, max: driver.maxPoints });
  }
  const basis = driver.basis === 'PER_100ML' ? t('why.basis.per100ml') : t('why.basis.per100g');
  const line = t('score.driver', {
    nutrient: name,
    value: formatAmount(driver.value),
    unit: driver.unit ?? row?.unit ?? '',
    basis,
    points: driver.points,
    max: driver.maxPoints,
  });
  return driver.isEstimated ? `${line} (${t('product.estimated')})` : line;
}

function missingLabels(codes: string[], nutrients: NutrientRow[]): string {
  return codes
    .map((code) => nutrients.find((n) => n.nutrientCode === code)?.displayName ?? code)
    .join(', ');
}
