import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError, storedRefreshToken } from '@/lib/api';
import { applySession, login, logout, register, signOutLocally } from '@/lib/auth';
import { exportMyData, requestAccountDeletion } from '@/lib/account';
import { getOpsAccess } from '@/lib/ops';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { useSaved } from '@/lib/saved';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { ConsentPanel } from '@/features/consent/ConsentPanel';
import { Button } from '@/design-system/Button';
import { RowGroup, Row } from '@/design-system/RowGroup';
import { BottomSheet, ConfirmSheet } from '@/design-system/BottomSheet';
import { Avatar } from '@/design-system/Avatar';
import { Icon } from '@/design-system/Icon';
import { LogoMark } from '@/design-system/Logo';
import { Skeleton } from '@/design-system/States';
import './SettingsScreen.css';

/**
 * "You" — the account hub. Identity at the top, then your records, sharing, preferences,
 * privacy and data, policies. A plain grouped list is the right shape for an account page;
 * every consumer app's "My account" is exactly this, and it is reached rarely.
 */
export function SettingsScreen() {
  const signedIn = useIsSignedIn();
  useDocumentTitleKey('nav.you');

  return (
    <div className="page layout-list you">
      {signedIn ? <ProfileHeader /> : <WelcomePanel />}

      {signedIn && <RecordsPanel />}
      {signedIn && <SharingPanel />}

      <RowGroup title={t('settings.appearanceHeading')}>
        <Row icon="sun" label={t('you.appearance')} description={t('you.appearanceRowHint')} to="/you/appearance" />
      </RowGroup>

      {signedIn && (
        <>
          <ConsentPanel />
          <DataRightsPanel />
          <OpsAccessPanel />
        </>
      )}

      <RowGroup title={t('settings.legalHeading')}>
        <Row icon="shield" label={t('legal.privacyTitle')} to="/legal/privacy" />
        <Row icon="list" label={t('legal.termsTitle')} to="/legal/terms" />
      </RowGroup>

      {signedIn && <SignOutPanel />}
    </div>
  );
}

/* ------------------------------------------------------------- identity */

function ProfileHeader() {
  const me = useMeQuery();

  if (me.isPending) {
    return (
      <div className="you__hero">
        <Skeleton lines={2} label={t('loading.default')} />
      </div>
    );
  }

  const name = me.data?.member?.displayLabel ?? t('you.accountFallback');
  const household = me.data?.household ?? null;
  const subline = household
    ? household.individual
      ? t('you.soloSubline')
      : t('you.familySubline', { household: household.name, count: household.memberCount })
    : t('you.noHouseholdSubline');

  return (
    <header className="you__hero">
      <Avatar name={name} size="xl" tone={1} />
      <div className="you__hero-text">
        <h1 className="you__name">{name}</h1>
        <p className="you__subline">{subline}</p>
      </div>
      {household ? (
        <Link to="/you/profile" className="btn btn--secondary btn--sm you__edit">
          <Icon name="edit" size={16} />
          {t('profile.editAction')}
        </Link>
      ) : (
        <Link to="/welcome" className="btn btn--primary btn--sm you__edit">
          {t('you.finishSetup')}
        </Link>
      )}
    </header>
  );
}

function RecordsPanel() {
  const saved = useSaved();
  return (
    <RowGroup title={t('you.recordsHeading')}>
      <Row icon="user" label={t('profile.heading')} description={t('you.profileHint')} to="/you/profile" />
      <Row icon="family" label={t('nav.family')} to="/family" />
      <Row icon="home" label={t('you.household')} description={t('you.householdHint')} to="/you/household" />
      <Row
        icon="heart"
        label={t('you.saved')}
        description={t('you.savedHint')}
        to="/saved"
        trailing={
          saved.length > 0 ? (
            <>
              <span>{saved.length}</span>
              <Icon name="chevron-right" size={18} />
            </>
          ) : undefined
        }
      />
      <Row icon="history" label={t('you.recentlyViewed')} to="/history" />
      <Row icon="insight" label={t('you.insights')} description={t('you.insightsHint')} to="/insights" />
    </RowGroup>
  );
}

