import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@/design-system/Card';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { ActivityFeedDay, ActivityFeedRow } from '@/design-system/ActivityFeedRow';
import { useMeQuery } from '@/lib/me';
import {
  isNotificationsEnabled,
  listNotifications,
  type NotificationItem,
} from '@/lib/notifications';
import { NotificationPreferencesSheet } from './NotificationPreferencesSheet';
import './NotificationsPanel.css';

/**
 * The in-app notification inbox (plan §3/§18 4.3): "a short list of plain sentences... not a
 * notification-settings matrix." Meant to sit inside `/next` (see `NextScreen.tsx`) — there is
 * no dedicated `/notifications` route in the plan's sitemap (§3), and none was pre-wired in
 * `App.tsx` for this pass, so this component is a self-contained panel rather than a screen.
 *
 * BACKEND DEPENDENCY REQUIRED (`lib/notifications.ts`, D-050) — gated the same way as the health
 * shelf: {@link isNotificationsEnabled} is false in every ordinary build.
 *
 * Owned by C5 (workstreams doc §1/§3).
 */
export function NotificationsPanel() {
  if (!isNotificationsEnabled()) {
    return (
      <Card as="div" className="notifications-panel__gate">
        <p className="notifications-panel__gate-badge">Not live yet</p>
        <p>
          An in-app inbox isn't offered in this build — the backend for it doesn't exist yet
          (D-050). This card previews the design against sample data once the feature flag is on.
        </p>
      </Card>
    );
  }
  return <NotificationsShell />;
}

function NotificationsShell() {
  const me = useMeQuery();
  const memberId = me.data?.member?.id ?? null;
  const memberType = me.data?.member?.memberType ?? null;
  const [preferencesOpen, setPreferencesOpen] = useState(false);

  const notifications = useQuery({
    queryKey: ['notifications', memberId, memberType],
    queryFn: () => listNotifications(memberId as string, memberType as 'ADULT' | 'CHILD'),
    enabled: memberId !== null && memberType !== null,
  });

  if (me.isPending) {
    return <Skeleton lines={3} label="Loading…" />;
  }

  if (memberType === 'CHILD') {
    // D-050/D-036, permanent: a child's own account is never addressed by a notification.
    return (
      <Card as="div">
        <p>Notifications aren't sent to a child's own account.</p>
      </Card>
    );
  }

  return (
    <div className="notifications-panel">
      <p className="notifications-panel__mocked-banner" role="status">
        MOCKED — these are sample notifications, not real ones. No message here was actually
        sent to you.
      </p>

      {notifications.isPending ? (
        <Skeleton lines={3} label="Loading…" />
      ) : notifications.isError ? (
        <ErrorState error={notifications.error} onRetry={() => void notifications.refetch()} />
      ) : notifications.data.length === 0 ? (
        <Card as="div">
          <p>Nothing to tell you yet.</p>
        </Card>
      ) : (
        <NotificationList items={notifications.data} />
      )}

      <Button variant="secondary" size="sm" onClick={() => setPreferencesOpen(true)}>
        Notification preferences
      </Button>

      {memberId && (
        <NotificationPreferencesSheet
          open={preferencesOpen}
          onClose={() => setPreferencesOpen(false)}
          memberId={memberId}
        />
      )}
    </div>
  );
}

function NotificationList({ items }: { items: readonly NotificationItem[] }) {
  const groups = groupByDay(items);
  return (
    <>
      {groups.map(({ heading, rows }) => (
        <ActivityFeedDay key={heading} heading={heading} headingLevel={3}>
          {rows.map((item) => (
            <ActivityFeedRow
              key={item.id}
              sentence={item.sentence}
              time={new Date(item.at).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
              {...(item.read ? {} : { glyph: '●' })}
            />
          ))}
        </ActivityFeedDay>
      ))}
    </>
  );
}

function groupByDay(items: readonly NotificationItem[]): Array<{ heading: string; rows: NotificationItem[] }> {
  const todayKey = new Date().toDateString();
  const byKey = new Map<string, NotificationItem[]>();
  for (const item of items) {
    const key = new Date(item.at).toDateString();
    const list = byKey.get(key) ?? [];
    list.push(item);
    byKey.set(key, list);
  }
  return Array.from(byKey.entries()).map(([key, rows]) => ({
    heading: key === todayKey ? 'Today' : new Date(rows[0]?.at ?? Date.now()).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }),
    rows,
  }));
}
