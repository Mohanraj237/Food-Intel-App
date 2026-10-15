import { Link } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import { useMeQuery } from '@/lib/me';
import { dateKeyInZone } from '@/lib/access';
import { useTodaySummary, metricDayState } from '@/lib/dashboard';
import type { MetricCategory } from '@/lib/wellness';
import { formatMetricValue } from '@/lib/metricDisplay';
import { Icon, type IconName } from '@/design-system/Icon';
import './TodayGlance.css';

const GLANCE: ReadonlyArray<{ code: string; category: MetricCategory; labelKey: MessageKey; icon: IconName }> = [
  { code: 'ENERGY_KCAL', category: 'NUTRITION', labelKey: 'today.mealsLabel', icon: 'meal' },
  { code: 'WATER_ML', category: 'HYDRATION', labelKey: 'today.water', icon: 'water' },
  { code: 'STEPS', category: 'ACTIVITY', labelKey: 'today.activityLabel', icon: 'steps' },
  { code: 'SLEEP_MINUTES', category: 'SLEEP', labelKey: 'today.sleepLabel', icon: 'sleep' },
];

/**
 * "Your day so far" — four numbers from the same `/today` read the Today screen uses, as a
 * reason to come back to Home every day rather than only when shopping. Renders nothing until
 * the person has a household, an age band and tracking turned on; never a zero for "not
 * logged".
 */
export function TodayGlance() {
  const me = useMeQuery();
  const member = me.data?.member ?? null;
  const household = me.data?.household ?? null;
  const canShow =
    member !== null &&
    household !== null &&
    me.data?.capabilities.canTrack === true &&
    me.data?.trackingConsent.active === true;

  const todayKey = household ? dateKeyInZone(new Date(), household.timeZone) : undefined;
  const today = useTodaySummary(canShow ? member.id : null, todayKey);

  if (!canShow || !today.isSuccess) return null;

  return (
    <section className="glance" aria-labelledby="glance-heading">
      <div className="glance__head">
        <h2 id="glance-heading" className="glance__title">
          {t('home.glanceTitle')}
        </h2>
        <Link to="/today" className="glance__link">
          {t('home.glanceOpen')}
          <Icon name="chevron-right" size={16} />
        </Link>
      </div>
      <ul className="glance__list" role="list">
        {GLANCE.map((item) => {
          const state = metricDayState(today.data, item.code, item.category);
          const value = state.state === 'LOGGED' ? formatMetricValue(item.code, state.value, state.unit) : null;
          return (
            <li key={item.code} className={value ? 'glance__item' : 'glance__item glance__item--empty'}>
              <span className="glance__icon">
                <Icon name={item.icon} size={18} />
              </span>
              <span className="glance__text">
                <span className="glance__label">{t(item.labelKey)}</span>
                <span className="glance__value">{value ?? t('progressTile.notLogged')}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
