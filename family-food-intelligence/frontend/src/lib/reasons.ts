import { t } from './i18n';
import { formatAmount, type NutrientRow } from './catalog';
import type { Reason } from './scoring';

/**
 * Turns a structured, deterministic `Reason` (never free text — D-011) into the
 * one or two lines of copy the "why" UI shows.
 *
 * A nutrient's display name and unit are read from the product's own
 * `nutrients` list rather than hard-coded here, so this never invents a label
 * the backend did not already provide. Falls back to the raw code only when
 * the product's nutrient list genuinely does not carry that code — a technical
 * token surfacing beats a fabricated English name.
 */
function nutrientLabel(code: string, nutrients: NutrientRow[]): { name: string; unit: string } {
  const row = nutrients.find((n) => n.nutrientCode === code);
  return { name: row?.displayName ?? derivedFactLabel(code), unit: row?.unit ?? '' };
}

/** Engine-derived facts (D-059) have no nutrient row; they carry their own labels. */
function derivedFactLabel(code: string): string {
  switch (code) {
    case 'INR_STARS':
      return t('why.fact.stars');
    case 'NOVA_GROUP':
      return t('why.fact.nova');
    default:
      return code;
  }
}

function basisLabel(basis: Reason['basis']): string {
  if (basis === 'PER_SERVING') return t('why.basis.perServing');
  if (basis === 'PER_100ML') return t('why.basis.per100ml');
  return t('why.basis.per100g');
}

export function describeReason(reason: Reason, nutrients: NutrientRow[]): string[] {
  switch (reason.code) {
    case 'NUTRIENT_EXCEEDS_THRESHOLD': {
      if (reason.nutrientCode == null || reason.value == null || reason.basis == null) {
        return [t('why.noRules')];
      }
      const { name, unit } = nutrientLabel(reason.nutrientCode, nutrients);
      const lines = [
        t('why.driver', { nutrient: name, value: formatAmount(reason.value), unit, basis: basisLabel(reason.basis) }),
      ];
      if (reason.threshold != null) {
        lines.push(
          t('why.threshold', {
            threshold: formatAmount(reason.threshold),
            unit,
            standard: reason.standardRef ?? '—',
          }),
        );
      }
      return lines;
    }
    case 'DERIVED_FACT_THRESHOLD': {
      if (reason.nutrientCode == null || reason.value == null) {
        return [t('why.noRules')];
      }
      const lines = [t('why.derived', { fact: derivedFactLabel(reason.nutrientCode), value: formatAmount(reason.value) })];
      if (reason.threshold != null) {
        lines.push(t('why.derivedThreshold', { threshold: formatAmount(reason.threshold), standard: reason.standardRef ?? '—' }));
      }
      return lines;
    }
    case 'POINTS_BAND': {
      if (reason.nutrientCode == null || reason.value == null) {
        return [];
      }
      const { name, unit } = nutrientLabel(reason.nutrientCode, nutrients);
      return [t('why.driver', { nutrient: name, value: formatAmount(reason.value), unit, basis: basisLabel(reason.basis) })];
    }
    case 'ASSUMED_ZERO': {
      const name = reason.nutrientCode ? nutrientLabel(reason.nutrientCode, nutrients).name : t('product.nutrition');
      return [t('why.assumedZero', { nutrient: name })];
    }
    case 'SCORE_NOT_APPLICABLE':
      return [t('why.notApplicable')];
    case 'MISSING_NUTRIENT_DATA': {
      const name = reason.nutrientCode ? nutrientLabel(reason.nutrientCode, nutrients).name : t('product.nutrition');
      return [t('why.missingData', { nutrient: name })];
    }
    case 'NO_GOALS_SET':
      return [t('why.noGoals')];
    case 'NO_ACTIVE_RULES':
    default:
      return [t('why.noRules')];
  }
}
