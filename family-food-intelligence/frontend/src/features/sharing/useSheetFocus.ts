import { useEffect, useRef, type RefObject } from 'react';

/**
 * Focus management for content rendered inside a `BottomSheet`.
 *
 * `BottomSheet` focuses the dialog when it opens and restores focus when it closes, but it does
 * not keep Tab inside the dialog, so a keyboard user could tab out behind an `aria-modal` sheet
 * into a page they cannot see. That component is shared and owned elsewhere, so the trap lives
 * here, beside the sheets that need it, and attaches to the nearest `role="dialog"` ancestor.
 */

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function focusablesIn(dialog: HTMLElement): HTMLElement[] {
  return Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (el) => el.getAttribute('aria-hidden') !== 'true' && !el.closest('[hidden]'),
  );
}

/** Keeps Tab and Shift+Tab cycling inside the dialog that contains `anchorRef`. */
export function useSheetFocusTrap(anchorRef: RefObject<HTMLElement>): void {
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Tab') return;
      const dialog = anchorRef.current?.closest<HTMLElement>('[role="dialog"]');
      if (!dialog) return;

      const focusables = focusablesIn(dialog);
      const active = document.activeElement;
      if (focusables.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      const first = focusables[0] as HTMLElement;
      const last = focusables[focusables.length - 1] as HTMLElement;

      if (!dialog.contains(active)) {
        event.preventDefault();
        first.focus();
      } else if (event.shiftKey && (active === first || active === dialog)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [anchorRef]);
}

/**
 * Moves focus to `targetRef` whenever `key` changes after the first render — the step heading
 * of a multi-step sheet, so a screen reader announces the new step instead of leaving focus on
 * a button that no longer exists. The first render is left to `BottomSheet`, which focuses the
 * dialog itself so its title is announced.
 */
export function useFocusOnChange(targetRef: RefObject<HTMLElement>, key: string): void {
  const previous = useRef<string | null>(null);
  useEffect(() => {
    if (previous.current !== null && previous.current !== key) {
      targetRef.current?.focus();
    }
    previous.current = key;
  }, [key, targetRef]);
}
