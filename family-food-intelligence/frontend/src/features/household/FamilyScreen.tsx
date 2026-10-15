import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { useIsSignedIn } from '@/lib/useSession';
import { useMeQuery } from '@/lib/me';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { ErrorState, Skeleton } from '@/design-system/States';
import { FamilyWellness } from './FamilyWellness';
import './FamilyScreen.css';

/**
 * `/family` — "How is my family doing?" (plan §18 Journey 3.1).
 *
 * This screen used to also carry the household-setup UI (invite an adult, add a child, leave,
 * remove, erase) and a readiness/allergens card. Both moved out (workstreams doc §3/§0.5): setup
 * lives at `/you/household` now (`HouseholdSettingsScreen.tsx`), and the readiness card moved to
 * `/food` per plan G-06 as `features/home/FoodReadinessCard.tsx` (C2's file — it reads this
 * workstream's `useHouseholdOverview` hook rather than duplicating it; the old `HouseholdOverview`
 * *component* that used to render here is deleted as dead code now that nothing imports it).
 * What remains here is exactly the warm feed `FamilyWellness` renders, so this file is now only
 * account/session-level gating: is someone signed in, has `/me` answered yet, do they have a
 * household at all.
 */
export function FamilyScreen() {
  useDocumentTitleKey('family.heading');
  const signedIn = useIsSignedIn();
  const me = useMeQuery({ enabled: signedIn });

  if (!signedIn) {
    return (
      <div className="page">
        <h1>{t('family.heading')}</h1>
        <p>{t('family.signInPrompt')}</p>
        <Link to="/you" className="btn btn--primary btn--md">
          {t('action.signIn')}
        </Link>
      </div>
    );
  }

  if (me.isPending) {
    return (
      <div className="page">
        <h1>{t('family.heading')}</h1>
        <Skeleton lines={3} label={t('loading.default')} />
      </div>
    );
  }

  if (me.isError) {
    return (
      <div className="page">
        <h1>{t('family.heading')}</h1>
        <ErrorState error={me.error} onRetry={() => void me.refetch()} />
      </div>
    );
  }

  // Defensive only: `RequireHousehold` (App.tsx) already redirects `mode = NONE` to `/welcome`
  // before this screen mounts. If it is ever reached anyway, say why plainly rather than read
  // `.household.id` off a null value.
  if (me.data.household === null) {
    return (
      <div className="page">
        <h1>{t('family.heading')}</h1>
        <p>{t('today.needsHousehold')}</p>
        <Link to="/welcome" className="btn btn--primary btn--md">
          {t('household.create')}
        </Link>
      </div>
    );
  }

  return <FamilyWellness householdId={me.data.household.id} />;
}
