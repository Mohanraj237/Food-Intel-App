import type { ElementType, ReactNode } from 'react';
import './HouseholdMemberCard.css';

/**
 * A short, stable stand-in for a photo we will never have (no user-photo
 * upload feature). One character gives a household member a visual anchor
 * without pretending to be an avatar we don't have.
 *
 * Takes the first *grapheme*, not the first UTF-16 code unit. `charAt(0)` cuts
 * a surrogate pair in half, so a member named with an emoji or any character
 * outside the Basic Multilingual Plane rendered as U+FFFD — a literal broken
 * glyph where their initial should be. `Intl.Segmenter` also keeps a Devanagari
 * consonant together with its matra, which matters rather more here than the
 * emoji case that exposed the bug.
 */
export function memberInitial(label: string): string {
  const trimmed = label.trim();
  if (trimmed.length === 0) return '?';

  const first = firstGrapheme(trimmed);
  // Locale-aware uppercase: Turkish dotless i and similar. Emoji and scripts
  // without case are returned unchanged, which is what we want.
  return first.toLocaleUpperCase();
}

function firstGrapheme(text: string): string {
  // Available in every browser this product targets; the fallback keeps the
  // component safe in a test environment or an older engine without it.
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
    const [segment] = segmenter.segment(text);
    if (segment) return segment.segment;
  }
  return Array.from(text)[0] ?? text.charAt(0);
}

interface HouseholdMemberCardProps {
  name: string;
  roleLabel: string;
  isSelf?: boolean;
  isChild?: boolean;
  /** "You" — passed as text (via `t()`) so the card stays locale-agnostic. */
  youLabel?: string;
  actions?: ReactNode;
  as?: ElementType;
  className?: string;
}

/**
 * One row in "Your household." Previously hand-built twice (`FamilyScreen`'s
 * `MembersList` and `ProfilePanel`'s header) with the same avatar/name/role
 * anatomy diverging slightly each time. This is that anatomy, once — the
 * actions slot stays per-caller since which actions apply (leave, remove,
 * erase, edit profile) depends on who is looking and who the row is.
 */
export function HouseholdMemberCard({
  name,
  roleLabel,
  isSelf = false,
  isChild = false,
  youLabel,
  actions,
  as: Component = 'div',
  className,
}: HouseholdMemberCardProps) {
  return (
    <Component
      className={[
        'member-card',
        isSelf ? 'member-card--self' : '',
        isChild ? 'member-card--child' : '',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span
        className={[
          'member-card__avatar',
          isSelf ? 'member-card__avatar--self' : '',
          isChild ? 'member-card__avatar--child' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        aria-hidden="true"
      >
        {memberInitial(name)}
      </span>
      <div className="member-card__info">
        <span className="member-card__name">
          {name}
          {isSelf && youLabel && <span className="member-card__you">{youLabel}</span>}
        </span>
        <span className="member-card__role">{roleLabel}</span>
      </div>
      {actions && <div className="member-card__actions">{actions}</div>}
    </Component>
  );
}
