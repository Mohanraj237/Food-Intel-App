import { useSearchParams, Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { useFamilyOverview, type HouseholdOverview, type OverviewMember } from '@/lib/dashboard';
import { useLogSheet } from '@/design-system/LogSheet';
import { FamilyMemberCard, type FamilyMemberCardRow } from '@/design-system/FamilyMemberCard';
import { DayNavigator } from '@/design-system/DayNavigator';
import { WeekStrip } from '@/design-system/WeekStrip';
import { Avatar } from '@/design-system/Avatar';
import { PageHeader } from '@/design-system/PageHeader';
import { Icon } from '@/design-system/Icon';
import { ErrorState, Skeleton } from '@/design-system/States';
import { GoalMeter } from '@/features/goals/GoalMeter';
import { isoDaysBefore, todayInZone } from '@/features/goals/calendarDay';
import {
  canLogFor,
  cardFooterFor,
  cardRowsFor,
  familyDateLabel,
  habitsRowFor,
  updatedTextFor,
  type DayContext,
} from './familyMetrics';
import { FamilySummaryPanel } from './FamilySummaryPanel';
import { LatestUpdates } from './LatestUpdates';
import './FamilyWellness.css';

const DATE_PARAM = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Family Mode: a warm feed of how everyone's doing (plan §18 Journey 3.1) — never a roster, an
 * admin console, or a per-member probe.
 *
 * <p>Reads exactly one endpoint, `GET /households/{id}/overview`, and nothing else. This is the
 * fix for the live G-01 defect: an earlier version called `listObservations`/`goalProgress`/
 * `listHabits`/`visibleCategories` once per member, which wrote a REFUSED row into a *child's own
 * access log* on every visit regardless of whether the viewer could see anything — the exact "the
 * UI never discovers access by trying" rule the plan states in §12. The overview applies the
 * access policy server-side and returns each card pre-filtered, with its week, goals and habits,
 * and a household summary built from those same cards — so there is nothing left to probe for.
 *
 * <p>No card is ranked against another. Server order (you, then children you guard, then by
 * joinedAt) is rendered as given and never re-sorted; the summary has no per-person figure; the
 * updates feed is ordered by time, never by value (D-051).
 */
export function FamilyWellness({ householdId }: { householdId: string }) {
  const [params, setParams] = useSearchParams();
  const rawDate = params.get('date');
  const requested = rawDate && DATE_PARAM.test(rawDate) ? rawDate : undefined;
  const overview = useFamilyOverview(householdId, requested);
  const logSheet = useLogSheet();

  if (overview.isPending) {
    return (
      <div className="page fwell">
        <PageHeader title={t('family.heading')} />
        <div className="fwell__skeleton-summary">
          <Skeleton lines={3} label={t('loading.default')} />
        </div>
        <ul className="fwell__grid" role="list">
          <li><Skeleton lines={5} label={t('loading.default')} /></li>
          <li><Skeleton lines={5} label={t('loading.default')} /></li>
        </ul>
      </div>
    );
  }

  if (overview.isError) {
    return (
      <div className="page fwell">
        <PageHeader title={t('family.heading')} />
        <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />
      </div>
    );
  }

  const data = overview.data;
  const today = todayInZone(data.timeZone);
  const shownDate = requested ?? data.date;
  const ctx: DayContext = { date: data.date, timeZone: data.timeZone, viewingToday: data.date >= today };

  function goTo(date: string) {
    const next = new URLSearchParams(params);
    if (date >= today) next.delete('date');
    else next.set('date', date);
    setParams(next);
  }

  return (
    <div className={overview.isPlaceholderData ? 'page fwell fwell--updating' : 'page fwell'} aria-busy={overview.isPlaceholderData}>
      <PageHeader
        title={t('family.heading')}
        {...(!data.individual ? { eyebrow: data.householdName } : {})}
        {...(!data.individual
          ? {
              actions: (
                <DayNavigator
                  label={familyDateLabel(shownDate)}
                  isToday={shownDate >= today}
                  onPrevious={() => goTo(isoDaysBefore(shownDate, 1))}
                  onNext={() => goTo(isoDaysBefore(shownDate, -1))}
                  onToday={() => goTo(today)}
                />
              ),
            }
          : {})}
      />

      {data.individual ? (
        // AC-2.7, the never-nag rule: a household of one gets exactly this one quiet card, two
        // equal-weight next steps, and nothing else — no badge, banner or modal anywhere else in
        // the product ever references adding family.
        <div className="fwell__solo">
          <p className="fwell__solo-title">{t('family.solo.title')}</p>
          <p className="fwell__solo-body">{t('family.solo.body')}</p>
          <div className="fwell__solo-actions">
            <Link to="/you/household?intent=invite" className="btn btn--secondary btn--md">
              {t('family.solo.inviteAdult')}
            </Link>
            <Link to="/you/household?intent=addChild" className="btn btn--secondary btn--md">
              {t('family.solo.addChild')}
            </Link>
          </div>
        </div>
      ) : (
        <FamilyBody data={data} ctx={ctx} onLog={(subjectId) => logSheet.open({ subjectId })} />
      )}
    </div>
  );
}

function FamilyBody({
  data,
  ctx,
  onLog,
}: {
  data: HouseholdOverview;
  ctx: DayContext;
  onLog: (subjectId: string) => void;
}) {
  const shared = data.members.filter((m) => m.relationship !== 'NONE');
  const notShared = data.members.filter((m) => m.relationship === 'NONE');

  return (
    <>
      {ctx.viewingToday && data.mealsAwaitingConfirmation > 0 && (
        <aside className="fwell__waiting" aria-label={t('familyHub.waitingTitle')}>
          <span className="fwell__waiting-title">{t('familyHub.waitingTitle')}</span>
          <span className="fwell__waiting-body">
            {data.mealsAwaitingConfirmation === 1
              ? t('today.mealsAwaitingOne')
              : t('today.mealsAwaitingMany', { count: data.mealsAwaitingConfirmation })}
          </span>
          <Link to="/meals?filter=awaiting" className="fwell__waiting-link">
            {t('familyHub.waitingReview')} <span aria-hidden="true">›</span>
          </Link>
        </aside>
      )}

      <FamilySummaryPanel overview={data} ctx={ctx} />

      <div className="fwell__layout">
        <section className="fwell__main" aria-labelledby="fwell-people-heading">
          <div className="fwell__section-head">
            <h2 id="fwell-people-heading" className="fwell__section-title">
              {t('familyHub.peopleHeading')}
            </h2>
            <p className="fwell__legend">{t('familyHub.legend')}</p>
          </div>

          <ul className="fwell__grid" role="list">
            {shared.map((member) => (
              <li key={member.memberId}>
                <MemberCard member={member} ctx={ctx} onLog={onLog} />
              </li>
            ))}
          </ul>

          {notShared.length > 0 && <NotSharedList members={notShared} householdName={data.householdName} />}
        </section>

        <div className="fwell__aside">
          <LatestUpdates overview={data} ctx={ctx} />
        </div>
      </div>

      <Link to="/you/household" className="fwell__manage">
        <Icon name="settings" size={18} />
        {t('familyHub.manageHousehold')}
      </Link>
    </>
  );
}

function MemberCard({ member, ctx, onLog }: { member: OverviewMember; ctx: DayContext; onLog: (subjectId: string) => void }) {
  const child = member.memberType === 'CHILD';
  const { rows, overflow } = cardRowsFor(member, ctx);
  const habits = habitsRowFor(member, ctx);

  const cardRows: FamilyMemberCardRow[] = rows.map((row) => ({
    key: row.key,
    label: row.label,
    valueText: row.valueText,
    muted: !row.logged,
    timeText: row.timeText,
    chart: <WeekStrip {...row.strip} selectedDate={ctx.date} />,
    detail: row.goal ? <GoalMeter progress={row.goal} child={child} variant="compact" /> : null,
  }));

  const habitsRow: FamilyMemberCardRow | null = habits
    ? {
        key: 'HABITS',
        label: habits.label,
        valueText: habits.valueText,
        chart: habits.strip ? <WeekStrip {...habits.strip} selectedDate={ctx.date} /> : null,
      }
    : null;

  const openHref = `/family/${member.memberId}${ctx.viewingToday ? '' : `?date=${ctx.date}`}`;

  return (
    <FamilyMemberCard
      name={member.displayLabel}
      relationship={member.relationship}
      updatedText={updatedTextFor(member, ctx)}
      rows={cardRows}
      habits={habitsRow}
      overflowText={overflow.length > 0 ? t('familyHub.also', { categories: overflow.join(', ') }) : null}
      emptyText={t('familyHub.nothingThisWeek')}
      footer={cardFooterFor(member)}
      openHref={openHref}
      onLog={canLogFor(member.relationship) ? () => onLog(member.memberId) : undefined}
    />
  );
}

/**
 * The people who share nothing with you, as one quiet list in server order — not a wall of empty
 * cards. A child you don't look after is not "not shared with you": a child never makes a sharing
 * choice, and their records are reachable only through guardianship (D-044), so they get their
 * own line. Nothing here links anywhere: there is nothing behind it to open.
 */
function NotSharedList({ members, householdName }: { members: OverviewMember[]; householdName: string }) {
  return (
    <section className="fwell__others" aria-labelledby="fwell-others-heading">
      <h3 id="fwell-others-heading" className="fwell__others-title">
        {t('familyHub.othersHeading', { household: householdName })}
      </h3>
      <ul className="fwell__others-list" role="list">
        {members.map((member) => (
          <li key={member.memberId} className="fwell__other">
            <Avatar name={member.displayLabel} size="sm" child={member.memberType === 'CHILD'} />
            <span className="fwell__other-name">{member.displayLabel}</span>
            <span className="fwell__other-note">
              {member.memberType === 'CHILD'
                ? t('familyMemberCard.childGuardedElsewhere')
                : t('familyHub.othersNotShared')}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
