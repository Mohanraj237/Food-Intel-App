import type { ReactNode } from 'react';
import type { Capabilities } from '@/lib/me';

/**
 * Renders from a server-given capability flag, never a client-side inference (D-046, plan §12).
 *
 * Both wrappers below take the *already-fetched* response — `/me`'s or `/members/{id}/experience`'s
 * `capabilities`, or an overview/today payload's `visibleScopes` — rather than fetching anything
 * themselves. That is deliberate: it keeps every screen's "one source per screen" query
 * discipline (plan §11) intact, and it is why there is no `memberId` prop here for either
 * component to go fetch with. A screen that needs the data calls its own hook, then wraps the
 * part of its tree that depends on the answer in one of these.
 */

interface RequireScopeProps {
  /** The caller's already-fetched `visibleScopes` (a `MemberToday`/`OverviewMember`/
   *  `MemberAccess` field). */
  visibleScopes: readonly string[];
  scope: string;
  children: ReactNode;
  /** The "not shared with you" state (plan §2.4) — omit to render nothing rather than a
   *  fallback, when the caller already renders its own designed empty state around this. */
  fallback?: ReactNode;
}

/** Gates on one access-scope category being visible to the caller. */
export function RequireScope({ visibleScopes, scope, children, fallback = null }: RequireScopeProps) {
  if (!visibleScopes.includes(scope)) return <>{fallback}</>;
  return <>{children}</>;
}

interface RequireCapabilityProps {
  /** The caller's already-fetched capabilities (`MeView.capabilities` or
   *  `MemberExperience.capabilities`); `undefined` while still loading treats the gate as
   *  closed, same as a `false` test — nothing renders early on an optimistic guess. */
  capabilities: Capabilities | undefined;
  test: (capabilities: Capabilities) => boolean;
  children: ReactNode;
  fallback?: ReactNode;
}

/** Gates on a `Capabilities` predicate — `canTrack`, `habits`, a `trackableMetrics` membership,
 *  and so on — never on an inferred age band or member type. */
export function RequireCapability({ capabilities, test, children, fallback = null }: RequireCapabilityProps) {
  if (!capabilities || !test(capabilities)) return <>{fallback}</>;
  return <>{children}</>;
}
