import { t } from '@/lib/i18n';
import './VerdictChip.css';

/**
 * The closed verdict enum (D-004). Mirrors the Java enum exactly.
 *
 * `CONTAINS_ALLERGEN` is deliberately absent: an allergen is not a verdict. It has
 * its own component, its own colour and its own position at the top of the screen.
 * Keeping it out of this union is what stops it ever being rendered as one chip
 * among several, which would read as "one more consideration" rather than
 * "read this first".
 */
export type Verdict =
  | 'EVERYDAY'
  | 'OCCASIONAL'
  | 'LIMIT'
  | 'DOESNT_FIT'
  | 'INSUFFICIENT_DATA';

/**
 * Glyphs so the verdict is never carried by colour alone (AC-10.6, Pack 10 §6).
 * They are decorative in the accessibility tree — the text label is the real
 * content — but they give a colour-blind sighted user a second channel.
 */
const GLYPH: Record<Verdict, string> = {
  EVERYDAY: '●',
  OCCASIONAL: '◐',
  LIMIT: '⊖',
  DOESNT_FIT: '⊘',
  INSUFFICIENT_DATA: '?',
};

const MODIFIER: Record<Verdict, string> = {
  EVERYDAY: 'verdict-chip--everyday',
  OCCASIONAL: 'verdict-chip--occasional',
  LIMIT: 'verdict-chip--limit',
  DOESNT_FIT: 'verdict-chip--doesnt-fit',
  INSUFFICIENT_DATA: 'verdict-chip--insufficient',
};

interface VerdictChipProps {
  verdict: Verdict;
  /** Who this verdict is for. Omitted for a product-level chip. */
  memberLabel?: string;
  size?: 'sm' | 'md' | 'lg';
}

export function VerdictChip({ verdict, memberLabel, size = 'md' }: VerdictChipProps) {
  const label = t(`verdict.${verdict}` as Parameters<typeof t>[0]);

  return (
    <span
      className={`verdict-chip ${MODIFIER[verdict]} verdict-chip--${size}`}
      data-verdict={verdict}
    >
      <span className="verdict-chip__glyph" aria-hidden="true">
        {GLYPH[verdict]}
      </span>
      <span className="verdict-chip__text">
        {memberLabel ? (
          <>
            <span className="verdict-chip__member">{memberLabel}</span>
            <span className="verdict-chip__label">{label}</span>
          </>
        ) : (
          <span className="verdict-chip__label">{label}</span>
        )}
      </span>
    </span>
  );
}
