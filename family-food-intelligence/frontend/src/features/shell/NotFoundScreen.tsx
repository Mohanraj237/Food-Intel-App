import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { EmptyState } from '@/design-system/States';

export function NotFoundScreen() {
  return (
    <div className="page layout-list">
      <EmptyState
        icon="search"
        title={t('error.resource_not_found')}
        action={
          <Link to="/" className="btn btn--primary btn--sm">
            {t('action.backHome')}
          </Link>
        }
      />
    </div>
  );
}
