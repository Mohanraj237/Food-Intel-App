import { useId, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import type { HouseholdOverview } from '@/lib/dashboard';
import { Avatar } from '@/design-system/Avatar';
import { familyDateLabel, feedRowsFor, type DayContext } from './familyMetrics';
import './LatestUpdates.css';

const COLLAPSED_ROWS = 5;

/**
 * What the people you can see logged on the day, newest first — "a family group chat's status
 * view" (plan §18 3.1), derived from the overview already on screen, so it costs no request.
 *
 * <p>A timeline, never a leaderboard: rows are ordered by when something was logged, not by how
 * much (D-051). Readings appear as recorded, with no colour (D-037); a child's meals as a count.
 */
export function LatestUpdates({ overview, ctx }: { overview: HouseholdOverview; ctx: DayContext }) {
  const headingId = useId();
  const [expanded, setExpanded] = useState(false);
  const rows = useMemo(() => feedRowsFor(overview), [overview]);
  const visible = expanded ? rows : rows.slice(0, COLLAPSED_ROWS);
  const dateSuffix = ctx.viewingToday ? '' : `?date=${ctx.date}`;

  return (
    <section className="ffeed" aria-labelledby={headingId}>
      <h2 id={headingId} className="ffeed__title">
        {ctx.viewingToday ? t('familyFeed.headingToday') : t('familyFeed.headingOn', { date: familyDateLabel(ctx.date) })}
      </h2>

      {rows.length === 0 ? (
        <p className="ffeed__empty">{ctx.viewingToday ? t('familyFeed.emptyToday') : t('familyFeed.emptyOn')}</p>
      ) : (
        <ol className="ffeed__list" role="list">
          {visible.map((row) => (
            <li key={row.key}>
              <Link
                to={`/family/${row.memberId}${dateSuffix}`}
                className="ffeed__row"
                aria-label={t('familyFeed.rowLabel', {
                  name: row.name,
                  category: row.label,
                  value: row.valueText,
                  time: row.timeText,
                })}
              >
                <Avatar name={row.name} size="sm" />
                <span className="ffeed__body">
                  <span className="ffeed__who">{row.name}</span>
                  <span className="ffeed__what">
                    {row.label} · {row.valueText}
                  </span>
                </span>
                <time className="ffeed__time" dateTime={new Date(row.at).toISOString()}>
                  {row.timeText}
                </time>
              </Link>
            </li>
          ))}
        </ol>
      )}

      {rows.length > COLLAPSED_ROWS && (
        <button type="button" className="ffeed__more" aria-expanded={expanded} onClick={() => setExpanded((v) => !v)}>
          {expanded ? t('familyFeed.showFewer') : t('familyFeed.showAll', { count: rows.length })}
        </button>
      )}
    </section>
  );
}
