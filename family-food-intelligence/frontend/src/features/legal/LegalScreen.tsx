import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { listPolicies } from '@/lib/consent';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import './LegalScreen.css';

/**
 * The privacy notice and terms of use.
 *
 * These routes did not exist. Every consent record the product writes carries a
 * `documentUri` of `/legal/privacy#account`, and the app asked people to agree
 * to purposes described by a document that returned a 404 — consent to something
 * you cannot read is not consent, and for a product that stores a child's
 * allergens that is the sharpest edge in the whole system.
 *
 * The privacy notice is **generated from the consent policies the API actually
 * returns**, not hand-written prose kept in a separate file. That is deliberate:
 * a notice that drifts from the purposes the software really registers is worse
 * than no notice, because it is confidently wrong. Each purpose here is the same
 * record, with the same version, that Settings asks the user to turn on, so the
 * two can never disagree.
 *
 * The surrounding commitments below describe behaviour that is observable in the
 * product today (export, deletion, retention of consent records). Anything the
 * product does not yet do is not claimed.
 */
export function PrivacyScreen() {
  useDocumentTitleKey('legal.privacyTitle');

  const policies = useQuery({
    queryKey: ['consent', 'policies'],
    queryFn: listPolicies,
    staleTime: 30 * 60 * 1000,
  });

  return (
    <article className="legal">
      <nav className="legal__crumbs">
        <Link to="/settings">{t('legal.backToSettings')}</Link>
      </nav>

      <h1 className="legal__title">{t('legal.privacyTitle')}</h1>
      <p className="legal__lede">{t('legal.privacyLede')}</p>

      <section className="legal__section" aria-labelledby="legal-purposes">
        <h2 id="legal-purposes" className="legal__h2">{t('legal.purposesHeading')}</h2>
        <p>{t('legal.purposesBody')}</p>

        {policies.isPending && <p className="legal__muted">{t('loading.default')}</p>}
        {policies.isError && <p className="legal__muted">{t('legal.purposesUnavailable')}</p>}

        {policies.isSuccess && (
          <dl className="legal__purposes">
            {policies.data.map((policy) => (
              <div key={policy.purposeCode} className="legal__purpose" id={anchorFor(policy.purposeCode)}>
                <dt>
                  {policy.purposeName}
                  {policy.essential && (
                    <span className="legal__tag">{t('legal.essentialTag')}</span>
                  )}
                </dt>
                <dd>
                  <p>{policy.purposeDescription}</p>
                  <p className="legal__muted">
                    {t('consent.policyVersion', { version: policy.policyVersion })}
                  </p>
                </dd>
              </div>
            ))}
          </dl>
        )}
      </section>

      <section className="legal__section" aria-labelledby="legal-children">
        <h2 id="legal-children" className="legal__h2">{t('legal.childrenHeading')}</h2>
        <p>{t('legal.childrenBody')}</p>
      </section>

      <section className="legal__section" aria-labelledby="legal-rights">
        <h2 id="legal-rights" className="legal__h2">{t('legal.rightsHeading')}</h2>
        <p>{t('legal.rightsBody')}</p>
        <ul className="legal__list">
          <li>{t('legal.rightExport')}</li>
          <li>{t('legal.rightWithdraw')}</li>
          <li>{t('legal.rightDelete')}</li>
        </ul>
        <Link to="/settings" className="btn btn--secondary btn--sm">
          {t('legal.backToSettings')}
        </Link>
      </section>

      <section className="legal__section" aria-labelledby="legal-sources">
        <h2 id="legal-sources" className="legal__h2">{t('legal.sourcesHeading')}</h2>
        <p>{t('legal.sourcesBody')}</p>
      </section>

      <p className="legal__review">{t('legal.reviewNotice')}</p>
    </article>
  );
}

/**
 * Terms of use.
 *
 * Short on purpose. The only terms this product can honestly assert today are
 * the ones its own behaviour already demonstrates: what the information is, what
 * it is not, and what we do not promise about a catalogue built from open data.
 */
export function TermsScreen() {
  useDocumentTitleKey('legal.termsTitle');

  return (
    <article className="legal">
      <nav className="legal__crumbs">
        <Link to="/settings">{t('legal.backToSettings')}</Link>
      </nav>

      <h1 className="legal__title">{t('legal.termsTitle')}</h1>
      <p className="legal__lede">{t('legal.termsLede')}</p>

      <section className="legal__section" aria-labelledby="terms-what">
        <h2 id="terms-what" className="legal__h2">{t('legal.termsWhatHeading')}</h2>
        <p>{t('legal.termsWhatBody')}</p>
        <p className="legal__notice">{t('medical.notice')}</p>
      </section>

      <section className="legal__section" aria-labelledby="terms-accuracy">
        <h2 id="terms-accuracy" className="legal__h2">{t('legal.termsAccuracyHeading')}</h2>
        <p>{t('legal.termsAccuracyBody')}</p>
      </section>

      <section className="legal__section" aria-labelledby="terms-account">
        <h2 id="terms-account" className="legal__h2">{t('legal.termsAccountHeading')}</h2>
        <p>{t('legal.termsAccountBody')}</p>
      </section>

      <section className="legal__section" aria-labelledby="terms-contrib">
        <h2 id="terms-contrib" className="legal__h2">{t('legal.termsContributionsHeading')}</h2>
        <p>{t('legal.termsContributionsBody')}</p>
      </section>

      <p className="legal__review">{t('legal.reviewNotice')}</p>
    </article>
  );
}

/**
 * Consent records point at `/legal/privacy#account`, `#personal_fit` and so on,
 * so each purpose needs a stable anchor matching the purpose code the API
 * returns.
 */
function anchorFor(purposeCode: string): string {
  return purposeCode.toLowerCase();
}
