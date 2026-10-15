import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { t, type MessageKey } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { SearchBar } from '@/design-system/SearchBar';
import { withQuery } from '@/lib/searchParams';
import { CompareTrayBar } from '@/design-system/CompareTrayBar';
import { Icon, type IconName } from '@/design-system/Icon';
import { LogoMark } from '@/design-system/Logo';
import { Avatar } from '@/design-system/Avatar';
import { ToastProvider } from '@/design-system/Toast';
import { LogSheetProvider, useLogSheet } from '@/design-system/LogSheet';
import './AppShell.css';

/**
 * Responsive application shell.
 *
 * - **Under 1024px** — a compact sticky header (brand, account) with the search pill under it,
 *   and a bottom navigation bar with a raised centre "Log" action.
 * - **1024px and up** — one top bar: brand, search in the middle, navigation as icon+label
 *   links, account at the end.
 *
 * Home is the first destination for everyone: the product's promise is "does this food fit
 * the people I shop for", so discovery leads and tracking follows.
 */
type NavItem =
  | { kind: 'link'; to: string; key: MessageKey; icon: IconName; end: boolean }
  | { kind: 'log'; key: MessageKey; icon: IconName };

const NAV_SIGNED_OUT: readonly NavItem[] = [
  { kind: 'link', to: '/', key: 'nav.home', icon: 'home', end: true },
  { kind: 'link', to: '/search', key: 'nav.search', icon: 'search', end: false },
  { kind: 'link', to: '/scan', key: 'nav.scan', icon: 'scan', end: false },
  { kind: 'link', to: '/you', key: 'action.signIn', icon: 'user', end: false },
];

const NAV_SIGNED_IN: readonly NavItem[] = [
  { kind: 'link', to: '/', key: 'nav.home', icon: 'home', end: true },
  { kind: 'link', to: '/today', key: 'nav.today', icon: 'today', end: false },
  { kind: 'log', key: 'nav.log', icon: 'plus' },
  { kind: 'link', to: '/family', key: 'nav.family', icon: 'family', end: false },
  { kind: 'link', to: '/you', key: 'nav.you', icon: 'user', end: false },
];

export function AppShell() {
  const signedIn = useIsSignedIn();
  const NAV = signedIn ? NAV_SIGNED_IN : NAV_SIGNED_OUT;

  return (
    <ToastProvider>
      <LogSheetProvider>
        <ShellChrome nav={NAV} />
      </LogSheetProvider>
    </ToastProvider>
  );
}

