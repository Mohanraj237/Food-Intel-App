import enIN from '@/locales/en-IN';

/**
 * Minimal message resolution.
 *
 * No i18n library: the product needs key lookup and named interpolation, and
 * nothing else. A library would add a bundle cost and a plural/gender ruleset we
 * do not use, on a product whose first promise is speed on a mid-range Android.
 *
 * Every user-facing string in the app comes from here (D-019). Hindi is a second
 * bundle, not a refactor.
 */

export type MessageKey = keyof typeof enIN;

const BUNDLES = { 'en-IN': enIN } as const;

export type Locale = keyof typeof BUNDLES;

export const DEFAULT_LOCALE: Locale = 'en-IN';

let activeLocale: Locale = DEFAULT_LOCALE;

export function setLocale(locale: Locale): void {
  activeLocale = locale;
  document.documentElement.lang = locale;
}

export function getLocale(): Locale {
  return activeLocale;
}

/**
 * Resolves a message and substitutes `{name}` placeholders.
 *
 * A missing key returns the key itself rather than throwing or rendering blank.
 * A blank string on a health screen is worse than a visible key: it hides that
 * something is wrong, and the key is at least diagnosable. `assertNoMissingKeys`
 * in the test suite stops one reaching production.
 */
export function t(key: MessageKey, params?: Record<string, string | number>): string {
  const template = BUNDLES[activeLocale][key] as string | undefined;

  if (template === undefined) {
    if (import.meta.env.DEV) {
      console.warn(`[i18n] missing key: ${key}`);
    }
    return key;
  }

  if (!params) {
    return template;
  }

  return template.replace(/\{(\w+)\}/g, (match, name: string) => {
    const value = params[name];
    return value === undefined ? match : String(value);
  });
}

/** Every key in the active bundle. Used by the vocabulary and coverage tests. */
export function allMessages(): Record<string, string> {
  return BUNDLES[activeLocale] as Record<string, string>;
}
