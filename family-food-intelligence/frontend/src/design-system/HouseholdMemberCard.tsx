import type { ElementType, ReactNode } from 'react';
import './HouseholdMemberCard.css';

/**
 * A short, stable stand-in for a photo we will never have (no user-photo
 * upload feature). One capital letter gives a household member a visual
 * anchor without pretending to be an avatar we don't have.
 */
function initial(label: string): string {
  const trimmed = label.trim();
  return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?';
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
        {initial(name)}
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
