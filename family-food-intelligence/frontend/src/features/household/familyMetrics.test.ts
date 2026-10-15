import { describe, expect, it } from 'vitest';
import {
  AT_0705,
  AT_0940,
  DATE,
  ZONE,
  aarav,
  day,
  krishnan,
  meera,
  overview,
  raghav,
  summary,
  week,
} from '@/test/familyOverviewFixture';
import {
  bloodPressureToday,
  cardFooterFor,
  cardRowsFor,
  feedRowsFor,
  habitsRowFor,
  includedNamesFor,
  mealsLineFor,
  stripFor,
  summaryTilesFor,
  updatedTextFor,
  weekSentence,
  type DayContext,
} from './familyMetrics';

const today: DayContext = { date: DATE, timeZone: ZONE, viewingToday: true };
const pastDay: DayContext = { date: DATE, timeZone: ZONE, viewingToday: false };

describe('card rows', () => {
  it('shows up to four categories in spec order and names the rest', () => {
    const { rows, overflow } = cardRowsFor(raghav, today);
    expect(rows.map((r) => r.label)).toEqual(['Meals', 'Water', 'Activity', 'Sleep']);
    expect(overflow).toEqual(['Mood', 'Body']);
  });

  it('speaks values in the units a person uses', () => {
    const { rows } = cardRowsFor(raghav, today);
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r.valueText]));
    expect(byLabel.Water).toBe('2.2 L');
    expect(byLabel.Sleep).toBe('6 h 40 min');
    expect(byLabel.Activity).toBe('7200 steps');
  });

  it("counts a child's meals instead of headlining kcal (UXD-08)", () => {
    const { rows } = cardRowsFor(aarav, today);
    const meals = rows.find((r) => r.category === 'NUTRITION');
    expect(meals?.valueText).toBe('2 meals');
    expect(meals?.valueText).not.toMatch(/kcal/);
    expect(meals?.goal).toBeNull();
    expect(meals?.strip.points.map((p) => p.value)).toEqual([1, 1, null, 1, 1, 1, 2]);
  });

  it('pairs a blood pressure reading and gives it no goal', () => {
    const { rows } = cardRowsFor(krishnan, today);
    const readings = rows.find((r) => r.category === 'CLINICAL');
    expect(readings?.valueText).toBe('132/84 mmHg');
    expect(readings?.timeText).toBe('8:10 am');
    expect(readings?.goal).toBeNull();
    expect(readings?.strip.goalValue).toBeNull();
    expect(readings?.strip.aggregation).toBe('LATEST');
    expect(readings?.strip.points[0]).toMatchObject({ value: 138, pair: 88 });
  });

  it('says "not logged" rather than showing a zero, and changes wording for a past day', () => {
    const { rows } = cardRowsFor(krishnan, today);
    const activity = rows.find((r) => r.category === 'ACTIVITY');
    expect(activity?.logged).toBe(false);
    expect(activity?.valueText).toBe('Not logged today');
    expect(cardRowsFor(krishnan, pastDay).rows.find((r) => r.category === 'ACTIVITY')?.valueText).toBe(
      'Not logged that day',
    );
  });

  it('only shows categories the viewer can see', () => {
    const { rows } = cardRowsFor(meera, today);
    expect(rows.map((r) => r.category)).toEqual(['ACTIVITY']);
  });
});

describe('week strips', () => {
  it('keeps an unlogged day as a gap, never a zero', () => {
    const water = raghav.week.find((w) => w.code === 'WATER_ML');
    const strip = stripFor(raghav, water!, null);
    expect(strip.points[3]?.value).toBeNull();
    expect(strip.points[3]?.label).toMatch(/not logged/);
  });

  it('draws a goal line only for a daily summed goal', () => {
    const water = raghav.week.find((w) => w.code === 'WATER_ML')!;
    const daily = raghav.goals.find((g) => g.metricCode === 'WATER_ML')!;
    expect(stripFor(raghav, water, daily).goalValue).toBe(2500);
    expect(stripFor(raghav, water, { ...daily, period: 'WEEKLY' }).goalValue).toBeNull();
  });
});

describe('week sentences (spec §6.4)', () => {
  it('averages a summed metric over the days that were logged only', () => {
    const water = week('WATER_ML', 'HYDRATION', 'ml', 'SUM', [2000, null, 2000, null, 2000, null, 2000]);
    expect(weekSentence(water)).toBe('Water: logged on 4 of 7 days. Average on logged days: 2 L.');
  });

  it('counts a recorded zero as a logged day', () => {
    const steps = week('STEPS', 'ACTIVITY', 'steps', 'SUM', [0, null, null, null, null, null, 100]);
    expect(weekSentence(steps)).toBe('Steps: logged on 2 of 7 days. Average on logged days: 50 steps.');
  });

  it('gives a reading its count and latest value only — no average, no change', () => {
    const glucose = week('BLOOD_GLUCOSE_MGDL', 'CLINICAL', 'mg/dL', 'LATEST', [110, 120, null, null, null, null, 118]);
    const sentence = weekSentence(glucose);
    expect(sentence).toBe('Blood sugar (glucose): 3 readings. Latest 118 mg/dL on Sun 13 Sept.');
    expect(sentence).not.toMatch(/average|change/i);
  });

  it('states a body measurement as plain arithmetic change', () => {
    const weight = raghav.week.find((w) => w.code === 'WEIGHT_KG')!;
    expect(weekSentence(weight)).toMatch(/Change since Mon 7 Sept: −0\.8 kg\.$/);
  });

  it('says nothing was logged when the week is empty', () => {
    const empty = week('WATER_ML', 'HYDRATION', 'ml', 'SUM', [null, null, null, null, null, null, null]);
    expect(weekSentence(empty)).toBe('No water logged in the last 7 days.');
  });
});

