import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ApiError, currentAccountId, storedRefreshToken, subscribeToSession } from '@/lib/api';
import { restoreSession } from '@/lib/auth';
import { AppShell } from './AppShell';
import { HomeScreen } from '@/features/home/HomeScreen';
import { SearchScreen } from '@/features/search/SearchScreen';
import { ProductScreen } from '@/features/product/ProductScreen';
import { CompareScreen } from '@/features/compare/CompareScreen';
import { ScanScreen } from '@/features/scan/ScanScreen';
import { FamilyScreen } from '@/features/household/FamilyScreen';
import { SettingsScreen } from '@/features/settings/SettingsScreen';
import { OpsQueueScreen } from '@/features/ops/OpsQueueScreen';
import { OpsReviewScreen } from '@/features/ops/OpsReviewScreen';
import { NotFoundScreen } from '@/features/shell/NotFoundScreen';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      retry: (failureCount, error) => {
        // Never retry a rejection the server meant: a 401, a 403 or a consent
        // refusal is an answer, not a hiccup. Retrying one wastes the user's
        // connection and, for a consent refusal, repeatedly attempts processing
        // the user has declined.
        if (error instanceof ApiError && !error.isRetryable) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
    },
  },
});

/**
 * Clears every cached query the instant the signed-in account changes — sign-out (account id
 * to `null`) or signing in as a different account in the same tab, without a page reload in
 * between. Without this, `staleTime: 60_000` above means a screen like Home's "recently
 * viewed" rail, the household list, or the ops-access check could keep showing a *previous*
 * account's cached data to whoever is now signed in, for up to a minute (found during Phase 6
 * hardening review — the backend re-checks authorization on every request regardless, but the
 * client must not display stale private data it already fetched under a different identity).
 * A token refresh that keeps the same account id is deliberately not a clear — that would
 * needlessly refetch everything on every silent refresh.
 */
let lastNotifiedAccountId = currentAccountId();
subscribeToSession(() => {
  const nextAccountId = currentAccountId();
  if (nextAccountId !== lastNotifiedAccountId) {
    lastNotifiedAccountId = nextAccountId;
    queryClient.clear();
  }
});

export function App() {
  // The access token is memory-only (api.ts), so a reload otherwise looks
  // signed-out even with a valid refresh token still stored. Gate the very
  // first render only when there is a token worth trying — an anonymous
  // visitor, the common case for browsing, never waits on this at all.
  const [sessionReady, setSessionReady] = useState(() => storedRefreshToken() === null);

  useEffect(() => {
    if (sessionReady) return;
    void restoreSession().finally(() => setSessionReady(true));
  }, [sessionReady]);

  if (!sessionReady) {
    return null;
  }

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<HomeScreen />} />
            <Route path="/search" element={<SearchScreen />} />
            <Route path="/product/:id" element={<ProductScreen />} />
            <Route path="/compare" element={<CompareScreen />} />
            <Route path="/scan" element={<ScanScreen />} />
            <Route path="/family" element={<FamilyScreen />} />
            <Route path="/settings" element={<SettingsScreen />} />
            <Route path="/ops" element={<OpsQueueScreen />} />
            <Route path="/ops/:id" element={<OpsReviewScreen />} />
            <Route path="/404" element={<NotFoundScreen />} />
            <Route path="*" element={<Navigate to="/404" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
