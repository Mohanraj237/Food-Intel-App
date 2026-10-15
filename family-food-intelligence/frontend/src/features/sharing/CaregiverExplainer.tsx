import { t } from '@/lib/i18n';

/**
 * What a caregiver can and cannot do, in plain words (D-045). Shown open wherever MANAGE is
 * being chosen, before anything is saved, so nobody makes someone a caregiver without reading
 * what that means; shown collapsed on a caregiver's card afterwards, one tap away.
 *
 * `areas` names the areas chosen; when empty the sentence speaks about "the areas you choose"
 * instead of listing nothing.
 */
export function CaregiverExplainer({
  name,
  areas,
  collapsible = false,
}: {
  name: string;
  areas: string;
  collapsible?: boolean;
}) {
  const list = (
    <ul className="sharing-caregiver__list" role="list">
      <Item mark="✓">
        {areas ? t('sharing.caregiver.can', { name, areas }) : t('sharing.caregiver.canChosen', { name })}
      </Item>
      <Item mark="✕">{t('sharing.caregiver.noOnward')}</Item>
      <Item mark="✕">{t('sharing.caregiver.noSettings')}</Item>
      <Item mark="✕">{t('sharing.caregiver.noExport')}</Item>
      <Item mark="✓">{t('sharing.caregiver.stopAnyTime')}</Item>
      <Item mark="✓">{t('sharing.caregiver.logged')}</Item>
    </ul>
  );

  if (collapsible) {
    return (
      <details className="sharing-caregiver sharing-caregiver--collapsible">
        <summary className="sharing-caregiver__summary">{t('sharing.caregiver.heading')}</summary>
        {list}
      </details>
    );
  }

  return (
    <div className="sharing-caregiver" role="note">
      <p className="sharing-caregiver__title">{t('sharing.caregiver.heading')}</p>
      {list}
    </div>
  );
}

/** The mark is decoration; each sentence already says "can" or "can't" in words. */
function Item({ mark, children }: { mark: string; children: string }) {
  return (
    <li className="sharing-caregiver__item">
      <span className="sharing-caregiver__mark" aria-hidden="true">
        {mark}
      </span>
      <span>{children}</span>
    </li>
  );
}
