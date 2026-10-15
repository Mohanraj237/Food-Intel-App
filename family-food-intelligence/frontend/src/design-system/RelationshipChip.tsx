import { t } from '@/lib/i18n';
import type { Relationship } from '@/lib/access';
import './RelationshipChip.css';

/**
 * A `Relationship` (D-044's `SELF`/`GUARDIAN`/`CAREGIVER`/`SHARED`/`NONE`), in plain English.
 *
 * The enum itself must never reach the screen (plan §2.4) — every caller that has one of these
 * values renders it through this chip rather than printing the wire value. Text-based, not
 * colour-alone (WCAG 1.4.1): the label carries the whole meaning, and the chip's background is
 * the same neutral surface for every value on purpose — a relationship is not a status to grade.
 */

const LABEL_KEYS: Record<Relationship, Parameters<typeof t>[0]> = {
  SELF: 'relationshipChip.SELF',
  GUARDIAN: 'relationshipChip.GUARDIAN',
  CAREGIVER: 'relationshipChip.CAREGIVER',
  SHARED: 'relationshipChip.SHARED',
  NONE: 'relationshipChip.NONE',
};

export function relationshipLabel(relationship: Relationship): string {
  return t(LABEL_KEYS[relationship]);
}

export function RelationshipChip({ relationship }: { relationship: Relationship }) {
  return <span className="relationship-chip">{relationshipLabel(relationship)}</span>;
}
