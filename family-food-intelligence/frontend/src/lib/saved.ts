import { useSyncExternalStore } from 'react';
import { currentAccountId, subscribeToSession } from './api';
import type { ProductSummary } from './catalog';

/**
 * Saved products — a shortlist kept on this device.
 *
 * There is no server-side "saved" concept yet, and a shortlist while shopping is worth
 * having before one exists. So this is honest per-device state (localStorage), keyed by the
 * signed-in account so two people sharing a phone never see each other's list, and every
 * surface that shows it says "on this device". Nothing here is sent anywhere.
 *
 * A snapshot of the card data is stored with each entry so the Saved screen renders
 * instantly and offline; the product page is still fetched live when opened.
 */

export interface SavedProduct {
  id: string;
  brandName: string;
  name: string;
  packSize: { value: number | null; unit: string | null } | null;
  categoryCode: string | null;
  categoryName: string | null;
  imageUrl: string | null;
  savedAt: string;
}

const KEY_PREFIX = 'ffi.saved.';
const MAX_ENTRIES = 200;

const listeners = new Set<() => void>();
let cache: { key: string; items: SavedProduct[] } | null = null;

function storageKey(): string {
  return `${KEY_PREFIX}${currentAccountId() ?? 'anon'}`;
}

function read(): SavedProduct[] {
  const key = storageKey();
  if (cache && cache.key === key) return cache.items;
  let items: SavedProduct[] = [];
  try {
    const raw = localStorage.getItem(key);
    if (raw) {
      const parsed: unknown = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        items = parsed.filter((p): p is SavedProduct => typeof p === 'object' && p !== null && typeof (p as SavedProduct).id === 'string');
      }
    }
  } catch {
    items = [];
  }
  cache = { key, items };
  return items;
}

function write(items: SavedProduct[]): void {
  const key = storageKey();
  cache = { key, items };
  try {
    localStorage.setItem(key, JSON.stringify(items));
  } catch {
    // Private mode or storage full: the list still works for this page view.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // A different account signing in changes the key; readers must re-read.
  const unsubscribeSession = subscribeToSession(listener);
  return () => {
    listeners.delete(listener);
    unsubscribeSession();
  };
}

export function listSaved(): SavedProduct[] {
  return read();
}

export function isSaved(productId: string): boolean {
  return read().some((p) => p.id === productId);
}

export function toSavedProduct(product: ProductSummary): SavedProduct {
  return {
    id: product.id,
    brandName: product.brandName,
    name: product.name,
    packSize: product.packSize,
    categoryCode: product.categoryCode,
    categoryName: product.categoryName,
    imageUrl: product.imageUrl,
    savedAt: new Date().toISOString(),
  };
}

/** Adds or removes; returns the new state (`true` = now saved). */
export function toggleSaved(product: SavedProduct): boolean {
  const current = read();
  if (current.some((p) => p.id === product.id)) {
    write(current.filter((p) => p.id !== product.id));
    return false;
  }
  write([{ ...product, savedAt: new Date().toISOString() }, ...current].slice(0, MAX_ENTRIES));
  return true;
}

export function removeSaved(productId: string): void {
  write(read().filter((p) => p.id !== productId));
}

/** Reactive list, newest first. */
export function useSaved(): SavedProduct[] {
  return useSyncExternalStore(subscribe, read, read);
}

export function useIsSaved(productId: string): boolean {
  return useSyncExternalStore(
    subscribe,
    () => isSaved(productId),
    () => false,
  );
}
