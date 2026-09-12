import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { getOpsAccess } from '@/lib/ops';
import { useIsSignedIn } from '@/lib/useSession';
import { Skeleton } from '@/design-system/States';

/**
 * Gate in front of every `/ops` screen.
 *
 * The backend is, and remains, the real boundary — every ops endpoint re-checks
 * the caller's operator role regardless of how the screen was reached (D-032).
 * This changes only what a non-operator *sees* on the way to being refused.
 *
 * Previously the queue rendered its heading, its Pending/Approved/Rejected tabs
 * and then a red "Something went wrong" carrying a bare correlation UUID. That
 * told anyone who typed the URL that an internal review console exists and what
 * its workflow looks like, framed a routine authorization requirement as a
 * system fault, and offered no way to sign in. Checking access before rendering
 * anything removes all four problems without weakening the server-side check.
 */
export function RequireOperator({ children }: { children: ReactNode }) {
  const signedIn = useIsSignedIn();

  const access = useQuery({
    queryKey: ['ops', 'access'],
    queryFn: getOpsAccess,
    enabled: signedIn,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  if (!signedIn) {
    return <OperatorSignIn />;
  }

  if (access.isLoading) {
    return (
      <div className="page ops">
        <Skeleton lines={3} label={t('loading.default')} />
      </div>
    );
  }

  // A failed access check and a negative one are the same answer to the person
  // in front of the screen: this is not for you. Neither reveals the queue.
  if (!access.data?.isOperator) {
    return <OperatorSignIn />;
  }

  return <>{children}</>;
}

/**
 * Not an error screen. Needing to sign in is an ordinary state, so it is styled
 * as one and carries the action that resolves it.
 */
function OperatorSignIn() {
  return (
    <div className="page ops">
      <h1>{t('ops.signInRequiredTitle')}</h1>
      <p className="ops__empty">{t('ops.signInRequiredBody')}</p>
      <Link to="/settings" className="btn btn--primary btn--md">
        {t('action.signIn')}
      </Link>
    </div>
  );
}
