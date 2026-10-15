import { useQuery } from '@tanstack/react-query';
import { HealthShelfCard } from '@/design-system/HealthShelfCard';
import { getHealthShelfSummary, isHealthShelfEnabled } from '@/lib/healthShelf';
import { formatDoseTime, formatWeekdayShort } from './format';
import './HealthShelfTeaser.css';

/**
 * A compact "your health stuff" teaser for Today/You (plan §18 4.2's own route line — cards on
 * Today/You, detail at `/next/health-shelf`). Not wired into either screen by this pass: Today
 * (`features/today/**`) and You (`features/settings/SettingsScreen.tsx`) are C1's files, outside
 * this workstream's ownership (workstreams doc §1) — this component exists so C1 can drop it in
 * with a one-line import (`<HealthShelfTeaser memberId={me.member.id} />`) rather than needing to
 * duplicate the flag/query logic.
 *
 * Renders nothing at all — not even a loading flicker — when {@link isHealthShelfEnabled} is
 * false, so importing this early into a live screen is always safe: it stays invisible until
 * the flag is actually turned on for a review build.
 */
export function HealthShelfTeaser({ memberId }: { memberId: string }) {
  const enabled = isHealthShelfEnabled();
  const summary = useQuery({
    queryKey: ['health-shelf', 'summary', memberId, 'populated'],
    queryFn: () => getHealthShelfSummary(memberId, 'populated'),
    enabled,
  });

  if (!enabled) return null;

  return (
    <section aria-label="Your health stuff">
      <ul className="health-shelf-teaser__list" role="list">
        {summary.isPending ? (
          <>
            <HealthShelfCard label="Next dose" fact="" state="loading" />
            <HealthShelfCard label="Appointment" fact="" state="loading" />
          </>
        ) : summary.data ? (
          <>
            {summary.data.nextDose && (
              <HealthShelfCard
                label="Next dose"
                fact={summary.data.nextDose.medicationName}
                dateTime={formatDoseTime(summary.data.nextDose.dueAt)}
                href="/next/health-shelf"
              />
            )}
            {summary.data.nextAppointment && (
              <HealthShelfCard
                label="Appointment"
                fact={summary.data.nextAppointment.withWhom}
                dateTime={formatWeekdayShort(summary.data.nextAppointment.at)}
                href="/next/health-shelf"
              />
            )}
          </>
        ) : null}
      </ul>
    </section>
  );
}
