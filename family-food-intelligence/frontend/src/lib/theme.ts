/**
 * Appearance (light/dark).
 *
 * Light is the primary consumer experience (docs/design/design-system.md).
 * Unlike the previous build, dark is never inferred from the OS — it is only
 * ever the result of an explicit choice here, persisted so it survives a
 * reload. The choice is applied as `data-theme` on `<html>`; tokens.css only
 * defines dark values under `[data-theme="dark"]`.
 */

export type ThemeChoice = 'light' | 'dark';

const STORAGE_KEY = 'ffi-theme';

export function getStoredTheme(): ThemeChoice {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export function applyTheme(theme: ThemeChoice): void {
  const root = document.documentElement;
  if (theme === 'dark') {
    root.setAttribute('data-theme', 'dark');
  } else {
    root.removeAttribute('data-theme');
  }

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    meta.setAttribute('content', theme === 'dark' ? '#121513' : '#0E7C6B');
  }
}

export function setTheme(theme: ThemeChoice): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Best-effort: the choice still applies for this page view even if it
    // cannot persist (private browsing, storage disabled).
  }
  applyTheme(theme);
}