function SharingPanel() {
  return (
    <RowGroup title={t('you.sharingHeading')}>
      <Row icon="share" label={t('you.sharing')} description={t('you.sharingHint')} to="/you/sharing" />
      <Row icon="family" label={t('you.sharedWithMe')} to="/you/shared-with-me" />
      <Row icon="shield" label={t('you.accessLog')} to="/you/access-log" />
    </RowGroup>
  );
}

function OpsAccessPanel() {
  const access = useQuery({ queryKey: ['ops', 'access'], queryFn: getOpsAccess });

  if (!access.isSuccess || !access.data.isOperator) return null;

  return (
    <RowGroup>
      <Row icon="grid" label={t('ops.consoleHeading')} description={t('ops.consoleLink')} to="/ops" />
    </RowGroup>
  );
}

function SignOutPanel() {
  const [confirming, setConfirming] = useState(false);

  const signOut = useMutation({
    mutationFn: async () => {
      const token = storedRefreshToken();
      if (token) {
        await logout(token).catch(() => undefined);
      }
    },
    onSettled: () => signOutLocally(),
  });

  return (
    <>
      <RowGroup>
        <Row
          icon="arrow-left"
          label={t('action.signOut')}
          onClick={() => {
            if (!signOut.isPending) setConfirming(true);
          }}
          trailing={signOut.isPending ? <span>{t('loading.default')}</span> : undefined}
        />
      </RowGroup>

      <ConfirmSheet
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false);
          signOut.mutate();
        }}
        title={t('settings.signOutConfirmTitle')}
        body={t('settings.signOutConfirmBody')}
        confirmLabel={t('action.signOut')}
        loading={signOut.isPending}
      />
    </>
  );
}

/* ------------------------------------------------------------ signed out */

function WelcomePanel() {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => (mode === 'signin' ? login(email, password) : register(email, password)),
    onSuccess: applySession,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setLocalError(null);
    if (mode === 'signup' && password !== confirmPassword) {
      setLocalError(t('auth.passwordMismatch'));
      return;
    }
    mutation.mutate();
  }

  function switchMode(next: 'signin' | 'signup') {
    setMode(next);
    setLocalError(null);
    mutation.reset();
  }

  const errorMessage = localError ?? (mutation.error instanceof ApiError ? mutation.error.message : null);

  return (
    <section className="welcome" aria-labelledby="auth-heading">
      <div className="welcome__intro">
        <LogoMark size={44} />
        <h1 id="auth-heading" className="welcome__title">
          {t('auth.welcomeTitle')}
        </h1>
        <p className="welcome__lede">{t('auth.welcomeLede')}</p>
        <ul className="welcome__points" role="list">
          <li>
            <Icon name="family" size={18} />
            <span>{t('auth.point1')}</span>
          </li>
          <li>
            <Icon name="shield" size={18} />
            <span>{t('auth.point2')}</span>
          </li>
          <li>
            <Icon name="lock" size={18} />
            <span>{t('auth.point3')}</span>
          </li>
        </ul>
      </div>

      <div className="welcome__form">
        <div className="segmented" role="tablist" aria-label={t('auth.accountHeading')}>
          <button
            id="auth-tab-signin"
            type="button"
            role="tab"
            aria-selected={mode === 'signin'}
            aria-controls="auth-tabpanel"
            className={mode === 'signin' ? 'segmented__item segmented__item--active' : 'segmented__item'}
            onClick={() => switchMode('signin')}
          >
            {t('auth.signInHeading')}
          </button>
          <button
            id="auth-tab-signup"
            type="button"
            role="tab"
            aria-selected={mode === 'signup'}
            aria-controls="auth-tabpanel"
            className={mode === 'signup' ? 'segmented__item segmented__item--active' : 'segmented__item'}
            onClick={() => switchMode('signup')}
          >
            {t('auth.signUpHeading')}
          </button>
        </div>

        <div id="auth-tabpanel" role="tabpanel" aria-labelledby={`auth-tab-${mode}`}>
          <form className="settings__form" onSubmit={submit}>
            <label className="field">
              <span className="field__label">{t('auth.email')}</span>
              <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>

            <label className="field">
              <span className="field__label">{t('auth.password')}</span>
              <input
                type="password"
                required
                minLength={mode === 'signup' ? 12 : undefined}
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              {mode === 'signup' && <span className="field__hint">{t('auth.passwordHint')}</span>}
            </label>

            {mode === 'signup' && (
              <label className="field">
                <span className="field__label">{t('auth.confirmPassword')}</span>
                <input
                  type="password"
                  required
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                />
              </label>
            )}

            {errorMessage && (
              <p className="form-error" role="alert">
                {errorMessage}
              </p>
            )}

            <Button type="submit" fullWidth loading={mutation.isPending}>
              {mode === 'signin' ? t('action.signIn') : t('action.signUp')}
            </Button>

            {mode === 'signin' && (
              <details className="settings__forgot">
                <summary>{t('settings.forgotPassword')}</summary>
                <p className="field__hint">{t('settings.forgotPasswordHelp')}</p>
              </details>
            )}

            {mode === 'signup' && (
              <p className="settings__legal-consent">
                {t('auth.legalAgreement')} <Link to="/legal/privacy">{t('legal.privacyTitle')}</Link>
                {' · '}
                <Link to="/legal/terms">{t('legal.termsTitle')}</Link>
              </p>
            )}
          </form>
        </div>
      </div>
    </section>
  );
}

