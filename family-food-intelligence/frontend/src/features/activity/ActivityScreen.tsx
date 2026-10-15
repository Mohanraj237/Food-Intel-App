import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { getRecentActivity, type RecentActivityEntry } from '@/lib/activity';
import { displayProductName, normaliseBrand } from '@/lib/productName';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { ProductImage } from '@/design-system/ProductImage';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import { Icon } from '@/design-system/Icon';
import './ActivityScreen.css';

/**
 * `/history` — what this account looked at, compared and decided, grouped by day and written
 * as sentences. Not a tracker: no streaks, no counts, no completion.
 */
export function ActivityScreen() {
  useDocumentTitleKey('history.heading');
  const signedIn = useIsSignedIn();

  const recent = useQuery({
    queryKey: ['activity', 'recent'],
    queryFn: getRecentActivity,
    enabled: signedIn,
  });

  if (!signedIn) {
    return (
      <div className="page layout-list">
        <PageHeader title={t('history.heading')} lede={t('activity.signInRequired')} />
        <div>
          <Link to="/you" className="btn btn--primary btn--md">
            {t('action.signIn')}
          </Link>
        </div>
      </div>
    );
  }

  if (recent.isPending) {
    return (
      <div className="page layout-list">
        <PageHeader title={t('history.heading')} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (recent.isError) {
    return (
      <div className="page layout-list">
        <PageHeader title={t('history.heading')} />
        <ErrorState error={recent.error} onRetry={() => void recent.refetch()} />
      </div>
    );
  }

  const entries = recent.data
    .filter((e) => e.product !== null)
    .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime());

  if (entries.length === 0) {
    return (
      <div className="page layout-list">
        <PageHeader title={t('history.heading')} lede={t('history.lede')} />
        <EmptyState
          icon="history"
          title={t('activity.empty')}
          action={
            <Link to="/search" className="btn btn--primary btn--sm">
              {t('saved.browse')}
            </Link>
          }
        />
      </div>
    );
  }

  const groups = groupByDay(entries);

  return (
    <div className="page layout-list">
      <PageHeader title={t('history.heading')} lede={t('history.lede')} />

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

function describeEvent(entry: RecentActivityEntry): string {
  const altName = entry.alternativeProduct
    ? `${normaliseBrand(entry.alternativeProduct.brandName)} ${displayProductName(entry.alternativeProduct).text}`
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
      return altName ? t('activity.sentenceBoughtAlternativeWith', { name: altName }) : t('activity.sentenceBoughtAlternative');
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
          <span className="activity__brand">{normaliseBrand(product.brandName)}</span>
          <span className="activity__product-name">{displayProductName(product).text}</span>
          <span className="activity__sentence">{describeEvent(entry)}</span>
        </span>
        <span className="activity__time">{formatTime(entry.occurredAt)}</span>
        <Icon name="chevron-right" size={18} className="activity__chev" />
      </Link>
    </li>
  );
}
