import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import {
  getAlternatives,
  getProductAlternatives,
  groupsOf,
  type Alternative,
  type AlternativeGroup,
  type AlternativeReason,
} from '@/lib/recommendation';
import type { HouseholdMember } from '@/lib/household';
import { formatAmount } from '@/lib/nutrition';
import { normaliseBrand } from '@/lib/productName';
import { Card } from '@/design-system/Card';
import { ErrorState, Skeleton } from '@/design-system/States';
import './AlternativesSection.css';

const GROUP_ORDER: AlternativeGroup[] = ['betterHouseholdFit', 'lowerSugar', 'lowerSodium', 'higherProtein', 'lessProcessed', 'higherRated'];

/**
 * "Better options" (D-013, D-060). Signed in with a household, it includes reasons about the
 * people the caller may see (allergens, verdicts); signed out, product-level reasons only
 * (rating band, points band, processing level). Every alternative shows what it is better on
 * and what it gives up. An empty list is an honest fact, never hidden and never padded.
 */
export function AlternativesSection({
  householdId,
  productId,
  members,
}: {
  householdId: string | null;
  productId: string;
  members: HouseholdMember[];
}) {
  const alternatives = useQuery({
    queryKey: ['alternatives', householdId, productId],
    queryFn: () => (householdId ? getAlternatives(householdId, productId) : getProductAlternatives(productId)),
  });

  const memberLabel = (memberId: string | null | undefined) =>
    members.find((m) => m.id === memberId)?.displayLabel ?? '';

  const list = alternatives.data?.alternatives ?? [];
  const groups = GROUP_ORDER.map((group) => ({ group, items: list.filter((a) => groupsOf(a).includes(group)) })).filter(
    (g) => g.items.length > 0,
  );
  const ungrouped = list.filter((a) => groupsOf(a).length === 0);

  return (
    <section className="alternatives" aria-labelledby="alternatives-heading">
      <h2 id="alternatives-heading" className="pdp__h2">{t('alternatives.heading')}</h2>

      {alternatives.isPending && <Skeleton lines={2} label={t('loading.default')} />}
      {alternatives.isError && <ErrorState error={alternatives.error} onRetry={() => void alternatives.refetch()} />}
      {alternatives.isSuccess && list.length === 0 && <p className="alternatives__empty">{t('alternatives.empty')}</p>}

      {groups.map(({ group, items }) => (
        <div key={group} className="alternatives__group">
          <h3 className="alternatives__group-title">{t(`alternatives.group.${group}` as MessageKey)}</h3>
          <ul className="alternatives__list">
            {items.map((alt) => (
              <AlternativeCard key={`${group}-${alt.productId}`} alternative={alt} memberLabel={memberLabel} />
            ))}
          </ul>
        </div>
      ))}
      {ungrouped.length > 0 && (
        <ul className="alternatives__list">
          {ungrouped.map((alt) => (
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
  memberLabel: (memberId: string | null | undefined) => string;
}) {
  const brand = normaliseBrand(alternative.brandName);
  return (
    <Card as="li" surface="sunken" padding="sm" className="alternatives__card">
      <Link to={`/product/${alternative.productId}`} className="alternatives__name">
        {brand ? `${brand} — ${alternative.name}` : alternative.name}
      </Link>
      {alternative.stars != null && (
        <p className="alternatives__rating">{t('card.rating', { stars: formatAmount(alternative.stars) })}</p>
      )}

      {alternative.betterOn.length > 0 && (
        <div className="alternatives__row alternatives__row--better">
          <ul>
            {alternative.betterOn.map((reason, i) => (
              <li key={i}>{t('alternatives.betterOn', { dimension: describeReason(reason, memberLabel) })}</li>
            ))}
          </ul>
        </div>
      )}

      {alternative.worseOn.length > 0 && (
        <div className="alternatives__row alternatives__row--worse">
          <ul>
            {alternative.worseOn.map((reason, i) => (
              <li key={i}>{t('alternatives.worseOn', { dimension: describeReason(reason, memberLabel) })}</li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

function nutrientLabel(code: string | null | undefined): string {
  return code ? t(`search.nutrient.${code}` as MessageKey) : '';
}

/** Structured reason → one short phrase; numbers only ever come from the reason itself (D-011). */
function describeReason(reason: AlternativeReason, memberLabel: (memberId: string | null | undefined) => string): string {
  const name = memberLabel(reason.memberId);
  const allergen = reason.allergenCode ? t(`allergenCode.${reason.allergenCode}` as MessageKey) : '';
  switch (reason.code) {
    case 'SAFER_ALLERGENS':
      return t('alternatives.reason.saferAllergens', { name, allergen });
    case 'NEW_ALLERGEN_RISK':
      return t('alternatives.reason.newAllergenRisk', { name, allergen });
    case 'BETTER_VERDICT':
      return t('alternatives.reason.betterVerdict', { name });
    case 'WORSE_VERDICT':
      return t('alternatives.reason.worseVerdict', { name });
    case 'HIGHER_RATING':
    case 'LOWER_RATING':
      return t(reason.code === 'HIGHER_RATING' ? 'alternatives.reason.higherRating' : 'alternatives.reason.lowerRating', {
        stars: reason.candidateValue == null ? '' : formatAmount(reason.candidateValue),
      });
    case 'LESS_PROCESSED':
    case 'MORE_PROCESSED':
      return t(reason.code === 'LESS_PROCESSED' ? 'alternatives.reason.lessProcessed' : 'alternatives.reason.moreProcessed', {
        group: reason.candidateValue ?? '',
        current: reason.currentValue ?? '',
      });
    default:
      if (reason.code.startsWith('LOWER_') || reason.code.startsWith('HIGHER_')) {
        return t(reason.code.startsWith('LOWER_') ? 'alternatives.reason.lowerNutrient' : 'alternatives.reason.higherNutrient', {
          nutrient: nutrientLabel(reason.nutrientCode).toLowerCase(),
          value: reason.candidateValue == null ? '' : formatAmount(reason.candidateValue),
          current: reason.currentValue == null ? '' : formatAmount(reason.currentValue),
        });
      }
      return '';
  }
}
