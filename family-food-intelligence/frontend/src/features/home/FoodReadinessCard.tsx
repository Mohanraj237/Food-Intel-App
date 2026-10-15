import { Link } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { useMyHousehold } from '@/lib/useMyHousehold';
import { useHouseholdOverview, type BlockerKind, type MemberReadiness } from '@/features/household/useHouseholdOverview';
import { Skeleton } from '@/design-system/States';
import { Icon } from '@/design-system/Icon';
import { Avatar } from '@/design-system/Avatar';
import './FoodReadinessCard.css';

/**
 * "Shopping for the Iyer family" — how the household is set up for personal results, what it is
 * watching and which allergens to avoid: the facts a person shopping for their family needs
 * beside the search box. No verdict colour anywhere (this is a setup nudge, not a food verdict).
 * What's still missing is one tap away, never a wall of blockers on the home screen.
 */
export function FoodReadinessCard() {
  const signedIn = useIsSignedIn();
  const household = useMyHousehold({ enabled: signedIn });

  if (!signedIn || household.householdId === null) return null;
  return <FoodReadinessCardInner householdId={household.householdId} />;
}

function FoodReadinessCardInner({ householdId }: { householdId: string }) {
  const overview = useHouseholdOverview(householdId);
  const me = useMeQuery();
  const householdName = me.data?.household?.name ?? '';
  const individual = me.data?.household?.individual === true;

  if (overview.isPending) {
    return (
      <section className="ready" aria-labelledby="ready-heading">
        <h2 id="ready-heading" className="ready__title">
          {t('overview.heading')}
        </h2>
        <Skeleton lines={2} label={t('loading.default')} />
      </section>
    );
  }

  if (overview.isError || overview.totalCount === 0) return null;

  const { readyCount, totalCount, needsAttention, sharedGoals, householdAllergens, members } = overview;
  const allReady = readyCount === totalCount;
  const faces = members.slice(0, 5);

  return (
    <section className="ready" aria-labelledby="ready-heading">
      <div className="ready__head">
        <div className="ready__faces" aria-hidden="true">
          {faces.map((entry) => (
            <Avatar key={entry.member.id} name={entry.member.displayLabel} size="sm" child={entry.member.memberType === 'CHILD'} />
          ))}
        </div>
        <div className="ready__titles">
          <h2 id="ready-heading" className="ready__title">
            {individual ? t('overview.headingSolo') : t('overview.headingFamily', { household: householdName })}
          </h2>
          <p className="ready__status">
            {allReady ? t('overview.allReady', { count: totalCount }) : t('overview.someReady', { ready: readyCount, total: totalCount })}
          </p>
        </div>
        <Link to="/you/household" className="ready__manage" aria-label={t('familyHub.manageHousehold')}>
          <Icon name="settings" size={18} />
        </Link>
      </div>

      <div className="ready__facts">
        <div className="ready__fact">
          <span className="ready__fact-title">{t('overview.watchingHeading')}</span>
          {sharedGoals.length === 0 ? (
            <span className="ready__fact-empty">{t('overview.watchingEmptyShort')}</span>
          ) : (
            <ul className="ready__tags" role="list">
              {sharedGoals.map(({ code, count }) => (
                <li key={code} className="ready__tag">
                  {t(`goal.${code}` as MessageKey)}
                  {count > 1 && <span className="ready__tag-count">{count}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="ready__fact">
          <span className="ready__fact-title">{t('overview.allergensHeadingShort')}</span>
          {householdAllergens.length === 0 ? (
            <span className="ready__fact-empty">{t('overview.allergensEmptyShort')}</span>
          ) : (
            <ul className="ready__tags" role="list">
              {householdAllergens.map((code) => (
                <li key={code} className="ready__tag ready__tag--allergen">
                  <Icon name="alert" size={12} />
                  {t(`allergenCode.${code}` as MessageKey)}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {needsAttention.length > 0 && (
        <details className="ready__missing">
          <summary className="ready__missing-summary">
            <Icon name="info" size={16} />
            {t('overview.missingSummary', { count: needsAttention.length })}
            <Icon name="chevron-down" size={16} className="ready__missing-chev" />
          </summary>
          <ul className="ready__blockers" role="list">
            {needsAttention.map((entry) => (
              <BlockerRow key={entry.member.id} entry={entry} />
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

function BlockerRow({ entry }: { entry: MemberReadiness }) {
  const { member, blocker } = entry;
  const name = member.displayLabel;

  if (blocker === null || blocker === 'PRIVATE') return null;

  const copy: Record<Exclude<BlockerKind, 'PRIVATE'>, { key: MessageKey; to: string; cta: MessageKey }> = {
    CONSENT_PENDING: {
      key: member.memberType === 'CHILD' ? 'overview.blockerChildConsent' : 'overview.blockerSelfConsent',
      to: '/you',
      cta: 'overview.blockerConsentCta',
    },
    CONSENT_OFF: { key: 'overview.blockerConsentOff', to: '/you', cta: 'overview.blockerConsentCta' },
    NO_PROFILE: {
      key: 'overview.blockerNoProfile',
      to: `/family/${member.id}/profile`,
      cta: 'foodReadiness.blockerProfileCta',
    },
  };

  const { key, to, cta } = copy[blocker];

  return (
    <li className="ready__blocker">
      <span className="ready__blocker-text">{t(key, { name })}</span>
      <Link to={to} className="ready__blocker-cta">
        {t(cta)}
        <Icon name="chevron-right" size={14} />
      </Link>
    </li>
  );
}
