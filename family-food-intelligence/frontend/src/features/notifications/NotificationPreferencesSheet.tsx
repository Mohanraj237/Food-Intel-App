import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BottomSheet } from '@/design-system/BottomSheet';
import { Switch } from '@/design-system/Switch';
import { RowGroup, Row } from '@/design-system/RowGroup';
import { Skeleton, ErrorState } from '@/design-system/States';
import {
  getNotificationPreferences,
  updateNotificationPreferences,
  type NotificationCategory,
  type NotificationPreferences,
} from '@/lib/notifications';
import './NotificationPreferencesSheet.css';

const CATEGORY_LABELS: Record<NotificationCategory, { label: string; description: string }> = {
  MEDICATION_REMINDER: { label: 'Medication reminders', description: 'On by default' },
  APPOINTMENT_REMINDER: { label: 'Appointment reminders', description: 'On by default' },
  GOAL_NUDGE: { label: 'Goal nudges', description: 'Off by default' },
  HABIT_NUDGE: { label: 'Habit nudges', description: 'Off by default' },
  FAMILY_ACTIVITY: { label: 'Family activity', description: 'Off by default' },
};

const CATEGORY_ORDER: readonly NotificationCategory[] = [
  'MEDICATION_REMINDER',
  'APPOINTMENT_REMINDER',
  'GOAL_NUDGE',
  'HABIT_NUDGE',
  'FAMILY_ACTIVITY',
];

/**
 * Preferences, one tap from the inbox (plan §18 4.3 wants this "one tap from You" — the closest
 * available anchor this pass is the inbox itself, since `SettingsScreen.tsx`/`/you` is C1's
 * file; see this workstream's final summary for that follow-up).
 *
 * Never mounted for a CHILD member — `NotificationsPanel.tsx`'s `NotificationsShell` returns
 * before rendering this sheet at all when the signed-in member is a child, so
 * {@link getNotificationPreferences} is always called with `'ADULT'` here, not because a child
 * could never have preferences, but because a child is never shown this door in the first place
 * (D-050/D-036).
 */
export function NotificationPreferencesSheet({
  open,
  onClose,
  memberId,
}: {
  open: boolean;
  onClose: () => void;
  memberId: string;
}) {
  const preferences = useQuery({
    queryKey: ['notification-preferences', memberId],
    queryFn: () => getNotificationPreferences(memberId, 'ADULT'),
    enabled: open,
  });

  const [local, setLocal] = useState<NotificationPreferences | null>(null);
  useEffect(() => {
    if (preferences.data) setLocal(preferences.data);
  }, [preferences.data]);

  const [savedNote, setSavedNote] = useState('');

  async function toggleCategory(category: NotificationCategory, checked: boolean) {
    if (!local) return;
    const next = { ...local, categories: { ...local.categories, [category]: checked } };
    setLocal(next);
    setSavedNote('');
    const saved = await updateNotificationPreferences(memberId, { categories: next.categories }, local);
    setLocal(saved);
    setSavedNote('Saved (to this preview only — nothing is sent to a server yet).');
  }

  return (
    <BottomSheet open={open} onClose={onClose} title="Notification preferences">
      <p role="status" aria-live="polite" className="sr-only">
        {savedNote}
      </p>
      {preferences.isPending ? (
        <Skeleton lines={4} label="Loading…" />
      ) : preferences.isError ? (
        <ErrorState error={preferences.error} onRetry={() => void preferences.refetch()} />
      ) : local ? (
        <>
          <RowGroup title="What you hear about">
            {CATEGORY_ORDER.map((category) => (
              <Row
                key={category}
                label={CATEGORY_LABELS[category].label}
                description={CATEGORY_LABELS[category].description}
                trailing={
                  <Switch
                    checked={local.categories[category]}
                    onChange={(checked) => void toggleCategory(category, checked)}
                    label={`${CATEGORY_LABELS[category].label}: ${local.categories[category] ? 'on' : 'off'}`}
                  />
                }
              />
            ))}
          </RowGroup>
          <RowGroup title="Quiet hours">
            <Row
              label="No notifications between"
              description={`${local.quietHoursStart}–${local.quietHoursEnd}`}
            />
            <Row label="At most, per day" description={`${local.dailyCap} notifications`} />
          </RowGroup>
          {savedNote && <p className="notification-preferences__saved">{savedNote}</p>}
        </>
      ) : null}
    </BottomSheet>
  );
}
