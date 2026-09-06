import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import './SearchBar.css';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (trimmedValue: string) => void;
  /** Must be unique per rendered instance — `AppShell` renders this twice
   *  (mobile header, desktop top bar) and previously both copies shared the
   *  literal id `global-search`, a duplicate-id/duplicate-label defect. */
  id: string;
}

/**
 * The global product-search field. Was inline JSX duplicated verbatim inside
 * `AppShell` for its mobile-header and desktop-topbar renderings; now one
 * definition, so a change to the search experience (placeholder, the scan
 * shortcut, keyboard behaviour) is made once.
 */
export function SearchBar({ value, onChange, onSubmit, id }: SearchBarProps) {
  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit(value.trim());
  }

  return (
    <form className="searchbar" role="search" onSubmit={handleSubmit}>
      <label htmlFor={id} className="sr-only">
        {t('search.placeholder')}
      </label>
      <span className="searchbar__icon" aria-hidden="true">⌕</span>
      <input
        id={id}
        type="search"
        className="searchbar__input"
        placeholder={t('search.placeholder')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete="off"
        enterKeyHint="search"
      />
      <Link className="searchbar__scan" to="/scan" aria-label={t('nav.scan')}>
        <span aria-hidden="true">▣</span>
      </Link>
    </form>
  );
}
