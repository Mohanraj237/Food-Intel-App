import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { SearchBar } from '@/design-system/SearchBar';
import './AppShell.css';

/**
 * Responsive application shell.
 *
 * Two genuinely different layouts rather than one stretched to fit:
 *
 * - **Under 1024px** — a sticky header carrying the search entry point, and a
 *   bottom navigation bar. Search sits at the top because that is where a thumb
 *   expects it after using any shopping app; navigation sits at the bottom
 *   because that is where the thumb can actually reach.
 * - **1024px and up** — a single top bar with search inline and navigation as
 *   links. A bottom bar on a desktop is a phone layout wearing a costume: the
 *   pointer is nowhere near the bottom of the window and the fixed strip just
 *   eats vertical space.
 *
 * The switch is a media query, not a user-agent sniff, so a narrow desktop
 * window behaves like a phone — which is what the user is actually asking for
 * when they make the window narrow.
 */

/**
 * Five destinations, not six.
 *
 * The first slot swaps rather than grows: signed out it is the marketing home, signed in it
 * is Today — a person who has an account wants their own day, not the landing page, and a
 * sixth bottom-nav item on a phone makes every target smaller. The wordmark still goes to
 * Home for anyone who wants it.
 */
const NAV_SIGNED_OUT = [
  { to: '/', key: 'nav.home', glyph: '⌂', end: true },
  { to: '/search', key: 'nav.search', glyph: '⌕', end: false },
  { to: '/scan', key: 'nav.scan', glyph: '▣', end: false },
  { to: '/family', key: 'nav.family', glyph: '☖', end: false },
  { to: '/settings', key: 'nav.settings', glyph: '⚙', end: false },
] as const;

const NAV_SIGNED_IN = [
  { to: '/today', key: 'nav.today', glyph: '◔', end: false },
  { to: '/search', key: 'nav.search', glyph: '⌕', end: false },
  { to: '/scan', key: 'nav.scan', glyph: '▣', end: false },
  { to: '/family', key: 'nav.family', glyph: '☖', end: false },
  { to: '/settings', key: 'nav.settings', glyph: '⚙', end: false },
] as const;

export function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const mainRef = useRef<HTMLElement>(null);
  const signedIn = useIsSignedIn();
  const NAV = signedIn ? NAV_SIGNED_IN : NAV_SIGNED_OUT;

  // The search box is part of the shell, so it must reflect the URL when the
  // user lands on /search from a link or the back button — otherwise the field
  // and the results silently disagree.
  useEffect(() => {
    if (location.pathname === '/search') {
      const q = new URLSearchParams(location.search).get('q') ?? '';
      setQuery(q);
    }
  }, [location.pathname, location.search]);

  // Move focus to the main region on navigation *after* the first paint, never
  // on the initial load.
  //
  // Focusing <main> on a route change is right: it stops a screen-reader or
  // keyboard user being parked in the nav after every navigation. Doing it on
  // first load is the opposite — focus starts inside the content, so the very
  // first Tab lands past the skip link, the wordmark, the global search box and
  // the whole top navigation, and the only way back to them is Shift+Tab. The
  // skip link is also pointless when focus is already in main. `hasNavigated`
  // keeps the good half of the behaviour and drops the harmful half.
  // Tracks the route this effect last ran for. A boolean "have I run before"
  // flag is not enough: StrictMode invokes effects twice on mount in
  // development, so the second pass would see the flag already set and focus
  // main anyway — reintroducing the bug only in dev, where it is most likely to
  // be noticed and mistaken for something else. Comparing the pathname is
  // idempotent and behaves identically in both modes.
  const focusedPath = useRef<string | null>(null);
  useEffect(() => {
    const previous = focusedPath.current;
    focusedPath.current = location.pathname;
    if (previous === null || previous === location.pathname) return;
    mainRef.current?.focus({ preventScroll: true });
  }, [location.pathname]);

  function submitSearch(trimmed: string) {
    navigate(trimmed ? `/search?q=${encodeURIComponent(trimmed)}` : '/search');
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        {t('nav.skipToContent')}
      </a>

      {/* ---- mobile / tablet header ---- */}
      <header className="shell__header">
        <div className="shell__brandrow">
          <Link to="/" className="wordmark">
            <span className="wordmark__mark" aria-hidden="true">◆</span>
            <span className="wordmark__text">{t('app.name')}</span>
          </Link>
          {/* A settings cog, not a hamburger: this opens Settings directly and
              never a menu, and the account state is carried in the label rather
              than in a glyph a screen-reader user cannot see. */}
          <Link
            to="/settings"
            className="shell__avatar"
            aria-label={signedIn ? t('nav.settingsAccount') : t('nav.settingsSignIn')}
          >
            <span aria-hidden="true">⚙</span>
          </Link>
        </div>
        <SearchBar
          id="global-search-mobile"
          value={query}
          onChange={setQuery}
          onSubmit={submitSearch}
        />
      </header>

      {/* ---- desktop top bar ---- */}
      <header className="shell__topbar">
        <div className="shell__topbar-inner">
          <Link to="/" className="wordmark wordmark--top">
            <span className="wordmark__mark" aria-hidden="true">◆</span>
            <span className="wordmark__text">{t('app.name')}</span>
          </Link>

          <div className="shell__topsearch">
            <SearchBar
              id="global-search-desktop"
              value={query}
              onChange={setQuery}
              onSubmit={submitSearch}
            />
          </div>

          <nav className="toplinks" aria-label={t('nav.primaryLabel')}>
            {NAV.filter((n) => n.to !== '/search' && n.to !== '/').map(({ to, key }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  isActive ? 'toplinks__item toplinks__item--active' : 'toplinks__item'
                }
              >
                {t(key)}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main id="main" ref={mainRef} tabIndex={-1} className="shell__main">
        <Outlet />
      </main>

      {/* ---- mobile bottom navigation ---- */}
      <nav className="bottom-nav" aria-label={t('nav.primaryLabel')}>
        {NAV.map(({ to, key, glyph, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              isActive ? 'bottom-nav__item bottom-nav__item--active' : 'bottom-nav__item'
            }
          >
            {({ isActive }) => (
              <>
                <span className="bottom-nav__glyph" aria-hidden="true">{glyph}</span>
                <span className="bottom-nav__label">{t(key)}</span>
                {isActive && <span className="sr-only">(current)</span>}
              </>
            )}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
