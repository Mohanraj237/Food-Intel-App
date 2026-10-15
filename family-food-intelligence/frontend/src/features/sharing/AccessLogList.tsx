import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import {
  ACCESS_QUERY_KEYS,
  dayHeading,
  describeLogEntry,
  entryTime,
  getAccessLog,
  groupAccessLogByDay,
  logRelationshipKey,
  type AccessLogEntry,
  type LogSubject,
} from '@/lib/access';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { ActivityFeedDay, ActivityFeedRow } from '@/design-system/ActivityFeedRow';

/** The server's default page. A full page means there may be older entries not shown. */
export const ACCESS_LOG_LIMIT = 100;

/** Not a colour: a shape per kind, beside a sentence that already says what happened — the
 *  decorative `glyph` slot `ActivityFeedRow` documents as never the only signal. */
const KIND_GLYPH: Record<AccessLogEntry['kind'], string> = {
  VIEWED: '◉',
  RECORDED: '✎',
  REFUSED: '⊘',
};

interface AccessLogListProps {
  memberId: string;
  subject: LogSubject;
  /** The household's zone, so "today" here is the same day as everywhere else in the product. */
  timeZone: string | undefined;
  emptyText: string;
  /** Heading level for the day groups, so the outline never skips a level. */
  dayHeadingLevel: 3 | 4;
}

/**
 * "Who has seen your data" — the access log as sentences, grouped by day (D-044 §6.4).
 *
 * Refused requests are listed alongside the rest: a refusal is the log doing its job, and
 * hiding it would make sharing look safer than it is.
 */
export function AccessLogList({ memberId, subject, timeZone, emptyText, dayHeadingLevel }: AccessLogListProps) {
  const log = useQuery({
    queryKey: ACCESS_QUERY_KEYS.log(memberId),
    queryFn: () => getAccessLog(memberId, { limit: ACCESS_LOG_LIMIT }),
  });

  if (log.isPending) {
    return <Skeleton lines={3} label={t('loading.default')} />;
  }
  if (log.isError) {
    return <ErrorState error={log.error} onRetry={() => void log.refetch()} />;
  }
  if (log.data.length === 0) {
    return <EmptyState title={emptyText} />;
  }

  const now = new Date();
  const days = groupAccessLogByDay(log.data, { now, ...(timeZone ? { timeZone } : {}) });

  return (
    <div className="access-log">
      {days.map((day) => (
        <ActivityFeedDay key={day.dateKey} heading={dayHeading(day, now, timeZone)} headingLevel={dayHeadingLevel}>
          {day.entries.map((entry) => (
            <AccessLogRow key={entry.id} entry={entry} subject={subject} timeZone={timeZone} />
          ))}
        </ActivityFeedDay>
      ))}
      {log.data.length >= ACCESS_LOG_LIMIT && (
        <p className="access-log__truncated">{t('sharing.log.truncated', { count: ACCESS_LOG_LIMIT })}</p>
      )}
    </div>
  );
}

function AccessLogRow({
  entry,
  subject,
  timeZone,
}: {
  entry: AccessLogEntry;
  subject: LogSubject;
  timeZone: string | undefined;
}) {
  const sentence = describeLogEntry(entry, subject);
  const via = logRelationshipKey(entry.relationship);
  const refused = entry.kind === 'REFUSED';
  const time = refused
    ? `${entryTime(entry.occurredAt, timeZone)} · ${t('access.log.refusedTag')}`
    : entryTime(entry.occurredAt, timeZone);

  return (
    <ActivityFeedRow
      glyph={KIND_GLYPH[entry.kind] ?? '•'}
      time={time}
      sentence={
        <>
          {t(sentence.messageKey, sentence.params)}
          {via && <> · {t(via)}</>}
        </>
      }
    />
  );
}
