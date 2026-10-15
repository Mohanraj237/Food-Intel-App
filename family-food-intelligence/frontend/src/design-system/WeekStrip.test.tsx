import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WeekStrip } from './WeekStrip';

const days = ['2026-09-07', '2026-09-08', '2026-09-09', '2026-09-10', '2026-09-11', '2026-09-12', '2026-09-13'];

describe('WeekStrip', () => {
  it('is one image named by its title and described by its summary sentence', () => {
    render(
      <WeekStrip
        points={days.map((date) => ({ date, value: 1, label: date }))}
        aggregation="SUM"
        title="Water, last 7 days"
        summary="Water: logged on 7 of 7 days."
      />,
    );
    const img = screen.getByRole('img', { name: 'Water, last 7 days' });
    expect(img).toHaveAccessibleDescription('Water: logged on 7 of 7 days.');
  });

  it('draws no bar for a day nobody logged — a gap, not a zero', () => {
    const { container } = render(
      <WeekStrip
        points={days.map((date, i) => ({ date, value: i === 3 ? null : 2000, label: date }))}
        aggregation="SUM"
        title="t"
        summary="s"
      />,
    );
    expect(container.querySelectorAll('rect.week-strip__bar')).toHaveLength(6);
    const gap = container.querySelector('[data-day="2026-09-10"]');
    expect(gap?.getAttribute('data-logged')).toBe('false');
    expect(gap?.querySelector('.week-strip__bar')).toBeNull();
  });

  it('still draws a sliver for a recorded zero, so it never looks like "not logged"', () => {
    const { container } = render(
      <WeekStrip points={[{ date: days[0]!, value: 0, label: 'x' }]} aggregation="SUM" title="t" summary="s" />,
    );
    expect(container.querySelectorAll('rect.week-strip__bar')).toHaveLength(1);
  });

  it('draws a paired reading as two dots, the second hollow, and no goal line', () => {
    const { container } = render(
      <WeekStrip
        points={[{ date: days[0]!, value: 132, pair: 84, label: '132/84' }]}
        aggregation="LATEST"
        goalValue={120}
        title="t"
        summary="s"
      />,
    );
    expect(container.querySelectorAll('circle.week-strip__dot')).toHaveLength(2);
    expect(container.querySelectorAll('circle.week-strip__dot--hollow')).toHaveLength(1);
    expect(container.querySelector('.week-strip__goal')).toBeNull();
  });

  it('softens every day but the one being viewed', () => {
    const { container } = render(
      <WeekStrip
        points={days.map((date) => ({ date, value: 5, label: date }))}
        aggregation="SUM"
        selectedDate="2026-09-13"
        title="t"
        summary="s"
      />,
    );
    expect(container.querySelectorAll('.week-strip__mark--soft')).toHaveLength(6);
    expect(container.querySelector('[data-day="2026-09-13"]')?.classList.contains('week-strip__mark--soft')).toBe(false);
  });
});
