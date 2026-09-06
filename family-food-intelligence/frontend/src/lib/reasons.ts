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
  return { name: row?.displayName ?? code, unit: row?.unit ?? '' };
}

export function describeReason(reason: Reason, nutrients: NutrientRow[]): string[] {
  switch (reason.code) {
    case 'NUTRIENT_EXCEEDS_THRESHOLD': {
      if (reason.nutrientCode == null || reason.value == null || reason.basis == null) {
        return [t('why.noRules')];
      }
      const { name, unit } = nutrientLabel(reason.nutrientCode, nutrients);
      const basis = reason.basis === 'PER_SERVING' ? t('why.basis.perServing') : t('why.basis.per100g');
      const lines = [
        t('why.driver', { nutrient: name, value: formatAmount(reason.value), unit, basis }),
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
