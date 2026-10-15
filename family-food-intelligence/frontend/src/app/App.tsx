import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom';
import { ApiError, currentAccountId, storedRefreshToken, subscribeToSession } from '@/lib/api';
import { restoreSession } from '@/lib/auth';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { listMembers } from '@/lib/household';
import { AppShell } from './AppShell';
import { Skeleton, ErrorState } from '@/design-system/States';
import { LogSheetRouteBridge } from '@/design-system/LogSheet';

import { HomeScreen } from '@/features/home/HomeScreen';
import { TodayScreen } from '@/features/today/TodayScreen';
import { MealLogScreen } from '@/features/meals/MealLogScreen';
import { MealDetailScreen } from '@/features/meals/MealDetailScreen';
import { MealHistoryScreen } from '@/features/meals/MealHistoryScreen';
import { GoalsScreen } from '@/features/goals/GoalsScreen';
import { HabitsScreen } from '@/features/habits/HabitsScreen';
import { TrackerHistoryScreen } from '@/features/trackers/TrackerHistoryScreen';
import { SearchScreen } from '@/features/search/SearchScreen';
import { CategoryScreen } from '@/features/category/CategoryScreen';
import { ProductScreen } from '@/features/product/ProductScreen';
import { CompareScreen } from '@/features/compare/CompareScreen';
import { ScanScreen } from '@/features/scan/ScanScreen';
import { SavedScreen } from '@/features/saved/SavedScreen';
import { FamilyScreen } from '@/features/household/FamilyScreen';
import { MemberView } from '@/features/family/MemberView';
import { HouseholdSettingsScreen } from '@/features/household/HouseholdSettingsScreen';
import { ProfilePanel } from '@/features/household/ProfilePanel';
import { SettingsScreen } from '@/features/settings/SettingsScreen';
import { AppearanceScreen } from '@/features/settings/AppearanceScreen';
import { ActivityScreen } from '@/features/activity/ActivityScreen';
import { OnboardingScreen } from '@/features/onboarding/OnboardingScreen';
import { SharingScreen } from '@/features/sharing/SharingScreen';
import { InsightsHistoryScreen } from '@/features/insights/InsightsHistoryScreen';
import { HealthShelfScreen } from '@/features/health-shelf/HealthShelfScreen';
import { AskScreen } from '@/features/ask/AskScreen';
import { OpsQueueScreen } from '@/features/ops/OpsQueueScreen';
import { OpsReviewScreen } from '@/features/ops/OpsReviewScreen';
import { RequireOperator } from '@/features/ops/RequireOperator';
import { PrivacyScreen, TermsScreen } from '@/features/legal/LegalScreen';
import { NotFoundScreen } from '@/features/shell/NotFoundScreen';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      retry: (failureCount, error) => {
        // Never retry a rejection the server meant: a 401, a 403 or a consent refusal is an
        // answer, not a hiccup.
        if (error instanceof ApiError && !error.isRetryable) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
    },
  },
});

/** Clears every cached query the instant the signed-in account changes. */
let lastNotifiedAccountId = currentAccountId();
subscribeToSession(() => {
  const nextAccountId = currentAccountId();
  if (nextAccountId !== lastNotifiedAccountId) {
    lastNotifiedAccountId = nextAccountId;
    queryClient.clear();
  }
});

/**
 * Gates every household/member-dependent route on `GET /me`'s `mode` (D-043, D-046): a signed-in
 * health route while mode = NONE redirects to `/welcome?next=<path>`. Browsing routes and the
 * account page work without a household and stay outside this gate.
 */
