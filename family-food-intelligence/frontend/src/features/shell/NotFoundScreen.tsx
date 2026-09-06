import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';

export function NotFoundScreen() {
  return (
    <div className="page">
      <h1>{t('error.resource_not_found')}</h1>
      <p>
        <Link to="/">{t('nav.home')}</Link>
      </p>
    </div>
  );
}
