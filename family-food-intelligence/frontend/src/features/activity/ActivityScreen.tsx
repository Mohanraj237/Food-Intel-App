import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { getRecentActivity, type RecentActivityEntry } from '@/lib/activity';
import { ProductImage } from '@/design-system/ProductImage';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import './ActivityScreen.css';

/**
 * Activity — a plain history of what this account looked at, compared, and
 * decided (FR-012, D-005's self-only pattern; same `getRecentActivity` call
 * `HomeScreen`'s "recently viewed" rail already uses).
 *
 * Deliberately not a tracker or a dashboard — the same boundary `HomeScreen`'s
 * and `OutcomeCapture`'s own file-header comments already draw (D-005, Strategy
 * §"What this is not"). No streak, no points, no "days active", no completion
 * percentage, no behavioural-pressure copy. Every row is a real event this
 * account actually recorded.
 *
 * Grouped by *when* (Today / Yesterday / a date), not by event type — a
 * returning visitor thinks "what did I do recently," not "show me my VIEWED
 * events." Each row reads as a sentence ("You viewed this product") rather
 * than exposing the raw event-type vocabulary.
 */
export function ActivityScreen() {
  const signedIn = useIsSignedIn();

  const recent = useQuery({
    queryKey: ['activity', 'recent'],
    queryFn: getRecentActivity,
    enabled: signedIn,
  });

  if (!signedIn) {
    return (
      <div className="page">
        <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>
        <p>{t('activity.signInRequired' as Parameters<typeof t>[0])}</p>
        <Link to="/settings" className="btn btn--primary btn--md">
          {t('action.signIn')}
        </Link>
      </div>
    );
  }

  if (recent.isPending) {
    return (
      <div className="page">
        <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (recent.isError) {
    return (
      <div className="page">
        <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>
        <ErrorState error={recent.error} onRetry={() => void recent.refetch()} />
      </div>
    );
  }

  // Only entries with a real product survive — a sentence with no product to
  // show it about has nothing honest to anchor to (never a placeholder row).
  const entries = recent.data
    .filter((e) => e.product !== null)
    .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime());

  if (entries.length === 0) {
    return (
      <div className="page">
        <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>
        <EmptyState title={t('activity.empty' as Parameters<typeof t>[0])} />
      </div>
    );
  }

  const groups = groupByDay(entries);

  return (
    <div className="page">
      <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>

      {groups.map((group) => (
        <section key={group.label} className="activity__group" aria-labelledby={`day-${group.label}`}>
          <h2 id={`day-${group.label}`} className="activity__group-heading">
            {group.label}
          </h2>
          <ul className="activity__list">
            {group.entries.map((entry) => (
              <ActivityRow key={entry.id} entry={entry} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** Buckets already-sorted (newest-first) entries into same-day runs. Assumes
 *  the input is sorted — a run breaks the moment the calendar day changes, so
 *  an unsorted input would fragment into far more groups than days. */
function groupByDay(entries: RecentActivityEntry[]): Array<{ label: string; entries: RecentActivityEntry[] }> {
  const groups: Array<{ label: string; entries: RecentActivityEntry[] }> = [];
  for (const entry of entries) {
    const label = dayLabel(entry.occurredAt);
    const current = groups[groups.length - 1];
    if (current && current.label === label) {
      current.entries.push(entry);
    } else {
      groups.push({ label, entries: [entry] });
    }
  }
  return groups;
}

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

function dayLabel(iso: string): string {
  const oneDayMs = 24 * 60 * 60 * 1000;
  const entryDay = startOfDay(new Date(iso));
  const today = startOfDay(new Date());
  if (entryDay === today) return t('activity.today');
  if (entryDay === today - oneDayMs) return t('activity.yesterday');
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'long' }).format(new Date(iso));
}

function formatTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', { timeStyle: 'short' }).format(new Date(iso));
  } catch {
    return '';
  }
}

/** One event, as a sentence rather than a raw event-type tag. `COMPARED` and
 *  `BOUGHT_ALTERNATIVE` are the two event types that genuinely involve a
 *  second product — named inline in the sentence when we have it, rather than
 *  rendering a second product card the way the previous layout did. */
function describeEvent(entry: RecentActivityEntry): string {
  const altName = entry.alternativeProduct
    ? `${entry.alternativeProduct.brandName} ${entry.alternativeProduct.name}`
    : null;

  switch (entry.eventType) {
    case 'VIEWED':
      return t('activity.sentenceViewed');
    case 'SCANNED':
      return t('activity.sentenceScanned');
    case 'COMPARED':
      return altName ? t('activity.sentenceComparedWith', { name: altName }) : t('activity.sentenceCompared');
    case 'BOUGHT':
      return t('activity.sentenceBought');
    case 'BOUGHT_ALTERNATIVE':
      return altName
        ? t('activity.sentenceBoughtAlternativeWith', { name: altName })
        : t('activity.sentenceBoughtAlternative');
    case 'BOUGHT_NEITHER':
      return t('activity.sentenceBoughtNeither');
    case 'SKIPPED':
      return t('activity.sentenceSkipped');
    default:
      return assertUnreachable(entry.eventType);
  }
}

function assertUnreachable(value: never): string {
  void value;
  return '';
}

function ActivityRow({ entry }: { entry: RecentActivityEntry }) {
  const product = entry.product;
  if (!product) return null;

  return (
    <li className="activity__entry">
      <Link to={`/product/${product.id}`} className="activity__product">
        <ProductImage
          imageUrl={product.imageUrl}
          categoryCode={product.categoryCode}
          seed={product.id + product.name}
          alt=""
          classNames={{ img: 'activity__img', art: 'activity__art', glyph: 'activity__glyph' }}
        />
        <span className="activity__product-text">
          <span className="activity__product-name">
            {product.brandName} {product.name}
          </span>
          <span className="activity__sentence">{describeEvent(entry)}</span>
        </span>
      </Link>
      <span className="activity__time">{formatTime(entry.occurredAt)}</span>
    </li>
  );
}
