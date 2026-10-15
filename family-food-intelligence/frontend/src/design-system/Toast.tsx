import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import './Toast.css';

/**
 * A short confirmation that appears above the bottom navigation and leaves on its own —
 * "Saved your water", "Added to Saved". One at a time; a new one replaces the current one.
 *
 * Only for confirmations of something that already happened. Errors stay inline where the
 * action is (`ErrorState`, `.form-error`), because a person needs to be able to read them and
 * act, and a toast disappears.
 */

interface ToastMessage {
  id: number;
  text: string;
  icon: IconName;
}

interface ToastContextValue {
  show: (text: string, icon?: IconName) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return ctx;
}

const TOAST_MS = 2600;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const timer = useRef<number | null>(null);

  const show = useCallback((text: string, icon: IconName = 'check') => {
    if (timer.current !== null) window.clearTimeout(timer.current);
    setToast({ id: Date.now(), text, icon });
    timer.current = window.setTimeout(() => setToast(null), TOAST_MS);
  }, []);

  useEffect(
    () => () => {
      if (timer.current !== null) window.clearTimeout(timer.current);
    },
    [],
  );

  const value = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {/* Always in the tree so a screen reader hears the message when it arrives. */}
      <div className="toast-region" role="status" aria-live="polite">
        {toast && (
          <div key={toast.id} className="toast">
            <span className="toast__icon">
              <Icon name={toast.icon} size={18} />
            </span>
            <span className="toast__text">{toast.text}</span>
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}
