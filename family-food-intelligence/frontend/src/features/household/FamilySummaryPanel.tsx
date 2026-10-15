import { useId } from 'react';
import { t } from '@/lib/i18n';
import type { HouseholdOverview } from '@/lib/dashboard';
import { WeekStrip } from '@/design-system/WeekStrip';
import {
  familyDateLabel,
  includedNamesFor,
  mealsLineFor,
  summaryTilesFor,
  type DayContext,
} from './familyMetrics';
import './FamilySummaryPanel.css';

/**
 * The household at a glance (D-054): water, steps, sleep and habits across the people whose
 * records you can see, each with its week.
 *
 * <p>Built on the server from your own cards, so it holds nothing you couldn't already read on
 * one of them. It names whose records it is built from and says how many people each number
 * covers — a total is never mistaken for everyone's. There is no per-person split, no "most" and
 * no ranking here (D-051); the cards below are where each person is. Readings, body measurements
 * and mood are never combined, and it says so.
 */
export function FamilySummaryPanel({ overview, ctx }: { overview: HouseholdOverview; ctx: DayContext }) {
  const headingId = useId();
  const summary = overview.summary;
  if (!summary) return null;

  const tiles = summaryTilesFor(summary, overview, ctx);
  const meals = mealsLineFor(summary, ctx);

  return (
    <section className="fsum" aria-labelledby={headingId}>
      <header className="fsum__head">
        <h2 id={headingId} className="fsum__title">
          {ctx.viewingToday
            ? t('familySummary.headingToday')
            : t('familySummary.headingOn', { date: familyDateLabel(ctx.date) })}
        </h2>
        <p className="fsum__from">{t('familySummary.from', { names: includedNamesFor(summary, overview) })}</p>
      </header>

      <ul className="fsum__tiles" role="list">
        {tiles.map((tile) => (
          <li key={tile.key} className="fsum__tile">
            <span className="fsum__label">{tile.label}</span>
            <span className="fsum__value">{tile.valueText}</span>
            <span className="fsum__sub">{tile.subText}</span>
            <WeekStrip {...tile.strip} selectedDate={ctx.date} size="md" fluid />
          </li>
        ))}
      </ul>

      <footer className="fsum__foot">
        {meals && <p className="fsum__meals">{meals}</p>}
        <p className="fsum__never">{t('familySummary.never')}</p>
      </footer>
    </section>
  );
}
