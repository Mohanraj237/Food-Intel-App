import { useSyncExternalStore } from 'react';
import { currentAccountId, subscribeToSession } from './api';

/**
 * The compare tray (D-066): up to five products picked from any card or product page, kept on
 * this device and keyed by the signed-in account (two people sharing a phone never see each
 * other's tray). Each entry carries just enough to draw a thumbnail in the tray bar without a
 * request. Nothing here is sent anywhere until Compare is opened.
 */

export const MAX_COMPARE = 5;

export interface TrayItem {
  id: string;
  brandName: string;
  name: string;
  imageUrl: string | null;
  categoryCode: string | null;
}

const KEY_PREFIX = 'ffi.compareTray.';
const listeners = new Set<() => void>();
let cache: { key: string; items: TrayItem[] } | null = null;

function storageKey(): string {
  return `${KEY_PREFIX}${currentAccountId() ?? 'anon'}`;
}

function read(): TrayItem[] {
  const key = storageKey();
  if (cache && cache.key === key) return cache.items;
  let items: TrayItem[] = [];
  try {
    const raw = localStorage.getItem(key);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    if (Array.isArray(parsed)) {
      items = parsed
        .filter((p): p is TrayItem => typeof p === 'object' && p !== null && typeof (p as TrayItem).id === 'string')
        .slice(0, MAX_COMPARE);
    }
  } catch {
    items = [];
  }
  cache = { key, items };
  return items;
}

function write(items: TrayItem[]): void {
  const key = storageKey();
  cache = { key, items };
  try {
    localStorage.setItem(key, JSON.stringify(items));
  } catch {
    // Private mode or storage full: the tray still works for this page view.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  const unsubscribeSession = subscribeToSession(listener);
  return () => {
    listeners.delete(listener);
    unsubscribeSession();
  };
}

export function trayItems(): TrayItem[] {
  return read();
}

export function isInTray(productId: string): boolean {
  return read().some((p) => p.id === productId);
}

export type AddResult = 'added' | 'removed' | 'full';

/** Adds the product, or removes it if already there. Refuses a sixth product rather than dropping one silently. */
export function toggleTray(item: TrayItem): AddResult {
  const items = read();
  if (items.some((p) => p.id === item.id)) {
    write(items.filter((p) => p.id !== item.id));
    return 'removed';
  }
  if (items.length >= MAX_COMPARE) return 'full';
  write([...items, item]);
  return 'added';
}

export function removeFromTray(productId: string): void {
  write(read().filter((p) => p.id !== productId));
}

/** Replaces the tray with these items (deduplicated, capped) — used when opening a shared compare link. */
export function setTray(items: TrayItem[]): void {
  const seen = new Set<string>();
  write(items.filter((i) => !seen.has(i.id) && seen.add(i.id)).slice(0, MAX_COMPARE));
}

export function clearTray(): void {
  write([]);
}

export function useTray(): TrayItem[] {
  return useSyncExternalStore(subscribe, read, read);
}

export function useIsInTray(productId: string): boolean {
  return useSyncExternalStore(subscribe, () => isInTray(productId), () => isInTray(productId));
}

/** The URL that opens a comparison of these products. */
export function compareHref(ids: string[]): string {
  return `/compare?ids=${ids.map(encodeURIComponent).join(',')}`;
}
