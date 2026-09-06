import { useSyncExternalStore } from 'react';
import { currentAccountId, isSignedIn, subscribeToSession } from './api';

/** Reactive view of `isSignedIn()` — updates every subscribed screen the instant
 *  sign-in or sign-out happens anywhere in the app, not just on that screen's
 *  next unrelated re-render. */
export function useIsSignedIn(): boolean {
  return useSyncExternalStore(subscribeToSession, isSignedIn, isSignedIn);
}

export function useAccountId(): string | null {
  return useSyncExternalStore(subscribeToSession, currentAccountId, currentAccountId);
}
