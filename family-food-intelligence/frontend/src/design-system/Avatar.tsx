import { memberInitial } from './HouseholdMemberCard';
import './Avatar.css';

/**
 * A person's initial on a warm tint. The tint is derived from the name so the same person
 * always gets the same colour across Family, Today, sharing and meal screens — a visual anchor
 * for "who" without a photo the product will never have.
 *
 * Six tints, all measured at ≥4.5:1 between ink and ground in both themes (tokens.css). The
 * hue is decorative; the initial and the name beside it carry the meaning.
 */
export function avatarTone(seed: string): 1 | 2 | 3 | 4 | 5 | 6 {
  let h = 5381;
  for (let i = 0; i < seed.length; i += 1) {
    h = ((h << 5) + h + seed.charCodeAt(i)) | 0;
  }
  return ((Math.abs(h) % 6) + 1) as 1 | 2 | 3 | 4 | 5 | 6;
}

interface AvatarProps {
  name: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  /** Pins the tint (e.g. `1` for "you" everywhere) instead of deriving it from the name. */
  tone?: 1 | 2 | 3 | 4 | 5 | 6 | undefined;
  /** Draws a dashed ring — a guardian-managed child. */
  child?: boolean | undefined;
  className?: string;
}

export function Avatar({ name, size = 'md', tone, child = false, className }: AvatarProps) {
  const resolved = tone ?? avatarTone(name);
  return (
    <span
      className={['avatar', `avatar--${size}`, `avatar--tone-${resolved}`, child ? 'avatar--child' : '', className ?? '']
        .filter(Boolean)
        .join(' ')}
      aria-hidden="true"
    >
      {memberInitial(name)}
    </span>
  );
}
