/**
 * In-app notification inbox + preferences (plan §10.3, §18 Journey 4.3).
 *
 * MOCKED — BACKEND DEPENDENCY REQUIRED. Confirmed against the backend on 2026-09-12: the
 * `notifications` package does not exist — zero code, not even an empty controller. Every
 * function below is served from an in-memory fixture. **Nothing in this file makes a network
 * call.**
 *
 * Suggested contract to hand to backend (plan §10.3):
 *   GET /api/v1/me/notifications
 *   GET/PUT /api/v1/members/{id}/notification-preferences
 *
 * D-050 is encoded directly in the shapes and defaults below, not left as a comment for later:
 *   - per-category opt-in, quiet hours, and a daily cap all exist as real fields;
 *   - engagement categories (goal/habit nudges, family activity) default OFF;
 *   - medication and appointment reminders default ON;
 *   - a notification is never addressed to a CHILD member — {@link listNotifications} and
 *     {@link getNotificationPreferences} both refuse a CHILD `memberType` outright (an empty
 *     inbox and `null`, respectively) rather than trusting a caller to remember to filter.
 */

// --------------------------------------------------------------------------- types

export type NotificationCategory =
  | 'MEDICATION_REMINDER'
  | 'APPOINTMENT_REMINDER'
  | 'GOAL_NUDGE'
  | 'HABIT_NUDGE'
  | 'FAMILY_ACTIVITY';

export interface NotificationItem {
  id: string;
  category: NotificationCategory;
  /** A plain, already-composed sentence — "Your water goal resets in 2 hours" — never a raw
   *  template the client fills in with an invented number. */
  sentence: string;
  at: string;
  read: boolean;
}

export interface NotificationPreferences {
  memberId: string;
  categories: Record<NotificationCategory, boolean>;
  /** 24h "HH:mm", household-local time — no notification is sent inside this window. */
  quietHoursStart: string;
  quietHoursEnd: string;
  /** The most notifications this member can receive in a rolling day, across every category. */
  dailyCap: number;
}

export const NOTIFICATION_CATEGORY_DEFAULTS: Record<NotificationCategory, boolean> = {
  MEDICATION_REMINDER: true,
  APPOINTMENT_REMINDER: true,
  GOAL_NUDGE: false,
  HABIT_NUDGE: false,
  FAMILY_ACTIVITY: false,
};

function defaultPreferences(memberId: string): NotificationPreferences {
  return {
    memberId,
    categories: { ...NOTIFICATION_CATEGORY_DEFAULTS },
    quietHoursStart: '22:00',
    quietHoursEnd: '07:00',
    dailyCap: 3,
  };
}

// --------------------------------------------------------------------------- feature flag

const ENV_FLAG_KEY = 'VITE_FEATURE_NOTIFICATIONS';
const STORAGE_FLAG_KEY = 'ffi.flag.notifications';

/** Off by default — the same env-var-then-localStorage pattern as `lib/healthShelf.ts`'s
 *  `isHealthShelfEnabled`, duplicated here rather than shared so this module has no import-time
 *  dependency on another BACKEND DEPENDENCY REQUIRED module. */
export function isNotificationsEnabled(): boolean {
  const envValue = (import.meta.env as Record<string, string | boolean | undefined>)[ENV_FLAG_KEY];
  if (envValue === true || envValue === 'true') return true;
  try {
    return localStorage.getItem(STORAGE_FLAG_KEY) === '1';
  } catch {
    return false;
  }
}

// --------------------------------------------------------------------------- mock fixtures

const MOCK_LATENCY_MS = 350;

function mockDelay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), MOCK_LATENCY_MS));
}

function isoMinutesAgo(n: number): string {
  return new Date(Date.now() - n * 60_000).toISOString();
}

function fixtureItems(): NotificationItem[] {
  return [
    {
      id: 'mock-notif-1',
      category: 'APPOINTMENT_REMINDER',
      sentence: "Your appointment with Dr. Rao is on Friday.",
      at: isoMinutesAgo(40),
      read: false,
    },
    {
      id: 'mock-notif-2',
      category: 'MEDICATION_REMINDER',
      sentence: 'A dose of Metformin is due this evening.',
      at: isoMinutesAgo(180),
      read: false,
    },
    {
      id: 'mock-notif-3',
      category: 'HABIT_NUDGE',
      sentence: "You've kept up Walk after dinner most days this week.",
      at: isoMinutesAgo(60 * 20),
      read: true,
    },
  ];
}

/**
 * `memberType` mirrors the field already returned by `/me`/`/households/{id}/overview`
 * (`lib/me.ts`'s `MeMember.memberType`) — callers pass what they already have, this module
 * fetches nothing itself.
 */
export function listNotifications(
  _memberId: string,
  memberType: 'ADULT' | 'CHILD',
): Promise<NotificationItem[]> {
  if (memberType === 'CHILD') {
    // D-050/D-036: never addressed to a child. Enforced here, not left to the caller to
    // remember, so a screen that forgets the check still cannot show a child a notification.
    return mockDelay([]);
  }
  return mockDelay(fixtureItems());
}

export function getNotificationPreferences(
  memberId: string,
  memberType: 'ADULT' | 'CHILD',
): Promise<NotificationPreferences | null> {
  if (memberType === 'CHILD') {
    return mockDelay(null);
  }
  return mockDelay(defaultPreferences(memberId));
}

export function updateNotificationPreferences(
  _memberId: string,
  patch: Partial<Omit<NotificationPreferences, 'memberId'>>,
  current: NotificationPreferences,
): Promise<NotificationPreferences> {
  return mockDelay({
    ...current,
    ...patch,
    categories: { ...current.categories, ...(patch.categories ?? {}) },
  });
}
