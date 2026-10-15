import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { useSaved } from '@/lib/saved';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { PageHeader } from '@/design-system/PageHeader';
import { ProductGrid } from '@/design-system/Rails';
import { EmptyState } from '@/design-system/States';
import { Icon } from '@/design-system/Icon';
import './SavedScreen.css';

/**
 * `/saved` — the shortlist. Device-local by design (lib/saved.ts), and the screen says so.
 */
export function SavedScreen() {
  useDocumentTitleKey('saved.heading');
  const saved = useSaved();

  return (
    <div className="page layout-dashboard saved">
      <PageHeader title={t('saved.heading')} lede={t('saved.lede')} />

      {saved.length === 0 ? (
        <EmptyState
          icon="heart"
          title={t('saved.empty')}
          body={t('saved.emptyBody')}
          action={
            <Link to="/search" className="btn btn--primary btn--sm">
              {t('saved.browse')}
            </Link>
          }
        />
      ) : (
        <>
          <ProductGrid
            products={saved.map((p) => ({
              id: p.id,
              brandName: p.brandName,
              name: p.name,
              packSize: p.packSize,
              categoryCode: p.categoryCode,
              categoryName: p.categoryName,
              imageUrl: p.imageUrl,
            }))}
          />
          <p className="saved__note">
            <Icon name="lock" size={16} />
            {t('saved.deviceNote')}
          </p>
        </>
      )}
    </div>
  );
}
