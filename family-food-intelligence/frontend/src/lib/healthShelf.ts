/**
 * "Your health stuff" — records, medications, appointments (plan §10.3, §18 Journey 4.2).
 *
 * MOCKED — BACKEND DEPENDENCY REQUIRED. Confirmed against `backend/src/main/java/com/familyfood`
 * on 2026-09-12: there is no `records` package, the `ai` package is an empty stub, and
 * `MEDICATIONS`/`APPOINTMENTS`/`RECORDS` exist only as unoffered `AccessScope` enum values —
 * no entity, service, or controller backs any of them. Every function below is served from an
 * in-memory fixture. **Nothing in this file makes a network call.**
 *
 * Suggested contract to hand to backend (plan §10.3), mirrored by the types and mock shapes here
 * so the UI can be redirected at the real thing later with the smallest possible diff:
 *   GET /api/v1/members/{id}/records
 *   GET /api/v1/members/{id}/medications
 *   GET /api/v1/members/{id}/doses
 *   GET /api/v1/members/{id}/appointments
 *
 * Hard constraints from D-049 (PROVISIONAL — this whole domain is release-gated on it, plan §17
 * Phase 4 exit criteria), encoded here as documentation for whoever builds the real client:
 *   - metadata + a private-bucket file reference only; no OCR or AI processing of a medical
 *     document, ever;
 *   - a medication is user-entered free text (name + instructions) — never a structured dosage
 *     the client computes with, and never an interaction check;
 *   - "next dose" is the next entry in a user-entered schedule, never a calculated dose.
 *
 * Release gate: this surface stays behind {@link isHealthShelfEnabled} — off by default — until
 * the backend module exists and D-049 clears (workstreams doc §5, C5 checklist).
 */

import { ApiError } from './api';

// --------------------------------------------------------------------------- types

export interface HealthRecordSummary {
  id: string;
  memberId: string;
  /** User-entered label, e.g. "Blood test" — never a diagnosis or a structured result. */
  label: string;
  /** ISO date the record is dated to, not when it was uploaded. */
  recordedOn: string;
  /** Whether a file is attached (a private-bucket reference server-side) — the client never
   *  sees or processes the file's content itself (D-049). */
  hasFile: boolean;
}

export interface Medication {
  id: string;
  memberId: string;
  /** User-entered text, e.g. "Metformin" — never matched against a drug database here. */
  name: string;
  /** User-entered plain instructions, e.g. "1 tablet after dinner" — displayed verbatim, never
   *  parsed into a dosage the client reasons about. */
  instructions: string;
  active: boolean;
}

export interface UpcomingDose {
  id: string;
  medicationId: string;
  medicationName: string;
  /** ISO datetime of the next entry in the user-entered schedule — never computed from a
   *  frequency rule (D-049 bans dose calculation). */
  dueAt: string;
}

export interface Appointment {
  id: string;
  memberId: string;
  /** e.g. "Dr. Rao" — free text. */
  withWhom: string;
  reason: string | null;
  at: string;
}

export interface HealthShelfSummary {
  nextDose: UpcomingDose | null;
  nextAppointment: Appointment | null;
  recentRecord: HealthRecordSummary | null;
}

/** The three states a reviewer needs to see without a real backend behind this screen (plan
 *  §18's loading/empty/error/success requirement, applied to a mock). */
export type HealthShelfScenario = 'populated' | 'empty' | 'error';

// --------------------------------------------------------------------------- feature flag

const ENV_FLAG_KEY = 'VITE_FEATURE_HEALTH_SHELF';
const STORAGE_FLAG_KEY = 'ffi.flag.healthShelf';

/**
 * Off by default, in every build, with no code path that flips it on its own. A build-time
 * `VITE_FEATURE_HEALTH_SHELF=true` env var is the intended way to turn this on for a real
 * review; a `localStorage.setItem('ffi.flag.healthShelf', '1')` escape hatch exists only so a
 * reviewer can see the finished shell without a rebuild — neither path is reachable through any
 * in-product control, so an ordinary user never finds a way to turn this on themselves.
 */
export function isHealthShelfEnabled(): boolean {
  const envValue = (import.meta.env as Record<string, string | boolean | undefined>)[ENV_FLAG_KEY];
  if (envValue === true || envValue === 'true') return true;
  try {
    return localStorage.getItem(STORAGE_FLAG_KEY) === '1';
  } catch {
    return false;
  }
}

// --------------------------------------------------------------------------- mock fixtures

const MOCK_LATENCY_MS = 400;

function mockDelay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), MOCK_LATENCY_MS));
}

function fixtureFor(memberId: string): HealthShelfSummary {
  return {
    nextDose: {
      id: 'mock-dose-1',
      medicationId: 'mock-med-1',
      medicationName: 'Metformin',
      dueAt: nextAt(20, 0),
    },
    nextAppointment: {
      id: 'mock-appt-1',
      memberId,
      withWhom: 'Dr. Rao',
      reason: null,
      at: nextWeekday(5, 10, 30), // next Friday, 10:30
    },
    recentRecord: {
      id: 'mock-record-1',
      memberId,
      label: 'Blood test',
      recordedOn: daysAgo(10),
      hasFile: true,
    },
  };
}

function nextAt(hour: number, minute: number): string {
  const d = new Date();
  d.setHours(hour, minute, 0, 0);
  if (d.getTime() < Date.now()) d.setDate(d.getDate() + 1);
  return d.toISOString();
}

function nextWeekday(isoWeekday: number, hour: number, minute: number): string {
  const d = new Date();
  const current = d.getDay() === 0 ? 7 : d.getDay();
  const daysAhead = ((isoWeekday - current) % 7 + 7) % 7 || 7;
  d.setDate(d.getDate() + daysAhead);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

/**
 * The one read this screen needs (plan §11's "one source per screen" discipline, applied even to
 * a mock — the shell is written the way the real client will be, not fanned out per card).
 *
 * `scenario` exists only for this file's mock purposes — a real client takes no such parameter;
 * it always returns whatever the server actually has.
 */
export function getHealthShelfSummary(
  memberId: string,
  scenario: HealthShelfScenario = 'populated',
): Promise<HealthShelfSummary> {
  if (scenario === 'error') {
    return mockDelay(undefined).then(() => {
      throw new ApiError({ status: 0, code: 'MOCK_UNAVAILABLE', messageKey: 'error.internal', isTransportFailure: true });
    });
  }
  if (scenario === 'empty') {
    return mockDelay({ nextDose: null, nextAppointment: null, recentRecord: null });
  }
  return mockDelay(fixtureFor(memberId));
}
