import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { getAlternatives, type Alternative, type AlternativeReason } from '@/lib/recommendation';
import type { HouseholdMember } from '@/lib/household';
import { Card } from '@/design-system/Card';
import { ErrorState, Skeleton } from '@/design-system/States';
import './AlternativesSection.css';

/**
 * "Better options" (D-013, FR-007) — position 5 in {@code ProductScreen}'s fixed
 * information order, right after the per-person fit it is built from.
 *
 * Every alternative shown is real: a same-category product that offers at
 * least one demonstrable improvement (never one that introduces a new
 * allergen risk — the backend excludes those before this component ever sees
 * them). An empty list is rendered as an honest fact, not hidden and not
 * padded with a same-category product that offers nothing (D-013's "no
 * better option in our data yet" is a first-class result).
 */
export function AlternativesSection({
  householdId,
  productId,
  members,
}: {
  householdId: string;
  productId: string;
  members: HouseholdMember[];
}) {
  const alternatives = useQuery({
    queryKey: ['alternatives', householdId, productId],
    queryFn: () => getAlternatives(householdId, productId),
  });

  const memberLabel = (memberId: string) =>
    members.find((m) => m.id === memberId)?.displayLabel ?? '';

  return (
    <section className="alternatives" aria-labelledby="alternatives-heading">
      <h2 id="alternatives-heading" className="pdp__h2">{t('alternatives.heading')}</h2>

      {alternatives.isPending && <Skeleton lines={2} label={t('loading.default')} />}
      {alternatives.isError && (
        <ErrorState error={alternatives.error} onRetry={() => void alternatives.refetch()} />
      )}
      {alternatives.isSuccess && alternatives.data.alternatives.length === 0 && (
        <p className="alternatives__empty">{t('alternatives.empty')}</p>
      )}
      {alternatives.isSuccess && alternatives.data.alternatives.length > 0 && (
        <ul className="alternatives__list">
          {alternatives.data.alternatives.map((alt) => (
            <AlternativeCard key={alt.productId} alternative={alt} memberLabel={memberLabel} />
          ))}
        </ul>
      )}
    </section>
  );
}

function AlternativeCard({
  alternative,
  memberLabel,
}: {
  alternative: Alternative;
  memberLabel: (memberId: string) => string;
}) {
  return (
    <Card as="li" surface="sunken" padding="sm" className="alternatives__card">
      <Link to={`/product/${alternative.productId}`} className="alternatives__name">
        {alternative.brandName ? `${alternative.brandName} — ${alternative.name}` : alternative.name}
      </Link>

      {alternative.betterOn.length > 0 && (
        <div className="alternatives__row alternatives__row--better">
          <ul>
            {alternative.betterOn.map((reason, i) => (
              <li key={i}>{t('alternatives.betterOn', { dimension: describeReasonDimension(reason, memberLabel) })}</li>
            ))}
          </ul>
        </div>
      )}

      {alternative.worseOn.length > 0 && (
        <div className="alternatives__row alternatives__row--worse">
          <ul>
            {alternative.worseOn.map((reason, i) => (
              <li key={i}>{t('alternatives.worseOn', { dimension: describeReasonDimension(reason, memberLabel) })}</li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

function describeReasonDimension(reason: AlternativeReason, memberLabel: (memberId: string) => string): string {
  const name = memberLabel(reason.memberId);
  switch (reason.code) {
    case 'SAFER_ALLERGENS':
      return t('alternatives.reason.saferAllergens', {
        name,
        allergen: reason.allergenCode ? t(`allergenCode.${reason.allergenCode}` as Parameters<typeof t>[0]) : '',
      });
    case 'NEW_ALLERGEN_RISK':
      return t('alternatives.reason.newAllergenRisk', {
        name,
        allergen: reason.allergenCode ? t(`allergenCode.${reason.allergenCode}` as Parameters<typeof t>[0]) : '',
      });
    case 'BETTER_VERDICT':
      return t('alternatives.reason.betterVerdict', { name });
    case 'WORSE_VERDICT':
      return t('alternatives.reason.worseVerdict', { name });
    default:
      return '';
  }
}
