import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { goal } from '@/test/familyOverviewFixture';
import { GoalMeter, goalStatus } from './GoalMeter';

describe('goalStatus (spec §6.5)', () => {
  it('states an "at least" goal in spoken units and says "Reached" only when it is', () => {
    expect(goalStatus(goal('WATER_ML', 'ml', 2500, 2200))).toMatchObject({ status: '2.2 of 2.5 L', fact: null });
    expect(goalStatus(goal('WATER_ML', 'ml', 2500, 2600)).fact).toBe('Reached');
  });

  it('never turns "nothing logged" into a zero', () => {
    const s = goalStatus(goal('STEPS', 'steps', 8000, null));
    expect(s).toMatchObject({ status: 'Nothing logged today yet', fraction: null, none: true });
  });

  it('gives a child no "Reached" and no colour change', () => {
    const s = goalStatus(goal('WATER_ML', 'ml', 1200, 1300), { child: true });
    expect(s.status).toBe('1.3 L logged · aim: at least 1.2 L');
    expect(s.fact).toBeNull();
    expect(s.neutral).toBe(true);
  });

  it('keeps an "at most" bar neutral and states when it is above the number set', () => {
    const s = goalStatus(goal('SODIUM_MG', 'mg', 2000, 2400, { direction: 'AT_MOST' }));
    expect(s).toMatchObject({ fact: 'Above the number you set', neutral: true });
  });

  it('gives a body measurement no bar and no "to go"', () => {
    const s = goalStatus(goal('WEIGHT_KG', 'kg', 70, 72.4, { aggregation: 'LATEST', direction: 'MAINTAIN', period: 'WEEKLY' }));
    expect(s.fraction).toBeNull();
    expect(s.status).toBe('Latest reading: 72.4 kg');
  });
});

describe('GoalMeter', () => {
  it('hides the bar from assistive technology — the sentence carries the meaning', () => {
    const { container } = render(<GoalMeter progress={goal('STEPS', 'steps', 8000, 7200)} variant="compact" />);
    expect(screen.getByText('7200 of 8000 steps')).toBeInTheDocument();
    expect(container.querySelector('.goal-meter__bar')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('shows the full goal with its metric, window and target', () => {
    render(<GoalMeter progress={goal('SLEEP_MINUTES', 'min', 540, 400)} />);
    expect(screen.getByText('Sleep')).toBeInTheDocument();
    expect(screen.getByText('At least 9 h each day')).toBeInTheDocument();
    expect(screen.getByText('6 h 40 min of 9 h')).toBeInTheDocument();
  });
});