describe('blood pressure pairing', () => {
  it('shows the newer number alone, labelled, when the two are not one reading', () => {
    const bp = bloodPressureToday([
      day('SYSTOLIC_MMHG', 'CLINICAL', 'mmHg', 'LATEST', 130, AT_0705),
      day('DIASTOLIC_MMHG', 'CLINICAL', 'mmHg', 'LATEST', 80, AT_0940),
    ]);
    expect(bp).toEqual({ text: '80 mmHg (bottom number)', at: AT_0940 });
  });

  it('is nothing at all when neither number was logged', () => {
    expect(bloodPressureToday([])).toBeNull();
  });
});

describe('card extras', () => {
  it('keeps habits as their own row with an honest denominator', () => {
    expect(habitsRowFor(raghav, today)?.valueText).toBe('1 of 1 done today');
    const nothingDue = { ...raghav, habits: { active: 1, dueToday: 0, doneToday: 1 } };
    expect(habitsRowFor(nothingDue, today)?.valueText).toBe('1 done today');
    // A weekday habit ticked on a Sunday is done but not due — never "2 of 1".
    const moreDoneThanDue = { ...raghav, habits: { active: 2, dueToday: 1, doneToday: 2 } };
    expect(habitsRowFor(moreDoneThanDue, today)?.valueText).toBe('2 done today');
  });

  it('says when the card was last updated, in the household zone', () => {
    expect(updatedTextFor(raghav, today)).toBe('Updated 9:40 am');
  });

  it('tells a caregiver what they can log, and a sharer that the rest is not shared', () => {
    expect(cardFooterFor(krishnan)).toBe('You can log: Water and Activity');
    expect(cardFooterFor(meera)).toBe("Other records aren't shared with you.");
    expect(cardFooterFor(raghav)).toBeNull();
  });
});

describe('latest updates feed', () => {
  const rows = feedRowsFor(overview());

  it('is ordered by when something was logged, newest first', () => {
    const times = rows.map((r) => r.at);
    expect([...times].sort((a, b) => b - a)).toEqual(times);
    expect(rows[0]?.timeText).toBe('9:40 am');
  });

  it('never includes someone who shares nothing', () => {
    expect(rows.some((r) => r.memberId === 'm-arjun' || r.memberId === 'm-ananya')).toBe(false);
  });

  it("pairs readings and counts a child's meals", () => {
    expect(rows.find((r) => r.memberId === 'm-krishnan' && r.label === 'Readings')?.valueText).toBe('132/84 mmHg');
    expect(rows.find((r) => r.memberId === 'm-aarav' && r.label === 'Meals')?.valueText).toBe('2 meals');
  });
});

describe('family summary', () => {
  const tiles = summaryTilesFor(summary, overview(), today);
  const byKey = Object.fromEntries(tiles.map((tile) => [tile.key, tile]));

  it('totals water and steps and says whose numbers they are', () => {
    expect(byKey.WATER_ML?.valueText).toBe('4.7 L');
    expect(byKey.WATER_ML?.subText).toBe('for 3 of 3 people');
    expect(byKey.STEPS?.subText).toBe('for 2 of 4 people');
  });

  it('averages sleep per person who logged it, and leaves a day nobody logged as a gap', () => {
    expect(byKey.SLEEP_MINUTES?.valueText).toBe('8 h');
    expect(byKey.SLEEP_MINUTES?.strip.points[3]?.value).toBeNull();
  });

  it('shows habits as done of due — and as a plain count when more were done than due', () => {
    expect(byKey.HABITS?.valueText).toBe('1 of 2 done');
    const sunday = { ...summary, habits: summary.habits!.map((d) => ({ ...d, due: 2, done: 3 })) };
    expect(summaryTilesFor(sunday, overview(), today).find((tile) => tile.key === 'HABITS')?.valueText).toBe('3 done');
  });

  it('counts people for meals, never kcal', () => {
    expect(mealsLineFor(summary, today)).toBe('Meals logged for 2 of 3 people');
  });

  it('names who is included, you first as "you"', () => {
    expect(includedNamesFor(summary, overview())).toBe('you, Aarav, Krishnan and Meera');
  });
});