function ShellChrome({ nav }: { nav: readonly NavItem[] }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const mainRef = useRef<HTMLElement>(null);
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });
  const displayName = me.data?.member?.displayLabel ?? null;

  useEffect(() => {
    if (location.pathname === '/search') {
      const q = new URLSearchParams(location.search).get('q') ?? '';
      setQuery(q);
    }
  }, [location.pathname, location.search]);

  const focusedPath = useRef<string | null>(null);
  useEffect(() => {
    const previous = focusedPath.current;
    focusedPath.current = location.pathname;
    if (previous === null || previous === location.pathname) return;
    mainRef.current?.focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [location.pathname]);

  function submitSearch(trimmed: string) {
    // On the search screen a submit refines the current search (filters kept); elsewhere it starts one.
    const base = location.pathname === '/search' ? new URLSearchParams(location.search) : new URLSearchParams();
    const next = withQuery(base, trimmed).toString();
    navigate(next ? `/search?${next}` : '/search');
  }

  // Results follow typing once on the search screen; elsewhere the field waits for submit.
  // Only `q` changes — filters and sort in the URL stay as they are (D-061).
  useEffect(() => {
    if (location.pathname !== '/search') return;
    const params = new URLSearchParams(location.search);
    const current = params.get('q') ?? '';
    const typed = query.trim();
    if (typed === current.trim()) return;
    const timer = setTimeout(() => {
      const next = withQuery(params, typed).toString();
      navigate(next ? `/search?${next}` : '/search', { replace: true });
    }, 250);
    return () => clearTimeout(timer);
  }, [query, location.pathname, location.search, navigate]);

  // Home carries its own hero search, so the shell's search pill hides there on phones to
  // avoid two identical fields one above the other.
  const onHome = location.pathname === '/';

  const topLinks = nav.filter((item) => item.kind === 'log' || (item.to !== '/search' && item.to !== '/'));

  const accountControl = (
    <Link
      to="/you"
      className="shell__account"
      aria-label={signedIn ? t('nav.youAccount') : t('nav.youSignIn')}
    >
      {signedIn && displayName ? (
        <Avatar name={displayName} size="sm" tone={1} />
      ) : (
        <span className="shell__account-icon">
          <Icon name="user" size={20} />
        </span>
      )}
    </Link>
  );

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        {t('nav.skipToContent')}
      </a>

      {/* ---- mobile / tablet header ---- */}
      <header className={onHome ? 'shell__header shell__header--home' : 'shell__header'}>
        <div className="shell__brandrow">
          <Link to="/" className="wordmark">
            <LogoMark size={30} />
            <span className="wordmark__text">{t('app.shortName')}</span>
          </Link>
          {accountControl}
        </div>
        {!onHome && (
          <SearchBar id="global-search-mobile" value={query} onChange={setQuery} onSubmit={submitSearch} />
        )}
      </header>

      {/* ---- desktop top bar ---- */}
      <header className="shell__topbar">
        <div className="shell__topbar-inner">
          <Link to="/" className="wordmark wordmark--top">
            <LogoMark size={34} />
            <span className="wordmark__text">{t('app.shortName')}</span>
          </Link>

          <div className="shell__topsearch">
            <SearchBar id="global-search-desktop" value={query} onChange={setQuery} onSubmit={submitSearch} />
          </div>

          <nav className="toplinks" aria-label={t('nav.primaryLabel')}>
            {topLinks.map((item) =>
              item.kind === 'log' ? (
                <LogNavButton key="log" variant="top" label={t(item.key)} />
              ) : (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    isActive ? 'toplinks__item toplinks__item--active' : 'toplinks__item'
                  }
                >
                  <Icon name={item.icon} size={20} />
                  <span>{t(item.key)}</span>
                </NavLink>
              ),
            )}
          </nav>
        </div>
      </header>

      <main id="main" ref={mainRef} tabIndex={-1} className="shell__main">
        <Outlet />
      </main>

      <CompareTrayBar />

      {/* ---- mobile bottom navigation ---- */}
      <nav
        className="bottom-nav"
        aria-label={t('nav.primaryLabel')}
        style={{ gridTemplateColumns: `repeat(${nav.length}, minmax(0, 1fr))` }}
      >
        {nav.map((item) =>
          item.kind === 'log' ? (
            <LogNavButton key="log" variant="bottom" label={t(item.key)} />
          ) : (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                isActive ? 'bottom-nav__item bottom-nav__item--active' : 'bottom-nav__item'
              }
            >
              <span className="bottom-nav__icon">
                <Icon name={item.icon} size={22} />
              </span>
              <span className="bottom-nav__label">{t(item.key)}</span>
            </NavLink>
          ),
        )}
      </nav>
    </div>
  );
}

/** The one place either nav renders the centre "Log" action — a button that opens the sheet. */
function LogNavButton({ variant, label }: { variant: 'top' | 'bottom'; label: string }) {
  const { open } = useLogSheet();

  if (variant === 'top') {
    return (
      <button type="button" className="toplinks__item toplinks__item--log" onClick={() => open()}>
        <Icon name="plus" size={20} />
        <span>{label}</span>
      </button>
    );
  }

  return (
    <button type="button" className="bottom-nav__item bottom-nav__item--log" onClick={() => open()}>
      <span className="bottom-nav__fab">
        <Icon name="plus" size={26} strokeWidth={2.25} />
      </span>
      <span className="bottom-nav__label">{label}</span>
    </button>
  );
}
