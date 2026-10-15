import { useId, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { t } from '@/lib/i18n';
import type { Suggestion } from '@/lib/catalog';
import { useSuggestions } from '@/lib/useSuggestions';
import { Icon } from './Icon';
import './SearchBar.css';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (trimmedValue: string) => void;
  /** Must be unique per rendered instance — the shell renders this twice. */
  id: string;
  /** `hero` is the larger, floating variant used on Home's top block. */
  variant?: 'shell' | 'hero';
  /** Show brand / category / product suggestions while typing (D-061). On by default. */
  suggest?: boolean;
}

/**
 * The global product-search field: a pill with the search icon leading and the barcode
 * scanner one tap away at the trailing edge — the two entry points to every product journey,
 * always within thumb reach.
 *
 * While typing it offers suggestions (brands, categories, products) as an ARIA combobox:
 * arrow keys move, Enter picks the highlighted one (or searches the typed words when none is
 * highlighted), Escape closes. Picking a brand searches it, a category opens it, a product
 * opens its page.
 */
export function SearchBar({ value, onChange, onSubmit, id, variant = 'shell', suggest = true }: SearchBarProps) {
  const navigate = useNavigate();
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const { items } = useSuggestions(value, suggest && open);
  const showList = open && items.length > 0;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const highlighted = showList && active >= 0 ? items[active] : undefined;
    if (highlighted) {
      pick(highlighted);
      return;
    }
    setOpen(false);
    onSubmit(value.trim());
  }

  function pick(item: Suggestion) {
    setOpen(false);
    setActive(-1);
    if (item.kind === 'PRODUCT') {
      navigate(`/product/${encodeURIComponent(item.target)}`);
    } else if (item.kind === 'CATEGORY') {
      onChange('');
      navigate(`/search?category=${encodeURIComponent(item.target)}`);
    } else {
      onChange(item.target);
      onSubmit(item.target);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!showList) {
      if (event.key === 'ArrowDown' && items.length > 0) setOpen(true);
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((i) => (i + 1) % items.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => (i <= 0 ? items.length - 1 : i - 1));
    } else if (event.key === 'Escape') {
      setOpen(false);
      setActive(-1);
    }
  }

  return (
    <form className={`searchbar searchbar--${variant}`} role="search" onSubmit={handleSubmit}>
      <label htmlFor={id} className="sr-only">
        {t('search.placeholder')}
      </label>
      <span className="searchbar__icon">
        <Icon name="search" size={20} />
      </span>
      <input
        id={id}
        type="search"
        className="searchbar__input"
        placeholder={t('search.placeholder')}
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        // Delay so a tap on a suggestion lands before the list closes.
        onBlur={() => setTimeout(() => setOpen(false), 120)}
        onKeyDown={handleKeyDown}
        autoComplete="off"
        enterKeyHint="search"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
      />
      {value.length > 0 && (
        <button
          type="button"
          className="searchbar__clear"
          aria-label={t('search.clear')}
          onClick={() => onChange('')}
        >
          <Icon name="close" size={16} />
        </button>
      )}
      <Link className="searchbar__scan" to="/scan" aria-label={t('nav.scan')}>
        <Icon name="scan" size={20} />
      </Link>

      <ul id={listId} role="listbox" className="searchbar__suggest" hidden={!showList} aria-label={t('search.suggestionsLabel')}>
        {items.map((item, index) => (
          <li
            key={`${item.kind}-${item.target}`}
            id={`${listId}-${index}`}
            role="option"
            aria-selected={index === active}
            className={index === active ? 'searchbar__option searchbar__option--active' : 'searchbar__option'}
            onMouseDown={(e) => {
              e.preventDefault();
              pick(item);
            }}
            onMouseEnter={() => setActive(index)}
          >
            <span className="searchbar__option-icon" aria-hidden="true">
              <Icon name={item.kind === 'PRODUCT' ? 'food' : item.kind === 'CATEGORY' ? 'grid' : 'bookmark'} size={16} />
            </span>
            <span className="searchbar__option-label">{item.label}</span>
            <span className="searchbar__option-meta">{suggestionMeta(item)}</span>
          </li>
        ))}
      </ul>
    </form>
  );
}

function suggestionMeta(item: Suggestion): string {
  if (item.kind === 'BRAND') {
    const count = Number(item.secondary);
    return Number.isFinite(count) && count > 0 ? t('search.suggest.brandCount', { count }) : t('search.suggest.brand');
  }
  if (item.kind === 'CATEGORY') return t('search.suggest.category');
  return item.secondary ?? '';
}
