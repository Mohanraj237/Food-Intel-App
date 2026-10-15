import { Link, useLocation } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { MAX_COMPARE, clearTray, compareHref, removeFromTray, useTray } from '@/lib/compareTray';
import { ProductImage } from './ProductImage';
import { Icon } from './Icon';
import './CompareTrayBar.css';

/**
 * The compare tray (D-066): whenever at least one product has been added to compare, a bar
 * sits above the bottom navigation (phones) or at the bottom of the window (desktop) with the
 * picked products and one action — Compare. Hidden on the compare screen itself, which shows
 * the same products as columns.
 */
export function CompareTrayBar() {
  const items = useTray();
  const location = useLocation();
  if (items.length === 0 || location.pathname === '/compare') return null;

  const ready = items.length >= 2;

  return (
    <>
    {/* Keeps the last row of a page reachable above the fixed tray. */}
    <div className="tray-spacer" aria-hidden="true" />
    <aside className="tray" aria-label={t('compare.trayLabel')}>
      <div className="tray__inner">
        <ul className="tray__items" role="list">
          {items.map((item) => (
            <li key={item.id} className="tray__item">
              <ProductImage
                imageUrl={item.imageUrl}
                categoryCode={item.categoryCode}
                seed={item.id + item.name}
                alt=""
                classNames={{ img: 'tray__img', art: 'tray__img tray__img--art', glyph: 'tray__glyph' }}
              />
              <button
                type="button"
                className="tray__remove"
                aria-label={t('compare.removeNamed', { name: item.name })}
                onClick={() => removeFromTray(item.id)}
              >
                <Icon name="close" size={12} />
              </button>
            </li>
          ))}
          {Array.from({ length: Math.max(0, 2 - items.length) }, (_, i) => (
            <li key={`empty-${i}`} className="tray__item tray__item--empty" aria-hidden="true">
              <Icon name="plus" size={16} />
            </li>
          ))}
        </ul>
        <p className="tray__hint">
          {ready ? t('compare.trayCount', { count: items.length, max: MAX_COMPARE }) : t('compare.trayNeedOne')}
        </p>
        <div className="tray__actions">
          <button type="button" className="btn btn--ghost btn--sm" onClick={clearTray}>
            {t('compare.trayClear')}
          </button>
          {ready ? (
            <Link to={compareHref(items.map((i) => i.id))} className="btn btn--primary btn--sm">
              <Icon name="compare" size={16} />
              {t('compare.trayOpen', { count: items.length })}
            </Link>
          ) : (
            <span className="btn btn--primary btn--sm btn--disabled" aria-disabled="true">
              <Icon name="compare" size={16} />
              {t('compare.trayOpen', { count: items.length })}
            </span>
          )}
        </div>
      </div>
    </aside>
    </>
  );
}