/* ---------------------------------------------------------- data rights */

function DataRightsPanel() {
  return (
    <RowGroup title={t('privacy.dataRightsHeading')}>
      <ExportRow />
      <DeleteAccountRow />
    </RowGroup>
  );
}

function ExportRow() {
  const mutation = useMutation({
    mutationFn: exportMyData,
    onSuccess: (data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `family-food-intelligence-data-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
    },
  });

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;
  const description = errorMessage ?? (mutation.isSuccess ? t('privacy.exportSuccess') : t('privacy.exportExplainer'));

  return (
    <Row
      icon="download"
      label={t('privacy.exportData')}
      description={description}
      onClick={() => {
        if (!mutation.isPending) mutation.mutate();
      }}
      trailing={mutation.isPending ? <span>{t('loading.default')}</span> : undefined}
    />
  );
}

function DeleteAccountRow() {
  const [open, setOpen] = useState(false);
  const [typedConfirm, setTypedConfirm] = useState('');

  const mutation = useMutation({
    mutationFn: requestAccountDeletion,
    onSuccess: () => {
      window.setTimeout(signOutLocally, 3000);
    },
  });

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  if (mutation.isSuccess) {
    return <Row icon="trash" label={t('privacy.deleteHeading')} description={t('privacy.deleteSuccess')} />;
  }

  return (
    <>
      <Row icon="trash" label={t('privacy.deleteAccount')} description={t('privacy.deleteExplainer')} tone="danger" onClick={() => setOpen(true)} />
      <BottomSheet open={open} onClose={() => setOpen(false)} title={t('privacy.deleteHeading')}>
        <p>{t('privacy.deleteWarning')}</p>
        <p>{t('privacy.otherMembersUnaffected')}</p>
        <label className="field">
          <span className="field__label">{t('privacy.deleteTypeConfirmLabel')}</span>
          <input value={typedConfirm} onChange={(e) => setTypedConfirm(e.target.value)} autoComplete="off" />
        </label>
        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}
        <div className="sheet__actions">
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t('action.cancel')}
          </Button>
          <Button variant="destructive" disabled={typedConfirm !== 'DELETE'} loading={mutation.isPending} onClick={() => mutation.mutate()}>
            {t('privacy.deleteConfirm')}
          </Button>
        </div>
      </BottomSheet>
    </>
  );
}
