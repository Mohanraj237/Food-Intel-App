import { useEffect } from 'react';
import { t, type MessageKey } from './i18n';

/**
 * Sets the document title for a screen.
 *
 * Every route previously shared one title, so browser history, tab switching
 * and a screen reader's page announcement all said "Family Food Intelligence"
 * and nothing more — leaving a user with several tabs open, or navigating by
 * history, unable to tell a product from the settings page.
 *
 * The product name stays as a suffix so the tab is still identifiable when
 * truncated to a few characters, which is how a pinned tab renders.
 */
export function useDocumentTitle(title: string | null | undefined): void {
  useEffect(() => {
    const appName = t('app.name');
    document.title = title ? `${title} · ${appName}` : appName;
    return () => {
      document.title = appName;
    };
  }, [title]);
}

/** Convenience for the common "title is a static message key" case. */
export function useDocumentTitleKey(key: MessageKey): void {
  useDocumentTitle(t(key));
}
