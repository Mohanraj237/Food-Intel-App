import { Link } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import { Skeleton } from '@/design-system/States';
import { useHouseholdOverview, type BlockerKind, type MemberReadiness } from './useHouseholdOverview';
import './HouseholdOverview.css';

/**
 * "How is my household doing?" — answered in the terms this product can
 * actually answer it.
 *
 * The roster below this used to be the whole screen: a list of names with a
 * "Set up profile" button. That told a user *who* is in the household and
 * nothing about whether the household is in a state where the product works.
 * The gap that caused was concrete — a guardian who sees "Not enough data yet"
 * on a product page had no way to find out that the reason was a missing age
 * band or an ungranted consent two screens away.
 *
 * So the overview leads with readiness, then with the two facts a person
 * shopping for this household actually needs: what everyone is watching, and
 * which allergens are in play across all of them.
 *
 * What it deliberately does **not** show: weight, BMI, body composition,
 * calorie targets, step counts, sleep, streaks or leaderboards. Those are not
 * missing features — the product does not collect them, by decision (D-017),
 * and for a child they are a permanent boundary rather than a backlog item
 * (`docs/product/post-mvp-backlog.md`). A dashboard implying otherwise would be
 * inventing data.
 */
export function HouseholdOverview({ householdId }: { householdId: string }) {
  const overview = useHouseholdOverview(householdId);

  if (overview.isPending) {
    return (
      <section className="hoverview" aria-labelledby="hoverview-heading">
        <h2 id="hoverview-heading" className="hoverview__heading">
          {t('overview.heading')}
        </h2>
        <Skeleton lines={3} label={t('loading.default')} />
      </section>
    );
  }

  // Errors are not surfaced as a banner here. The roster underneath has its own
  // error handling for the same members query, and two error boxes for one
  // failure reads as two problems.
  if (overview.isError || overview.totalCount === 0) {
    return null;
  }

  const { readyCount, totalCount, needsAttention, sharedGoals, householdAllergens } = overview;
  const allReady = readyCount === totalCount;

  return (
    <section className="hoverview" aria-labelledby="hoverview-heading">
      <h2 id="hoverview-heading" className="hoverview__heading">
        {t('overview.heading')}
      </h2>

      {/* --- readiness: the one number that predicts whether the product works --- */}
      <div className={`hoverview__status hoverview__status--${allReady ? 'ready' : 'attention'}`}>
        <p className="hoverview__status-line">
          {allReady
            ? t('overview.allReady', { count: totalCount })
            : t('overview.someReady', { ready: readyCount, total: totalCount })}
        </p>
        <p className="hoverview__status-sub">
          {allReady ? t('overview.allReadySub') : t('overview.someReadySub')}
        </p>
      </div>

      {needsAttention.length > 0 && (
        <ul className="hoverview__blockers" role="list">
          {needsAttention.map((entry) => (
            <BlockerRow key={entry.member.id} entry={entry} />
          ))}
        </ul>
      )}

      {/* --- the two facts that matter in a shop --- */}
      <div className="hoverview__facts">
        <div className="hoverview__fact">
          <h3 className="hoverview__fact-title">{t('overview.watchingHeading')}</h3>
          {sharedGoals.length === 0 ? (
            <p className="hoverview__fact-empty">{t('overview.watchingEmpty')}</p>
          ) : (
            <ul className="hoverview__taglist" role="list">
              {sharedGoals.map(({ code, count }) => (
                <li key={code} className="hoverview__tag">
                  {t(`goal.${code}` as MessageKey)}
                  {count > 1 && (
                    <span className="hoverview__tag-count">
                      {t('overview.sharedByCount', { count })}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="hoverview__fact">
          <h3 className="hoverview__fact-title">{t('overview.allergensHeading')}</h3>
          {householdAllergens.length === 0 ? (
            <p className="hoverview__fact-empty">{t('overview.allergensEmpty')}</p>
          ) : (
            <>
              <ul className="hoverview__taglist" role="list">
                {householdAllergens.map((code) => (
                  <li key={code} className="hoverview__tag hoverview__tag--allergen">
                    {t(`allergenCode.${code}` as MessageKey)}
                  </li>
                ))}
              </ul>
              {/* The same caveat the product screen carries. An allergen list is
                  only ever as good as the label it came from. */}
              <p className="hoverview__fact-note">{t('overview.allergensNote')}</p>
            </>
          )}
        </div>
      </div>
    </section>
  );
}

/**
 * One thing standing between a member and a real result, with the action that
 * resolves it. Every row is a link, because "Aarav needs consent" without a
 * route to the consent screen is the problem restated, not solved.
 */
function BlockerRow({ entry }: { entry: MemberReadiness }) {
  const { member, blocker } = entry;
  const name = member.displayLabel;

  const copy: Record<Exclude<BlockerKind, 'PRIVATE'>, { key: MessageKey; to: string; cta: MessageKey }> = {
    CONSENT_PENDING: {
      key: member.memberType === 'CHILD' ? 'overview.blockerChildConsent' : 'overview.blockerSelfConsent',
      to: '/settings',
      cta: 'overview.blockerConsentCta',
    },
    CONSENT_OFF: {
      key: 'overview.blockerConsentOff',
      to: '/settings',
      cta: 'overview.blockerConsentCta',
    },
    NO_PROFILE: {
      key: 'overview.blockerNoProfile',
      to: '/family',
      cta: 'overview.blockerProfileCta',
    },
  };

  if (blocker === null || blocker === 'PRIVATE') return null;
  const { key, to, cta } = copy[blocker];

  return (
    <li className="hoverview__blocker">
      <span className="hoverview__blocker-text">{t(key, { name })}</span>
      {/* A profile is edited in place on this screen, so that row points here
          and the roster below is the destination; consent lives in Settings. */}
      {to === '/settings' ? (
        <Link to={to} className="hoverview__blocker-cta">
          {t(cta)}
        </Link>
      ) : (
        <span className="hoverview__blocker-hint">{t(cta)}</span>
      )}
    </li>
  );
}
