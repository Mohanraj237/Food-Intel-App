import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import type { Relationship } from '@/lib/access';
import { Avatar } from './Avatar';
import { RelationshipChip } from './RelationshipChip';
import { Button } from './Button';
import './FamilyMemberCard.css';

export interface FamilyMemberCardRow {
  key: string;
  label: string;
  /** The day's value, or "Not logged today" — a sentence, never a zero. */
  valueText: string;
  /** Nothing logged on the day being viewed: drawn quieter, still stated. */
  muted?: boolean;
  /** A reading's time of day ("8:10 am"). */
  timeText?: string | null;
  /** The row's week at a glance — a `WeekStrip`. */
  chart?: ReactNode;
  /** Under the row — a compact goal meter. */
  detail?: ReactNode;
}

interface FamilyMemberCardProps {
  name: string;
  relationship: Relationship;
  /** "Updated 9:40 am". */
  updatedText?: string | null;
  /** Server order is preserved end to end: this component never re-sorts or re-picks rows. */
  rows?: readonly FamilyMemberCardRow[];
  /** Always its own row, never pushed off the card by the metric rows. */
  habits?: FamilyMemberCardRow | null;
  /** "Also: Body, Mood" — categories beyond the card's row limit, one tap away. */
  overflowText?: string | null;
  /** Shown instead of rows when the week holds nothing visible. */
  emptyText?: string | null;
  /** Why you see what you see: "You can log: Water, Activity" / "Other records aren't shared with you." */
  footer?: string | null;
  /** Present exactly when there's a member view to open — absent means identity-only. */
  openHref?: string | undefined;
  onLog?: (() => void) | undefined;
  logLabel?: string;
  openLabel?: string;
  /** A designed state replacing the rows, never blank or a raw error. */
  note?: string | undefined;
}

/**
 * One person on the Family hub (plan §8.2/§18 3.1, spec §4.4) — a feed of people, not a roster.
 * Every card has the same anatomy: who, their week in up to four rows, habits, why you can see
 * it, and what you can do. Each chart is on that person's own scale and is never lined up against
 * anyone else's (spec §6.7).
 *
 * <p>The card never becomes one giant `<Link>`: when a Log action exists alongside "Open", both
 * are their own control at the bottom rather than a button nested inside an anchor (invalid HTML,
 * and unreliable to tap).
 */
export function FamilyMemberCard({
  name,
  relationship,
  updatedText,
  rows = [],
  habits,
  overflowText,
  emptyText,
  footer,
  openHref,
  onLog,
  logLabel,
  openLabel,
  note,
}: FamilyMemberCardProps) {
  const allRows = habits ? [...rows, habits] : rows;

  return (
    <article className="family-member-card" aria-label={name}>
      <header className="family-member-card__head">
        <Avatar name={name} tone={relationship === 'SELF' ? 1 : undefined} child={relationship === 'GUARDIAN'} />
        <span className="family-member-card__who">
          <span className="family-member-card__name">{name}</span>
          <RelationshipChip relationship={relationship} />
        </span>
        {updatedText && <span className="family-member-card__updated">{updatedText}</span>}
      </header>

      {note ? (
        <p className="family-member-card__note">{note}</p>
      ) : (
        <>
          {allRows.length > 0 ? (
            <ul className="family-member-card__rows" role="list">
              {allRows.map((row) => (
                <li key={row.key} className="family-member-card__row">
                  <span className="family-member-card__label">{row.label}</span>
                  <span
                    className={
                      row.muted ? 'family-member-card__value family-member-card__value--muted' : 'family-member-card__value'
                    }
                  >
                    {row.valueText}
                    {row.timeText && <span className="family-member-card__time"> · {row.timeText}</span>}
                  </span>
                  <span className="family-member-card__chart">{row.chart}</span>
                  {row.detail && <div className="family-member-card__detail">{row.detail}</div>}
                </li>
              ))}
            </ul>
          ) : (
            emptyText && <p className="family-member-card__note">{emptyText}</p>
          )}
          {overflowText && openHref ? (
            <Link to={openHref} className="family-member-card__overflow">
              {overflowText}
            </Link>
          ) : (
            overflowText && <p className="family-member-card__overflow">{overflowText}</p>
          )}
          {footer && <p className="family-member-card__footer">{footer}</p>}
        </>
      )}

      {(onLog || openHref) && (
        <div className="family-member-card__actions">
          {onLog && (
            <Button variant="secondary" size="sm" onClick={onLog}>
              {logLabel ?? t('familyMemberCard.log')}
            </Button>
          )}
          {openHref && (
            <Link to={openHref} className="family-member-card__open" aria-label={`${openLabel ?? t('familyMemberCard.open')} ${name}`}>
              {openLabel ?? t('familyMemberCard.open')} <span aria-hidden="true">›</span>
            </Link>
          )}
        </div>
      )}
    </article>
  );
}
