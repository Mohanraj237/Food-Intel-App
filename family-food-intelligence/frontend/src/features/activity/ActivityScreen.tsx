import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { getRecentActivity, type ActivityEventType, type RecentActivityEntry } from '@/lib/activity';
import type { ProductSummary } from '@/lib/catalog';
import { Card } from '@/design-system/Card';
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
 * account actually recorded, grouped by what it represents — never summarised
 * into a score, and never padded when the account has little or no history.
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

  const entries = recent.data;

  if (entries.length === 0) {
    return (
      <div className="page">
        <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>
        <EmptyState title={t('activity.empty' as Parameters<typeof t>[0])} />
      </div>
    );
  }

  // Real `eventType` values only, bucketed into the three things this screen
  // ever claims to show — never an invented category. An event type that
  // fits none of these (there is none today) would simply not render, the
  // same "no honest thing to show" posture used elsewhere in this codebase.
  const viewed = entries.filter((e) => e.eventType === 'VIEWED' || e.eventType === 'SCANNED');
  const compared = entries.filter((e) => e.eventType === 'COMPARED');
  const decisions = entries.filter(
    (e) =>
      e.eventType === 'BOUGHT' ||
      e.eventType === 'BOUGHT_ALTERNATIVE' ||
      e.eventType === 'BOUGHT_NEITHER' ||
      e.eventType === 'SKIPPED',
  );

  return (
    <div className="page">
      <h1>{t('activity.heading' as Parameters<typeof t>[0])}</h1>

      <ActivityGroup headingKey="activity.groupViewed" entries={viewed} />
      <ActivityGroup headingKey="activity.groupCompared" entries={compared} />
      <ActivityGroup headingKey="activity.groupDecisions" entries={decisions} />
    </div>
  );
}

/** Renders nothing at all when its bucket is empty — an empty group heading
 *  is worse than no group (same rule `HomeScreen`'s `Rail` applies to a
 *  catalogue rail with zero matches). */
function ActivityGroup({
  headingKey,
  entries,
}: {
  headingKey: string;
  entries: RecentActivityEntry[];
}) {
  if (entries.length === 0) return null;

  const headingId = `${headingKey}-heading`;

  return (
    <Card as="section" className="activity__group" aria-labelledby={headingId}>
      <h2 id={headingId} className="activity__group-heading">
        {t(headingKey as Parameters<typeof t>[0])}
      </h2>
      <ul className="activity__list">
        {entries.map((entry) => (
          <ActivityRow key={entry.id} entry={entry} />
        ))}
      </ul>
    </Card>
  );
}

/**
 * One event. `BOUGHT_ALTERNATIVE` and `COMPARED` are the two event types that
 * genuinely involve two products — both are shown, honestly, rather than
 * collapsing to just one side of the comparison or just the outcome.
 */
function ActivityRow({ entry }: { entry: RecentActivityEntry }) {
  const showAlternative =
    (entry.eventType === 'COMPARED' || entry.eventType === 'BOUGHT_ALTERNATIVE') &&
    entry.alternativeProduct !== null;
  const alternativeNote =
    entry.eventType === 'BOUGHT_ALTERNATIVE'
      ? t('activity.chosenInstead' as Parameters<typeof t>[0])
      : undefined;
  const decision = decisionLabel(entry.eventType);

  return (
    <Card as="li" surface="sunken" padding="sm" className="activity__entry">
      <div className="activity__entry-products">
        {entry.product && <EntryProduct product={entry.product} />}
        {showAlternative && entry.alternativeProduct && (
          <EntryProduct
            product={entry.alternativeProduct}
            {...(alternativeNote ? { note: alternativeNote } : {})}
          />
        )}
      </div>
      <div className="activity__entry-meta">
        {decision && <span className="activity__tag">{decision}</span>}
        {entry.eventType === 'SCANNED' && (
          <span className="activity__tag">{t('activity.scanned' as Parameters<typeof t>[0])}</span>
        )}
        <span className="activity__date">{formatDate(entry.occurredAt)}</span>
      </div>
    </Card>
  );
}

function EntryProduct({ product, note }: { product: ProductSummary; note?: string }) {
  return (
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
        {note && <span className="activity__product-note">{note}</span>}
      </span>
    </Link>
  );
}

/** The three "bought" outcomes plus "skipped" reuse `OutcomeCapture`'s own
 *  labels verbatim (`outcome.*`, already registered) — this is the same
 *  decision, just read back later, not a second vocabulary for it. */
function decisionLabel(eventType: ActivityEventType): string | null {
  switch (eventType) {
    case 'BOUGHT':
      return t('outcome.boughtOriginal');
    case 'BOUGHT_ALTERNATIVE':
      return t('outcome.boughtAlternative');
    case 'BOUGHT_NEITHER':
      return t('outcome.boughtNeither');
    case 'SKIPPED':
      return t('outcome.skip');
    default:
      return null;
  }
}

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(iso));
  } catch {
    return iso.slice(0, 10);
  }
}