function RequireHousehold() {
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });
  const location = useLocation();

  if (signedIn && me.data && me.data.mode === 'NONE') {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/welcome?next=${next}`} replace />;
  }
  return <Outlet />;
}

/** Bridges `/you/profile` (self) and `/family/:memberId/profile` (a guarded child) onto ProfilePanel. */
function ProfileRoute({ subject }: { subject: 'self' | 'member' }) {
  const { memberId: routeMemberId } = useParams<{ memberId?: string }>();
  const navigate = useNavigate();
  const me = useMeQuery();
  const householdId = me.data?.household?.id ?? null;
  const targetMemberId = subject === 'self' ? me.data?.member?.id ?? null : routeMemberId ?? null;

  const members = useQuery({
    queryKey: ['household', 'members', householdId],
    queryFn: () => listMembers(householdId as string),
    enabled: householdId !== null,
  });

  if (me.isPending || (householdId !== null && members.isPending)) {
    return (
      <div className="page">
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }
  if (me.isError) {
    return (
      <div className="page">
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      </div>
    );
  }
  if (members.isError) {
    return (
      <div className="page">
        <ErrorState error={members.error} onRetry={() => void members.refetch()} />
      </div>
    );
  }
  if (!householdId || !targetMemberId) {
    return <NotFoundScreen />;
  }
  const member = (members.data ?? []).find((m) => m.id === targetMemberId);
  if (!member) {
    return <NotFoundScreen />;
  }

  return (
    <ProfilePanel
      householdId={householdId}
      member={member}
      onBack={() => navigate(subject === 'self' ? '/you' : `/family/${targetMemberId}`)}
    />
  );
}

export function App() {
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
            {/* ---- browsing: no household required ---- */}
            <Route path="/" element={<HomeScreen />} />
            <Route path="/food" element={<Navigate to="/" replace />} />
            <Route path="/search" element={<SearchScreen />} />
            <Route path="/category/:code" element={<CategoryScreen />} />
            <Route path="/product/:id" element={<ProductScreen />} />
            <Route path="/compare" element={<CompareScreen />} />
            <Route path="/scan" element={<ScanScreen />} />
            <Route path="/saved" element={<SavedScreen />} />
            <Route path="/history" element={<ActivityScreen />} />
            <Route path="/food/recent" element={<Navigate to="/history" replace />} />
            <Route path="/activity" element={<Navigate to="/history" replace />} />

            {/* ---- account: works with or without a household ---- */}
            <Route path="/you" element={<SettingsScreen />} />
            <Route path="/settings" element={<Navigate to="/you" replace />} />
            <Route path="/you/appearance" element={<AppearanceScreen />} />

            {/* ---- onboarding ---- */}
            <Route path="/welcome" element={<OnboardingScreen />} />

            {/* ---- sharing/caregiving: has its own "no household yet" state ---- */}
            <Route path="/you/sharing" element={<SharingScreen />} />
            <Route path="/you/sharing/:audienceMemberId" element={<SharingScreen />} />
            <Route path="/you/sharing/help" element={<SharingScreen />} />
            <Route path="/you/shared-with-me" element={<SharingScreen />} />
            <Route path="/you/access-log" element={<SharingScreen />} />

            {/* ---- household/member-dependent: gated on mode !== NONE ---- */}
            <Route element={<RequireHousehold />}>
              <Route path="/today" element={<TodayScreen />} />
              <Route path="/log/:kind" element={<LogSheetRouteBridge />} />

              <Route path="/goals" element={<GoalsScreen />} />
              <Route path="/habits" element={<HabitsScreen />} />
              <Route path="/trackers/:category" element={<TrackerHistoryScreen />} />

              <Route path="/meals/new" element={<MealLogScreen />} />
              <Route path="/meals/:id" element={<MealDetailScreen />} />
              <Route path="/meals" element={<MealHistoryScreen />} />

              <Route path="/family" element={<FamilyScreen />} />
              <Route path="/family/:memberId" element={<MemberView />} />
              <Route path="/family/:memberId/trackers/:category" element={<TrackerHistoryScreen />} />
              <Route path="/family/:memberId/goals" element={<GoalsScreen />} />
              <Route path="/family/:memberId/habits" element={<HabitsScreen />} />
              <Route path="/family/:memberId/profile" element={<ProfileRoute subject="member" />} />

              <Route path="/you/profile" element={<ProfileRoute subject="self" />} />
              <Route path="/you/household" element={<HouseholdSettingsScreen />} />

              <Route path="/insights" element={<InsightsHistoryScreen />} />
              <Route path="/next" element={<Navigate to="/insights" replace />} />
              <Route path="/next/insights" element={<Navigate to="/insights" replace />} />
              {/* Feature-flagged previews (off by default) keep their routes. */}
              <Route path="/next/health-shelf" element={<HealthShelfScreen />} />
              <Route path="/next/ask" element={<AskScreen />} />
            </Route>

            <Route path="/legal/privacy" element={<PrivacyScreen />} />
            <Route path="/legal/terms" element={<TermsScreen />} />
            <Route
              path="/ops"
              element={
                <RequireOperator>
                  <OpsQueueScreen />
                </RequireOperator>
              }
            />
            <Route
              path="/ops/:id"
              element={
                <RequireOperator>
                  <OpsReviewScreen />
                </RequireOperator>
              }
            />
            <Route path="/404" element={<NotFoundScreen />} />
            <Route path="*" element={<Navigate to="/404" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
