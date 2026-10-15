import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import type { NutrientRow } from '@/lib/catalog';
import type { ProductHealth } from '@/lib/scoring';
import { HealthAssessmentSection } from './HealthAssessmentSection';

const getProductHealth = vi.fn<(id: string) => Promise<ProductHealth>>();
vi.mock('@/lib/scoring', () => ({ getProductHealth: (id: string) => getProductHealth(id) }));

/**
 * The product-level assessment under both engines (D-003, D-058, D-059). The v2 case
 * cannot be seen live until rule set v2 is reviewed and activated, so this is where its
 * rendering is proved: stars first, the 0–100 as secondary, the draft-method attribution,
 * estimated and undeclared factors marked.
 */
const nutrients: NutrientRow[] = [
  row('ENERGY_KCAL', 'Energy', 'kcal'),
  row('TOTAL_SUGAR_G', 'Total sugar', 'g'),
  row('SAT_FAT_G', 'Saturated fat', 'g'),
  row('FVNL_PCT', 'Fruit, vegetable, nut and legume content', '%'),
  row('FIBRE_G', 'Dietary fibre', 'g'),
];

function row(nutrientCode: string, displayName: string, unit: string): NutrientRow {
  return { nutrientCode, displayName, amountPer100: 1, amountPerServing: null, unit, basisPer100: 'PER_100G', isEstimated: false, confidence: null, source: null };
}

function base(partial: Partial<ProductHealth>): ProductHealth {
  return {
    productVersionId: 'v', ruleSetVersion: 'v2', status: 'SCORED', score: null, grade: null, stars: null,
    ratingScheme: null, foodType: null, drivers: [], processingLevel: null,
    coverage: { required: [], present: [], missing: [] }, reasons: [], computedAt: '2026-09-15T00:00:00Z',
    ...partial,
  };
}

function renderOpen(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const { container } = render(<HealthAssessmentSection productId="p1" nutrients={nutrients} />, { wrapper });
  container.querySelector('details')?.setAttribute('open', '');
}

describe('HealthAssessmentSection', () => {
  beforeEach(() => getProductHealth.mockReset());

  it('leads with stars, labels the 0–100 as secondary and attributes the draft method', async () => {
    getProductHealth.mockResolvedValue(base({
      score: 22, grade: 'D', stars: 1.5, ratingScheme: 'FSSAI_INR_DRAFT_2022_DERIVED', foodType: 'SOLID',
      drivers: [
        { code: 'INR_SOLID_TOTALSUGAR', direction: 'NEGATIVE', nutrientCode: 'TOTAL_SUGAR_G', value: 25.5, unit: 'g', basis: 'PER_100G', points: 6, maxPoints: 20, threshold: 25.2, standardRef: 'FSSAI_INR', explanationKey: 'score.driver', isEstimated: false, assumed: false },
        { code: 'INR_SOLID_FV', direction: 'POSITIVE', nutrientCode: 'FVNL_PCT', value: 0, unit: '%', basis: 'PER_100G', points: 0, maxPoints: 10, threshold: null, standardRef: 'FSSAI_INR', explanationKey: 'score.driver', isEstimated: true, assumed: false },
        { code: 'INR_SOLID_FIBRE', direction: 'POSITIVE', nutrientCode: 'FIBRE_G', value: null, unit: null, basis: 'PER_100G', points: 0, maxPoints: 10, threshold: null, standardRef: 'FSSAI_INR', explanationKey: 'score.driver', isEstimated: false, assumed: true },
      ],
      processingLevel: { novaGroup: 4, isEstimated: true, source: 'OPEN_FOOD_FACTS' },
    }));
    renderOpen();

    const stars = await screen.findByText('1.5 of 5 stars');
    const secondary = screen.getByText('22 of 100');
    // Stars come before the 0–100 figure in the document (D-003: the derived number is not the headline).
    expect(stars.compareDocumentPosition(secondary) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByText(/Based on the FSSAI draft Indian Nutrition Rating method/)).toBeTruthy();
    expect(screen.getByText(/Total sugar: 25.5 g per 100 g — 6 of 20 points/)).toBeTruthy();
    expect(screen.getByText(/Fruit, vegetable, nut and legume content: 0 % per 100 g — 0 of 10 points \(estimated\)/)).toBeTruthy();
    expect(screen.getByText(/Dietary fibre: not declared on the label, counted as 0 of 10 points/)).toBeTruthy();
    expect(screen.getByText(/NOVA group 4 \(estimated\)/)).toBeTruthy();
  });

  it('names the missing label values when there is not enough data', async () => {
    getProductHealth.mockResolvedValue(base({
      status: 'INSUFFICIENT_DATA', ratingScheme: 'FSSAI_INR_DRAFT_2022_DERIVED',
      coverage: { required: ['SAT_FAT_G', 'ENERGY_KCAL'], present: ['ENERGY_KCAL'], missing: ['SAT_FAT_G'] },
      reasons: [{ code: 'MISSING_NUTRIENT_DATA', nutrientCode: 'SAT_FAT_G', basis: 'PER_100G', value: null, threshold: null, standardRef: 'FSSAI_INR' }],
    }));
    renderOpen();

    expect(await screen.findByText('Not enough label data to rate this product. Missing: Saturated fat.')).toBeTruthy();
    expect(screen.queryByText(/of 5 stars/)).toBeNull();
  });

  it('keeps the honest v1 state without person-level copy', async () => {
    getProductHealth.mockResolvedValue(base({
      status: 'NOT_YET_SCORED', ruleSetVersion: 'v1',
      reasons: [{ code: 'NO_ACTIVE_RULES', nutrientCode: null, basis: null, value: null, threshold: null, standardRef: null }],
    }));
    renderOpen();

    expect(await screen.findByText(/Not yet scored/)).toBeTruthy();
    expect(screen.queryByText(/for this person/)).toBeNull();
    expect(screen.queryByText(/FSSAI draft/)).toBeNull();
  });
});
