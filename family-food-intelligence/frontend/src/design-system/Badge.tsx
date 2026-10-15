import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import './Badge.css';

type BadgeTone = 'neutral' | 'brand' | 'accent' | 'allergen' | 'quality-good' | 'quality-warn' | 'quality-stale';

interface BadgeProps {
  children: ReactNode;
  tone?: BadgeTone;
  icon?: IconName;
  /** Larger padding for a badge that stands alone rather than inline. */
  size?: 'sm' | 'md';
}

/**
 * A small labelled tag — data quality, "Saved", a relationship. Text always carries the
 * meaning; the tone is a second channel, never the only one.
 */
export function Badge({ children, tone = 'neutral', icon, size = 'sm' }: BadgeProps) {
  return (
    <span className={`badge badge--${tone} badge--${size}`}>
      {icon && <Icon name={icon} size={size === 'sm' ? 14 : 16} />}
      <span>{children}</span>
    </span>
  );
}
