import type { MessageKey } from '@/lib/i18n';
import type { AccessLevel, SharingScope } from '@/lib/access';

/**
 * Message keys the sharing screens look up by enum value. Written out as literal maps rather
 * than built from template strings, so a missing key is a type error, not a raw key on screen.
 */

/** What a level means, as a choice ("See" / "See and log for you"). */
export const LEVEL_OPTION_KEYS: Record<AccessLevel, MessageKey> = {
  VIEW: 'access.level.VIEW',
  MANAGE: 'access.level.MANAGE',
};

/** One line under each level choice. */
export const LEVEL_HINT_KEYS: Record<AccessLevel, MessageKey> = {
  VIEW: 'sharing.levelHint.VIEW',
  MANAGE: 'sharing.levelHint.MANAGE',
};

/** A grant you made, as a status ("Can see" / "Can see and log for you"). */
export const LEVEL_STATUS_KEYS: Record<AccessLevel, MessageKey> = {
  VIEW: 'access.levelStatus.VIEW',
  MANAGE: 'access.levelStatus.MANAGE',
};

/** A grant made to you ("You can see" / "You can see and log for them"). */
export const LEVEL_INCOMING_KEYS: Record<AccessLevel, MessageKey> = {
  VIEW: 'access.levelIncoming.VIEW',
  MANAGE: 'access.levelIncoming.MANAGE',
};

/** What each area covers, in the words of the metrics it holds (MetricCode.java). */
export const SCOPE_HINT_KEYS: Record<SharingScope, MessageKey> = {
  ACTIVITY: 'sharing.scopeHint.ACTIVITY',
  SLEEP: 'sharing.scopeHint.SLEEP',
  HYDRATION: 'sharing.scopeHint.HYDRATION',
  NUTRITION: 'sharing.scopeHint.NUTRITION',
  BODY: 'sharing.scopeHint.BODY',
  CLINICAL: 'sharing.scopeHint.CLINICAL',
  WELLBEING: 'sharing.scopeHint.WELLBEING',
  HABITS: 'sharing.scopeHint.HABITS',
};

/** Non-colour cue for a level, beside its text. */
export const LEVEL_GLYPH: Record<AccessLevel, string> = {
  VIEW: '◉',
  MANAGE: '✎',
};
