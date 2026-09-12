import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError, storedRefreshToken } from '@/lib/api';
import { applySession, login, logout, register, signOutLocally } from '@/lib/auth';
import { exportMyData, requestAccountDeletion } from '@/lib/account';
import { getOpsAccess } from '@/lib/ops';
import { useIsSignedIn } from '@/lib/useSession';
import { getStoredTheme, setTheme, type ThemeChoice } from '@/lib/theme';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { ConsentPanel } from '@/features/consent/ConsentPanel';
import { Button } from '@/design-system/Button';
import { Card } from '@/design-system/Card';
import { RowGroup, Row } from '@/design-system/RowGroup';
import { Switch } from '@/design-system/Switch';
import { BottomSheet, ConfirmSheet } from '@/design-system/BottomSheet';
import './SettingsScreen.css';

/**
 * Account and privacy.
 *
 * Account is real: it is the only path by which `isSignedIn()` can ever become
 * true, which every other screen's "sign in to see your family's fit" boundary
 * depends on. The privacy section renders whatever the consent feature exports —
 * see the import below — rather than this screen owning that UI itself.
 */
export function SettingsScreen() {
  const signedIn = useIsSignedIn();
  useDocumentTitleKey('nav.settings');

  return (
    <div className="page">
      <div className="settings__intro">
        <h1>{t('nav.settings')}</h1>
        <p className="settings__lede">{t('settings.intro')}</p>
      </div>

      <AppearancePanel />

      {signedIn ? <AccountPanel /> : <AuthPanel />}

      {signedIn && (
        <>
          <ConsentPanel />
          <DataRightsPanel />
          <OpsAccessPanel />
        </>
      )}

      <PoliciesPanel />
    </div>
  );
}

/**
 * The privacy notice and terms, reachable whether or not you are signed in.
 *
 * Both documents used to 404, including from the `documentUri` recorded on every
 * consent grant. Someone deciding whether to create an account has the most
 * reason to read them, so this sits outside the signed-in branch.
 */
function PoliciesPanel() {
  return (
    <RowGroup title={t('settings.legalHeading')}>
      <Row label={t('legal.privacyTitle')} to="/legal/privacy" />
      <Row label={t('legal.termsTitle')} to="/legal/terms" />
    </RowGroup>
  );
}

/**
 * Light is the primary consumer experience; dark is a real but explicit
 * choice, never inferred from the OS (see lib/theme.ts). This is the only
 * place that choice can be made.
 */
function AppearancePanel() {
  const [theme, setThemeState] = useState<ThemeChoice>(() => getStoredTheme());

  function toggle(checked: boolean) {
    const next: ThemeChoice = checked ? 'dark' : 'light';
    setTheme(next);
    setThemeState(next);
  }

  return (
    <RowGroup title={t('settings.appearanceHeading')}>
      <Row
        label={t('settings.appearanceDark')}
        description={t('settings.appearanceDarkHint')}
        trailing={<Switch checked={theme === 'dark'} onChange={toggle} label={t('settings.appearanceDark')} />}
      />
    </RowGroup>
  );
}

/**
 * A UI affordance only — links to the ops-review console for the (rare) signed-in caller
 * who holds the operator role. Every ops endpoint re-checks this server-side regardless
 * (D-032); this panel simply avoids showing a link that would just 403 for everyone else.
 * Renders nothing at all for a non-operator, not a disabled/greyed-out link.
 */
function OpsAccessPanel() {
  const access = useQuery({
    queryKey: ['ops', 'access'],
    queryFn: getOpsAccess,
  });

  if (!access.isSuccess || !access.data.isOperator) {
    return null;
  }

  return (
    <RowGroup>
      <Row label={t('ops.consoleHeading')} description={t('ops.consoleLink')} to="/ops" />
    </RowGroup>
  );
}

