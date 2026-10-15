import { useState } from 'react';
import { t, type MessageKey } from '@/lib/i18n';
import { getStoredTheme, setTheme, type ThemeChoice } from '@/lib/theme';
import { useMeQuery } from '@/lib/me';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { RowGroup, Row } from '@/design-system/RowGroup';
import { Switch } from '@/design-system/Switch';
import './AppearanceScreen.css';

/**
 * `/you/appearance` (plan §3/§9/§18) — dark mode, moved here from `SettingsScreen.tsx`'s old
 * inline panel, plus the larger-text mode: `html[data-text-size="large"]` (a 1.25× scale + 48/56px
 * targets, already the selector `design-system/LogSheet.css` styles against). Offered, never
 * imposed — a plain toggle a person switches on themselves — and suggested once, as a single
 * line of context rather than a banner, to an OLDER_ADULT tier caller whose `/me` capabilities
 * say `largerTextSuggested` (plan §3.4).
 */

export type TextSizeChoice = 'default' | 'large';

const TEXT_SIZE_STORAGE_KEY = 'ffi-text-size';

export function getStoredTextSize(): TextSizeChoice {
  try {
    return localStorage.getItem(TEXT_SIZE_STORAGE_KEY) === 'large' ? 'large' : 'default';
  } catch {
    return 'default';
  }
}

export function applyTextSize(size: TextSizeChoice): void {
  const root = document.documentElement;
  if (size === 'large') {
    root.setAttribute('data-text-size', 'large');
  } else {
    root.removeAttribute('data-text-size');
  }
}

export function setTextSize(size: TextSizeChoice): void {
  try {
    localStorage.setItem(TEXT_SIZE_STORAGE_KEY, size);
  } catch {
    // Best-effort, same as lib/theme.ts's setTheme: the choice still applies to this page view.
  }
  applyTextSize(size);
}

export function AppearanceScreen() {
  useDocumentTitleKey('you.appearance' as MessageKey);
  const [theme, setThemeState] = useState<ThemeChoice>(() => getStoredTheme());
  const [textSize, setTextSizeState] = useState<TextSizeChoice>(() => getStoredTextSize());
  const me = useMeQuery();

  const suggestLargerText = me.data?.capabilities.largerTextSuggested === true && textSize !== 'large';

  return (
    <div className="page">
      <h1>{t('you.appearance' as MessageKey)}</h1>
      <p className="settings__lede">{t('you.appearanceLede' as MessageKey)}</p>

      <RowGroup title={t('settings.appearanceHeading')}>
        <Row
          label={t('settings.appearanceDark')}
          description={t('settings.appearanceDarkHint')}
          trailing={
            <Switch
              checked={theme === 'dark'}
              onChange={(checked) => {
                const next: ThemeChoice = checked ? 'dark' : 'light';
                setTheme(next);
                setThemeState(next);
              }}
              label={t('settings.appearanceDark')}
            />
          }
        />
        <Row
          label={t('settings.textSizeLabel' as MessageKey)}
          description={
            suggestLargerText
              ? t('settings.textSizeSuggested' as MessageKey)
              : t('settings.textSizeHint' as MessageKey)
          }
          trailing={
            <Switch
              checked={textSize === 'large'}
              onChange={(checked) => {
                const next: TextSizeChoice = checked ? 'large' : 'default';
                setTextSize(next);
                setTextSizeState(next);
              }}
              label={t('settings.textSizeLabel' as MessageKey)}
            />
          }
        />
      </RowGroup>
    </div>
  );
}
