import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@/design-system/Card';
import { Button } from '@/design-system/Button';
import { HealthShelfCard } from '@/design-system/HealthShelfCard';
import { EmptyState, ErrorState } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import { t } from '@/lib/i18n';
import { useMeQuery } from '@/lib/me';
import {
  getHealthShelfSummary,
  isHealthShelfEnabled,
  type HealthShelfScenario,
} from '@/lib/healthShelf';
import { formatDoseTime, formatShortDate, formatWeekdayShort } from './format';
import './HealthShelfScreen.css';

/**
 * `/next/health-shelf` — records/medications/appointments, as "your health stuff": a few plain
 * cards, never a document-management table (plan §3/§18 4.2; workstreams doc §5 C5 checklist).
 *
 * BACKEND DEPENDENCY REQUIRED (`lib/healthShelf.ts`) and release-gated on D-049 (PROVISIONAL) —
 * so the real surface below only renders when {@link isHealthShelfEnabled} is true, which is
 * false in every ordinary build. Flip it on to review the finished shell:
 *   - build-time: `VITE_FEATURE_HEALTH_SHELF=true`
 *   - or, without a rebuild, in the browser console: `localStorage.setItem('ffi.flag.healthShelf', '1')`
 *
 * Owned by C5 (workstreams doc §1/§3).
 */
export function HealthShelfScreen() {
  if (!isHealthShelfEnabled()) {
    return <HealthShelfGated />;
  }
  return <HealthShelfShell />;
}

function HealthShelfGated() {
  return (
    <div className="page layout-list">
      <PageHeader title={t('healthShelf.heading')} backTo="/today" backLabel={t('nav.today')} />
      <EmptyState
        icon="clock"
        title={t('healthShelf.gate.badge')}
        body={t('healthShelf.gate.body')}
        action={
          <Link to="/today" className="btn btn--secondary btn--sm">
            {t('next.backToToday')}
          </Link>
        }
      />
    </div>
  );
}

const SCENARIOS: ReadonlyArray<{ value: HealthShelfScenario; label: string }> = [
  { value: 'populated', label: 'Sample data' },
  { value: 'empty', label: 'Nothing added yet' },
  { value: 'error', label: "Can't load" },
];

function HealthShelfShell() {
  const me = useMeQuery();
  const memberId = me.data?.member?.id ?? null;
  const [scenario, setScenario] = useState<HealthShelfScenario>('populated');

  const summary = useQuery({
    queryKey: ['health-shelf', 'summary', memberId, scenario],
    queryFn: () => getHealthShelfSummary(memberId as string, scenario),
    enabled: memberId !== null,
  });

  return (
    <div className="page layout-dashboard">
      <h1>Your health stuff</h1>
      <p className="health-shelf__mocked-banner" role="status">
        MOCKED — this preview isn't connected to a real backend. Nothing here is your actual
        data, and no record, medication or appointment is really saved (D-049, still
        PROVISIONAL).
      </p>

      {summary.isPending || me.isPending ? (
        <ul className="layout-dashboard__grid health-shelf__grid" role="list">
          <HealthShelfCard label="Next dose" fact="" state="loading" />
          <HealthShelfCard label="Appointment" fact="" state="loading" />
          <HealthShelfCard label="Recent" fact="" state="loading" />
        </ul>
      ) : summary.isError ? (
        <ErrorState error={summary.error} onRetry={() => void summary.refetch()} />
      ) : summary.data ? (
        <>
          <ul className="layout-dashboard__grid health-shelf__grid" role="list">
            {summary.data.nextDose ? (
              <HealthShelfCard
                label="Next dose"
                fact={summary.data.nextDose.medicationName}
                dateTime={formatDoseTime(summary.data.nextDose.dueAt)}
              />
            ) : (
              <HealthShelfCard label="Next dose" fact="" state="empty" emptyText="No medication added yet." />
            )}
            {summary.data.nextAppointment ? (
              <HealthShelfCard
                label="Appointment"
                fact={summary.data.nextAppointment.withWhom}
                dateTime={formatWeekdayShort(summary.data.nextAppointment.at)}
              />
            ) : (
              <HealthShelfCard label="Appointment" fact="" state="empty" emptyText="No appointment scheduled." />
            )}
            {summary.data.recentRecord ? (
              <HealthShelfCard
                label="Recent"
                fact={summary.data.recentRecord.label}
                dateTime={formatShortDate(summary.data.recentRecord.recordedOn)}
              />
            ) : (
              <HealthShelfCard label="Recent" fact="" state="empty" emptyText="No records yet." />
            )}
          </ul>
          <div className="health-shelf__add">
            <Button variant="secondary" disabled aria-describedby="health-shelf-add-note">
              + Add
            </Button>
            <p id="health-shelf-add-note" className="health-shelf__add-note">
              Adding a record, medication or appointment isn't available in this preview — it
              needs the records backend first.
            </p>
          </div>
        </>
      ) : null}

      <Card as="div" className="health-shelf__preview-controls">
        <p className="health-shelf__preview-title">Preview controls</p>
        <p className="health-shelf__preview-note">
          Not part of the product — lets a reviewer see every state (loading, empty, error, and
          sample data) without a real backend behind this screen.
        </p>
        <div className="health-shelf__preview-options" role="group" aria-label="Preview scenario">
          {SCENARIOS.map((option) => (
            <Button
              key={option.value}
              variant={scenario === option.value ? 'primary' : 'ghost'}
              size="sm"
              onClick={() => setScenario(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      </Card>

      <p>
        <Link to="/today">Back to Today</Link>
      </p>
    </div>
  );
}
