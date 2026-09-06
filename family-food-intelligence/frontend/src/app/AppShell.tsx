import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
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

const NAV = [
  { to: '/', key: 'nav.home', glyph: '⌂', end: true },
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

  // The search box is part of the shell, so it must reflect the URL when the
  // user lands on /search from a link or the back button — otherwise the field
  // and the results silently disagree.
  useEffect(() => {
    if (location.pathname === '/search') {
      const q = new URLSearchParams(location.search).get('q') ?? '';
      setQuery(q);
    }
  }, [location.pathname, location.search]);

  // Move focus to the main region on navigation. Without this a screen-reader
  // or keyboard user stays parked in the nav and has to traverse the whole shell
  // again after every route change.
  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
  }, [location.pathname]);

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = query.trim();
    navigate(trimmed ? `/search?q=${encodeURIComponent(trimmed)}` : '/search');
  }

  const searchField = (
    <form className="searchbar" role="search" onSubmit={submitSearch}>
      <label htmlFor="global-search" className="sr-only">
        {t('search.placeholder')}
      </label>
      <span className="searchbar__icon" aria-hidden="true">⌕</span>
      <input
        id="global-search"
        type="search"
        className="searchbar__input"
        placeholder={t('search.placeholder')}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        autoComplete="off"
        enterKeyHint="search"
      />
      <Link className="searchbar__scan" to="/scan" aria-label={t('nav.scan')}>
        <span aria-hidden="true">▣</span>
      </Link>
    </form>
  );

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
          <Link to="/settings" className="shell__avatar" aria-label={t('nav.settings')}>
            <span aria-hidden="true">{signedIn ? '☺' : '☰'}</span>
          </Link>
        </div>
        {searchField}
      </header>

      {/* ---- desktop top bar ---- */}
      <header className="shell__topbar">
        <div className="shell__topbar-inner">
          <Link to="/" className="wordmark wordmark--top">
            <span className="wordmark__mark" aria-hidden="true">◆</span>
            <span className="wordmark__text">{t('app.name')}</span>
          </Link>

          <div className="shell__topsearch">{searchField}</div>

          <nav className="toplinks" aria-label={t('app.name')}>
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
      <nav className="bottom-nav" aria-label={t('app.name')}>
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
