import type { ElementType, HTMLAttributes, ReactNode } from 'react';
import './Card.css';

interface CardProps extends HTMLAttributes<HTMLElement> {
  as?: ElementType;
  padding?: 'sm' | 'md' | 'lg';
  /** `raised` sits on `--card-bg` with a border — the default, page-level panel.
   *  `sunken` sits on `--surface` — a secondary block nested inside a raised one
   *  (e.g. one alternative inside the alternatives list). */
  surface?: 'raised' | 'sunken';
  elevated?: boolean;
  children: ReactNode;
}

/**
 * The one bordered-surface primitive.
 *
 * Before this component existed, every feature screen redeclared the same
 * five properties (padding/background/border/border-radius/gap) under its own
 * BEM class — `family__panel`, `settings__section`, `scanpage__panel`,
 * `alternatives__card` were four independent copies of the same rule. A
 * change to any of those five properties meant editing every feature's CSS
 * file by hand and hoping none were missed. This is that rule, defined once;
 * a feature's own class stays alongside it only for whatever is genuinely
 * specific to that screen.
 */
export function Card({
  as: Component = 'div',
  padding = 'md',
  surface = 'raised',
  elevated = false,
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <Component
      className={[
        'card',
        `card--${surface}`,
        `card--pad-${padding}`,
        elevated ? 'card--elevated' : '',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
      {...rest}
    >
      {children}
    </Component>
  );
}