function AccountPanel() {
  const [confirmingSignOut, setConfirmingSignOut] = useState(false);

  const signOut = useMutation({
    mutationFn: async () => {
      const token = storedRefreshToken();
      if (token) {
        // Best-effort: the session ends locally even if the server call fails
        // (offline, already expired) — a user asking to sign out must never be
        // stuck signed in because of a network error.
        await logout(token).catch(() => undefined);
      }
    },
    onSettled: () => signOutLocally(),
  });

  return (
    <>
      <RowGroup title={t('auth.accountHeading')}>
        <Row label={t('nav.family')} to="/family" />
        <Row label={t('settings.activityLink' as Parameters<typeof t>[0])} to="/activity" />
        {/* Confirmed, not immediate. Sign-out was a single tap on a row styled
            exactly like the navigation rows above it, so a mis-tap ended the
            session with no way back except signing in again. */}
        <Row
          label={t('action.signOut')}
          onClick={() => {
            if (!signOut.isPending) setConfirmingSignOut(true);
          }}
          trailing={signOut.isPending ? <span>{t('loading.default')}</span> : undefined}
        />
      </RowGroup>

      <ConfirmSheet
        open={confirmingSignOut}
        onClose={() => setConfirmingSignOut(false)}
        onConfirm={() => {
          setConfirmingSignOut(false);
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

function AuthPanel() {
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

  const errorMessage =
    localError ?? (mutation.error instanceof ApiError ? mutation.error.message : null);

  return (
    <Card as="section" className="settings__section" aria-labelledby="auth-heading">
      <div className="settings__switch" role="tablist" aria-label={t('auth.accountHeading')}>
        <button
          id="auth-tab-signin"
          type="button"
          role="tab"
          aria-selected={mode === 'signin'}
          aria-controls="auth-tabpanel"
          className={mode === 'signin' ? 'settings__tab settings__tab--active' : 'settings__tab'}
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
          className={mode === 'signup' ? 'settings__tab settings__tab--active' : 'settings__tab'}
          onClick={() => switchMode('signup')}
        >
          {t('auth.signUpHeading')}
        </button>
      </div>

      <div id="auth-tabpanel" role="tabpanel" aria-labelledby={`auth-tab-${mode}`}>
        <h2 id="auth-heading" className="sr-only">
          {mode === 'signin' ? t('auth.signInHeading') : t('auth.signUpHeading')}
        </h2>

        <form className="settings__form" onSubmit={submit}>
          <label className="field">
            <span className="field__label">{t('auth.email')}</span>
            <input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
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
            <p className="form-error" role="alert">{errorMessage}</p>
          )}

          <Button type="submit" fullWidth loading={mutation.isPending}>
            {mode === 'signin' ? t('action.signIn') : t('action.signUp')}
          </Button>

          {/* There is no reset flow yet. Saying so plainly beats an absent link,
              which leaves a locked-out user with nothing at all. */}
          {mode === 'signin' && (
            <details className="settings__forgot">
              <summary>{t('settings.forgotPassword')}</summary>
              <p className="field__hint">{t('settings.forgotPasswordHelp')}</p>
            </details>
          )}

          {/* Shown at the moment of the decision, not buried in Settings. */}
          {mode === 'signup' && (
            <p className="settings__legal-consent">
              {t('auth.legalAgreement')}{' '}
              <Link to="/legal/privacy">{t('legal.privacyTitle')}</Link>
              {' · '}
              <Link to="/legal/terms">{t('legal.termsTitle')}</Link>
            </p>
          )}
        </form>
      </div>
    </Card>
  );
}

/** Data export and account deletion (NFR-09, AC-9.2/AC-9.3). */
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
      // A plain client-side download: the data never leaves the browser except
      // to be written to a file the user chose to save.
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
  const description = errorMessage
    ?? (mutation.isSuccess ? t('privacy.exportSuccess') : t('privacy.exportExplainer'));

  return (
    <Row
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
    // Signing out immediately would unmount this success message before anyone
    // could read it (the panel only renders while signed in) — a short delay
    // lets the confirmation actually be seen before the screen falls back to
    // the signed-out view. The refresh token is already revoked server-side by
    // this point; this delay only affects when the local session is cleared.
    onSuccess: () => {
      window.setTimeout(signOutLocally, 3000);
    },
  });

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.message : null;

  if (mutation.isSuccess) {
    return <Row label={t('privacy.deleteHeading')} description={t('privacy.deleteSuccess')} />;
  }

  return (
    <>
      <Row
        label={t('privacy.deleteAccount')}
        description={t('privacy.deleteExplainer')}
        tone="danger"
        onClick={() => setOpen(true)}
      />
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
          <Button
            variant="destructive"
            disabled={typedConfirm !== 'DELETE'}
            loading={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {t('privacy.deleteConfirm')}
          </Button>
        </div>
      </BottomSheet>
    </>
  );
}
