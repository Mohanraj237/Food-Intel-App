import { useRef, type KeyboardEvent } from 'react';
import './QuickActionChip.css';

export interface QuickActionChipOption {
  value: string;
  label: string;
}

interface QuickActionChipGroupProps {
  /** Named group, e.g. "Amount" — becomes the `role="radiogroup"`'s accessible name. */
  legend: string;
  options: readonly QuickActionChipOption[];
  value: string | null;
  onChange: (value: string) => void;
}

/**
 * A group of preset chips (plan §8.2) — the Log sheet's "150 / 250 / 500 ml", portion fractions,
 * and similar closed choice sets. A `role="radiogroup"` of `role="radio"` chips rather than a
 * row of plain buttons, so a screen reader announces it as one choice among options instead of a
 * list of unrelated actions — and so arrow keys move the selection the way a native radio group
 * already does, per WCAG 2.1.1.
 */
export function QuickActionChipGroup({ legend, options, value, onChange }: QuickActionChipGroupProps) {
  const groupRef = useRef<HTMLDivElement>(null);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!['ArrowRight', 'ArrowLeft', 'ArrowDown', 'ArrowUp'].includes(event.key)) return;
    event.preventDefault();
    const forward = event.key === 'ArrowRight' || event.key === 'ArrowDown';
    const currentIndex = options.findIndex((o) => o.value === value);
    const nextIndex =
      currentIndex === -1
        ? 0
        : (currentIndex + (forward ? 1 : -1) + options.length) % options.length;
    const next = options[nextIndex];
    if (!next) return;
    onChange(next.value);
    groupRef.current
      ?.querySelector<HTMLButtonElement>(`[data-chip-value="${cssEscape(next.value)}"]`)
      ?.focus();
  }

  return (
    <div
      ref={groupRef}
      role="radiogroup"
      aria-label={legend}
      className="quick-action-chips"
      onKeyDown={onKeyDown}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            data-chip-value={option.value}
            tabIndex={selected || (value === null && option === options[0]) ? 0 : -1}
            className={selected ? 'quick-action-chip quick-action-chip--selected' : 'quick-action-chip'}
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/** A single chip, for a caller that wants the button without the radiogroup wrapper. */
export function QuickActionChip({
  label,
  selected,
  onClick,
}: {
  label: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      className={selected ? 'quick-action-chip quick-action-chip--selected' : 'quick-action-chip'}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

function cssEscape(value: string): string {
  return typeof CSS !== 'undefined' && 'escape' in CSS ? CSS.escape(value) : value.replace(/"/g, '\\"');
}
