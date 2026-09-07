import { useEffect, useId, useRef, type ReactNode } from 'react';
import { t } from '@/lib/i18n';
import { Button } from './Button';
import './BottomSheet.css';

interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

/**
 * The `--z-sheet*` tokens existed in tokens.css from the original revamp but
 * nothing used them — there was no sheet/dialog primitive at all. Every
 * confirmation in the app used `window.confirm()`, which cannot be styled,
 * cannot show a reason, and reads as a browser prompt rather than part of the
 * product — exactly the "desktop form" feeling called out in the redesign
 * brief. This is that missing primitive.
 */
export function BottomSheet({ open, onClose, title, children }: BottomSheetProps) {
  const titleId = useId();
  const sheetRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return undefined;
    const previouslyFocused = document.activeElement as HTMLElement | null;
    sheetRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previouslyFocused?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div
        ref={sheetRef}
        className="sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sheet__handle" aria-hidden="true" />
        <h2 id={titleId} className="sheet__title">
          {title}
        </h2>
        <div className="sheet__body">{children}</div>
      </div>
    </div>
  );
}

interface ConfirmSheetProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  body?: string;
  confirmLabel: string;
  tone?: 'default' | 'danger';
  loading?: boolean;
}

/** The common case: confirm or cancel, with an optional destructive tone for
 *  an action that cannot be undone (leave, remove, erase). */
export function ConfirmSheet({
  open,
  onClose,
  onConfirm,
  title,
  body,
  confirmLabel,
  tone = 'default',
  loading = false,
}: ConfirmSheetProps) {
  return (
    <BottomSheet open={open} onClose={onClose} title={title}>
      {body && <p>{body}</p>}
      <div className="sheet__actions">
        <Button variant="ghost" onClick={onClose} disabled={loading}>
          {t('action.cancel')}
        </Button>
        <Button variant={tone === 'danger' ? 'destructive' : 'primary'} onClick={onConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </BottomSheet>
  );
}
