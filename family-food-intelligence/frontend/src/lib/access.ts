import { request } from './api';
import { getLocale, t, type MessageKey } from './i18n';

/**
 * The access contract: sharing, caregivers and the access log (D-044, D-045).
 *
 * Mirrors `backend/.../access/api/AccessController.java`, `AccessGrantView.java` and
 * `AccessLogService.AccessLogEntry` field for field. Hand-written for the same reason as the
 * other `lib/*.ts` clients: there is no OpenAPI generation yet.
 *
 * The backend is the only authority on who may see or do what. Nothing in this file decides
 * access; the helpers below only arrange what the server returned so a screen can say it in
 * plain language.
 */

// ------------------------------------------------------------------------ types

/** Mirrors `AccessScope.java`. A scope the server declares but does not offer yet
 *  (FOOD_PROFILE, MEDICATIONS, APPOINTMENTS, RECORDS) is never shown as a choice. */
export type AccessScope =
  | 'BODY'
  | 'ACTIVITY'
  | 'SLEEP'
  | 'HYDRATION'
  | 'NUTRITION'
  | 'CLINICAL'
  | 'WELLBEING'
  | 'HABITS'
  | 'FOOD_PROFILE'
  | 'MEDICATIONS'
  | 'APPOINTMENTS'
  | 'RECORDS';

/** VIEW: read. MANAGE: record, log or act on the subject's behalf in that scope; implies VIEW. */
export type AccessLevel = 'VIEW' | 'MANAGE';

/** How the caller stands towards a member. Household role is never an input (D-044). */
export type Relationship = 'SELF' | 'GUARDIAN' | 'CAREGIVER' | 'SHARED' | 'NONE';

/** One row of `GET /access/scopes`. */
export interface ScopeDefinition {
  scope: string;
  kind: string;
  offered: boolean;
}

/** `GET /members/{id}/access` — the caller's relationship and effective levels. */
export interface MemberAccess {
  memberId: string;
  relationship: Relationship;
  scopes: Array<{ scope: AccessScope; level: AccessLevel }>;
}

/**
 * One grant. `audienceLabel`/`subjectLabel` are absent when that person is no longer an active
 * member of the household — the server says so rather than inventing a name.
 *
 * The API's default JSON inclusion is `non_null` (application.yml), and `AccessGrantView` does
 * not override it, so a null field arrives *omitted*, not as `null`. The nullable fields are
 * therefore optional here, and every reader normalises with `?? null`. Comparing
 * `revokedAt !== null` on a live grant would read `undefined` as "revoked".
 */
export interface AccessGrant {
  id: string;
  subjectMemberId: string;
  subjectLabel?: string | null;
  audienceMemberId: string;
  audienceLabel?: string | null;
  scope: AccessScope;
  level: AccessLevel;
  grantedAt: string;
  revokedAt?: string | null;
}

export type AccessLogKind = 'VIEWED' | 'RECORDED' | 'REFUSED';

/**
 * One person-facing access log entry (`AccessLogService.AccessLogEntry`). Null fields arrive
 * omitted, as on {@link AccessGrant}.
 *
 * - `actorMemberId`/`actorLabel` are absent when the actor is no longer in the household.
 * - `scope` is the scope(s) the access concerned when the writer recorded one. A read that
 *   covered several scopes arrives comma-separated ("ACTIVITY,SLEEP"). A refusal carries none.
 * - `relationship` is how the actor stood towards the subject at the time, when recorded.
 */
export interface AccessLogEntry {
  id: string;
  occurredAt: string;
  actorMemberId?: string | null;
  actorLabel?: string | null;
  kind: AccessLogKind;
  resourceType?: string | null;
  scope?: string | null;
  relationship?: string | null;
}

export interface GrantAccessInput {
  audienceMemberId: string;
  scope: AccessScope;
  level: AccessLevel;
}

// --------------------------------------------------------------------- requests

/** Query keys, in one place, so a mutation invalidates exactly what it changed. */
export const ACCESS_QUERY_KEYS = {
  scopes: ['access', 'scopes'] as const,
  member: (memberId: string) => ['access', 'member', memberId] as const,
  grants: (memberId: string) => ['access', 'grants', memberId] as const,
  sharedWithMe: ['access', 'shared-with-me'] as const,
  log: (memberId: string) => ['access', 'log', memberId] as const,
};

function memberPath(memberId: string): string {
  return `/members/${encodeURIComponent(memberId)}`;
}

/** Every scope a grant can name, and whether the product offers it yet. */
export function listAccessScopes(): Promise<ScopeDefinition[]> {
  return request<ScopeDefinition[]>('/access/scopes');
}

/** The caller's relationship to a member, and what they may see or do. */
export function getMemberAccess(memberId: string): Promise<MemberAccess> {
  return request<MemberAccess>(`${memberPath(memberId)}/access`);
}

/** What this member currently shares, and with whom. The member only (403 otherwise). */
export function listAccessGrants(memberId: string): Promise<AccessGrant[]> {
  return request<AccessGrant[]>(`${memberPath(memberId)}/access-grants`);
}

/**
 * Shares one scope of your own data with an adult in your household. Idempotent at the same
 * level; a different level replaces the live grant (the history is kept server-side).
 * Refusals arrive as `ACCESS_GRANT_NOT_ALLOWED` with `details.reason` — see {@link grantErrorKey}.
 */
export function grantAccess(memberId: string, input: GrantAccessInput): Promise<AccessGrant> {
  return request<AccessGrant>(`${memberPath(memberId)}/access-grants`, {
    method: 'POST',
    body: input,
  });
}

/** Stops sharing. Takes effect on the other person's next request. */
export function revokeAccess(memberId: string, grantId: string): Promise<void> {
  return request<void>(`${memberPath(memberId)}/access-grants/${encodeURIComponent(grantId)}`, {
    method: 'DELETE',
  });
}

/** What other household members have shared with the caller. */
export function listSharedWithMe(): Promise<AccessGrant[]> {
  return request<AccessGrant[]>('/me/shared-with-me');
}

/**
 * Who else viewed or recorded this member's health data in the last 90 days, refused attempts
 * included. The member themselves, or a guardian for a child; anyone else gets a 403.
 */
export function getAccessLog(memberId: string, options: { limit?: number } = {}): Promise<AccessLogEntry[]> {
  const query = options.limit !== undefined ? `?limit=${encodeURIComponent(String(options.limit))}` : '';
  return request<AccessLogEntry[]>(`${memberPath(memberId)}/access-log${query}`);
}

// ---------------------------------------------------------------------- scopes

/** The scopes a person can share today, in the order a person thinks about them. */
export type SharingScope = 'ACTIVITY' | 'SLEEP' | 'HYDRATION' | 'NUTRITION' | 'BODY' | 'CLINICAL' | 'WELLBEING' | 'HABITS';

export const SHARING_SCOPE_ORDER: readonly SharingScope[] = [
  'ACTIVITY',
  'SLEEP',
  'HYDRATION',
  'NUTRITION',
  'BODY',
  'CLINICAL',
  'WELLBEING',
  'HABITS',
];

const SCOPE_LABEL_KEYS: Record<SharingScope, MessageKey> = {
  ACTIVITY: 'access.scope.ACTIVITY',
  SLEEP: 'access.scope.SLEEP',
  HYDRATION: 'access.scope.HYDRATION',
  NUTRITION: 'access.scope.NUTRITION',
  BODY: 'access.scope.BODY',
  CLINICAL: 'access.scope.CLINICAL',
  WELLBEING: 'access.scope.WELLBEING',
  HABITS: 'access.scope.HABITS',
};

/** Lower-case nouns for use inside a sentence ("Priya viewed your activity"). */
const SCOPE_NOUN_KEYS: Record<SharingScope, MessageKey> = {
  ACTIVITY: 'access.scopeNoun.ACTIVITY',
  SLEEP: 'access.scopeNoun.SLEEP',
  HYDRATION: 'access.scopeNoun.HYDRATION',
  NUTRITION: 'access.scopeNoun.NUTRITION',
  BODY: 'access.scopeNoun.BODY',
  CLINICAL: 'access.scopeNoun.CLINICAL',
  WELLBEING: 'access.scopeNoun.WELLBEING',
  HABITS: 'access.scopeNoun.HABITS',
};

export function isSharingScope(value: string): value is SharingScope {
  return (SHARING_SCOPE_ORDER as readonly string[]).includes(value);
}

/** Title-case label ("Health readings"). An unknown scope gets a neutral label, never a raw enum. */
export function scopeLabel(scope: string): string {
  return isSharingScope(scope) ? t(SCOPE_LABEL_KEYS[scope]) : t('access.scope.other');
}

/** In-sentence noun ("health readings"). */
export function scopeNoun(scope: string): string {
  return isSharingScope(scope) ? t(SCOPE_NOUN_KEYS[scope]) : t('access.scopeNoun.other');
}

/** "activity, sleep and water" — locale-aware list joining. */
export function joinList(items: readonly string[]): string {
  if (items.length === 0) return '';
  try {
    return new Intl.ListFormat(getLocale(), { style: 'long', type: 'conjunction' }).format(items);
  } catch {
    return items.join(', ');
  }
}

function scopeRank(scope: string): number {
  const index = (SHARING_SCOPE_ORDER as readonly string[]).indexOf(scope);
  return index === -1 ? SHARING_SCOPE_ORDER.length : index;
}

/** Sorts into {@link SHARING_SCOPE_ORDER}; anything unknown goes last, in its original order. */
export function sortScopes<T extends string>(scopes: readonly T[]): T[] {
  return scopes
    .map((scope, index) => ({ scope, index }))
    .sort((a, b) => scopeRank(a.scope) - scopeRank(b.scope) || a.index - b.index)
    .map((entry) => entry.scope);
}

/**
 * The scopes to offer as choices: only those the server marks `offered`, and only those this
 * client knows how to name. A scope the client cannot label is not shown as a choice until the
 * client learns it — rendering a raw enum, or offering a capability we cannot describe, would
 * both be worse than waiting (no UI for features that do not exist).
 */
export function offeredScopes(definitions: readonly ScopeDefinition[]): SharingScope[] {
  const offered = new Set<SharingScope>();
  for (const definition of definitions) {
    if (definition.offered && isSharingScope(definition.scope)) {
      offered.add(definition.scope);
    }
  }
  return SHARING_SCOPE_ORDER.filter((scope) => offered.has(scope));
}

/** The known scopes named by a log entry's `scope` field ("ACTIVITY,SLEEP"), in display order. */
export function parseLogScopes(scope: string | null | undefined): SharingScope[] {
  if (!scope) return [];
  const parsed = scope
    .split(',')
    .map((part) => part.trim())
    .filter(isSharingScope);
  return sortScopes(Array.from(new Set(parsed)));
}

// ----------------------------------------------------------------------- grants

export interface GrantGroup {
  /** The other person: the audience for grants you made, the subject for grants made to you. */
  memberId: string;
  /** `null` when that person is no longer an active household member. */
  label: string | null;
  /** Live grants only, in {@link SHARING_SCOPE_ORDER}. */
  grants: AccessGrant[];
  /** CAREGIVER if any grant is MANAGE, SHARED if all are VIEW (D-044's own rule). */
  relationship: 'CAREGIVER' | 'SHARED';
}

function groupGrants(
  grants: readonly AccessGrant[],
  idOf: (grant: AccessGrant) => string,
  labelOf: (grant: AccessGrant) => string | null,
): GrantGroup[] {
  const groups = new Map<string, GrantGroup>();
  for (const grant of grants) {
    // A revoked grant is history, not something anyone currently has. `??` because a live
    // grant's null `revokedAt` arrives omitted.
    if ((grant.revokedAt ?? null) !== null) continue;
    const id = idOf(grant);
    const existing = groups.get(id);
    if (existing) {
      existing.grants.push(grant);
      if (existing.label === null) existing.label = labelOf(grant);
    } else {
      groups.set(id, { memberId: id, label: labelOf(grant), grants: [grant], relationship: 'SHARED' });
    }
  }

  const result = Array.from(groups.values());
  for (const group of result) {
    const byScope = new Map(group.grants.map((grant) => [grant.scope as string, grant] as const));
    group.grants = sortScopes(Array.from(byScope.keys())).map((scope) => byScope.get(scope) as AccessGrant);
    group.relationship = group.grants.some((grant) => grant.level === 'MANAGE') ? 'CAREGIVER' : 'SHARED';
  }

  // Named people alphabetically; a departed person (no label) last. Ties keep a stable order.
  return result.sort((a, b) => {
    if (a.label === null && b.label !== null) return 1;
    if (a.label !== null && b.label === null) return -1;
    const byLabel = (a.label ?? '').localeCompare(b.label ?? '', getLocale(), { sensitivity: 'base' });
    return byLabel !== 0 ? byLabel : a.memberId.localeCompare(b.memberId);
  });
}

/** Your outgoing grants, one group per person you share with. */
export function groupGrantsByAudience(grants: readonly AccessGrant[]): GrantGroup[] {
  return groupGrants(grants, (g) => g.audienceMemberId, (g) => g.audienceLabel ?? null);
}

/** Grants made to you (`/me/shared-with-me`), one group per person who shares with you. */
export function groupGrantsBySubject(grants: readonly AccessGrant[]): GrantGroup[] {
  return groupGrants(grants, (g) => g.subjectMemberId, (g) => g.subjectLabel ?? null);
}

/**
 * A plain-language message for a refused grant, from `ACCESS_GRANT_NOT_ALLOWED`'s
 * `details.reason`. `null` when the error is something else — the caller then shows the
 * error's own message.
 */
export function grantErrorKey(error: unknown): MessageKey | null {
  if (!isProblem(error) || error.code !== 'ACCESS_GRANT_NOT_ALLOWED') return null;
  const reason = error.details?.['reason'];
  switch (reason) {
    case 'SCOPE_NOT_OFFERED':
      return 'access.error.SCOPE_NOT_OFFERED';
    case 'AUDIENCE_NOT_FOUND':
      return 'access.error.AUDIENCE_NOT_FOUND';
    case 'SELF_GRANT':
      return 'access.error.SELF_GRANT';
    case 'NOT_SAME_HOUSEHOLD':
      return 'access.error.NOT_SAME_HOUSEHOLD';
    case 'AUDIENCE_NOT_ADULT':
      return 'access.error.AUDIENCE_NOT_ADULT';
    default:
      return 'access.error.generic';
  }
}

function isProblem(error: unknown): error is { code: string; details?: Record<string, unknown> | undefined } {
  return typeof error === 'object' && error !== null && typeof (error as { code?: unknown }).code === 'string';
}

// ------------------------------------------------------------------ day buckets

const dayFormatters = new Map<string, Intl.DateTimeFormat>();

function dayFormatter(timeZone: string | undefined): Intl.DateTimeFormat {
  const cacheKey = timeZone ?? '';
  const cached = dayFormatters.get(cacheKey);
  if (cached) return cached;
  let formatter: Intl.DateTimeFormat;
  try {
    formatter = new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      ...(timeZone ? { timeZone } : {}),
    });
  } catch {
    // An unrecognised zone name: fall back to the device's own zone rather than throwing on
    // a screen whose job is to reassure someone.
    formatter = new Intl.DateTimeFormat('en-US', { year: 'numeric', month: '2-digit', day: '2-digit' });
  }
  dayFormatters.set(cacheKey, formatter);
  return formatter;
}

/**
 * The calendar date (`YYYY-MM-DD`) an instant falls on in `timeZone` (the device's zone when
 * omitted). Never `toISOString().slice(0, 10)`, which is the UTC date: at 02:00 in Kolkata that
 * is still yesterday.
 */
export function dateKeyInZone(instant: Date | string | number, timeZone?: string): string {
  const date = instant instanceof Date ? instant : new Date(instant);
  const parts = dayFormatter(timeZone).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** The day before a `YYYY-MM-DD` key, by calendar arithmetic (no zone, no DST involved). */
export function previousDateKey(dateKey: string): string {
  const [year, month, day] = dateKey.split('-').map(Number);
  const date = new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1));
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

export interface AccessLogDay {
  dateKey: string;
  /** `TODAY`/`YESTERDAY` for the two relative headings; `null` means "show the date". */
  relative: 'TODAY' | 'YESTERDAY' | null;
  /** Newest first. */
  entries: AccessLogEntry[];
}

/**
 * Groups log entries by calendar day in `timeZone` (the household's zone, so a day here is the
 * same day the rest of the product means), newest day first and newest entry first within a
 * day. An entry whose timestamp cannot be read cannot be placed on a day and is left out.
 */
export function groupAccessLogByDay(
  entries: readonly AccessLogEntry[],
  options: { now?: Date; timeZone?: string } = {},
): AccessLogDay[] {
  const now = options.now ?? new Date();
  const timeZone = options.timeZone;
  const todayKey = dateKeyInZone(now, timeZone);
  const yesterdayKey = previousDateKey(todayKey);

  const readable = entries
    .map((entry) => ({ entry, time: Date.parse(entry.occurredAt) }))
    .filter((item) => !Number.isNaN(item.time))
    .sort((a, b) => b.time - a.time);

  const days = new Map<string, AccessLogDay>();
  for (const { entry, time } of readable) {
    const dateKey = dateKeyInZone(time, timeZone);
    let day = days.get(dateKey);
    if (!day) {
      day = {
        dateKey,
        relative: dateKey === todayKey ? 'TODAY' : dateKey === yesterdayKey ? 'YESTERDAY' : null,
        entries: [],
      };
      days.set(dateKey, day);
    }
    day.entries.push(entry);
  }
  return Array.from(days.values()).sort((a, b) => b.dateKey.localeCompare(a.dateKey));
}

/** The heading for a day bucket: "Today", "Yesterday", or a date (with the year when it differs). */
export function dayHeading(day: Pick<AccessLogDay, 'dateKey' | 'relative'>, now: Date = new Date(), timeZone?: string): string {
  if (day.relative === 'TODAY') return t('access.log.today');
  if (day.relative === 'YESTERDAY') return t('access.log.yesterday');
  const sameYear = day.dateKey.slice(0, 4) === dateKeyInZone(now, timeZone).slice(0, 4);
  // The key is already a calendar date, so it is formatted as a UTC midnight in UTC: no zone
  // can move it onto a neighbouring day.
  return new Intl.DateTimeFormat(getLocale(), {
    day: 'numeric',
    month: 'long',
    ...(sameYear ? {} : { year: 'numeric' as const }),
    timeZone: 'UTC',
  }).format(new Date(`${day.dateKey}T00:00:00Z`));
}

/** "4:05 pm" in the household's zone. Empty when the timestamp cannot be read. */
export function entryTime(occurredAt: string, timeZone?: string): string {
  const time = Date.parse(occurredAt);
  if (Number.isNaN(time)) return '';
  try {
    return new Intl.DateTimeFormat(getLocale(), {
      timeStyle: 'short',
      ...(timeZone ? { timeZone } : {}),
    }).format(new Date(time));
  } catch {
    return new Intl.DateTimeFormat(getLocale(), { timeStyle: 'short' }).format(new Date(time));
  }
}

// ---------------------------------------------------------------- log sentences

/** Whose log is being read: your own, or a child's you are a guardian of. */
export type LogSubject = { kind: 'SELF' } | { kind: 'CHILD'; label: string };

export interface LogSentence {
  messageKey: MessageKey;
  params: Record<string, string>;
}

const SELF: LogSubject = { kind: 'SELF' };

/** What a refused request was for, when the entry names no scope. */
const REFUSED_RESOURCE_KEYS: Record<string, MessageKey> = {
  HEALTH_OBSERVATION: 'access.log.what.measurements',
  MEAL: 'access.log.what.meals',
  WELLNESS_GOAL: 'access.log.what.goals',
  HABIT: 'access.log.what.habits',
};

/**
 * One log entry as a sentence a person would say: "Priya viewed your activity", "Rahul logged
 * water for you", "Someone no longer in your household viewed your sleep", "A request from
 * Priya for your measurements was refused".
 *
 * The entry carries a kind (viewed / recorded / refused), a resource type and, for most
 * successes, the scope — never the specific metric — so the sentence names the area, not the
 * reading. It never claims more than the entry says.
 */
export function describeLogEntry(entry: AccessLogEntry, subject: LogSubject = SELF): LogSentence {
  const child = subject.kind === 'CHILD';
  const scopes = parseLogScopes(entry.scope);
  const scopeList = scopes.length > 0 ? joinList(scopes.map(scopeNoun)) : null;
  const params: Record<string, string> = child ? { subject: subject.label } : {};

  const pick = (selfKey: MessageKey, childKey: MessageKey): MessageKey => (child ? childKey : selfKey);
  const actorAtStart = entry.actorLabel ?? t('access.log.formerMember');

  switch (entry.kind) {
    case 'VIEWED':
      return {
        messageKey: pick('access.log.viewed', 'access.log.viewedChild'),
        params: { ...params, actor: actorAtStart, what: scopeList ?? t('access.scopeNoun.other') },
      };

    case 'RECORDED':
      switch (entry.resourceType) {
        case 'HEALTH_OBSERVATION':
          return {
            messageKey: pick('access.log.loggedReading', 'access.log.loggedReadingChild'),
            params: { ...params, actor: actorAtStart, what: scopeList ?? t('access.log.what.measurement') },
          };
        case 'MEAL':
          return {
            messageKey: pick('access.log.loggedMeal', 'access.log.loggedMealChild'),
            params: { ...params, actor: actorAtStart },
          };
        case 'WELLNESS_GOAL':
          return {
            messageKey: pick('access.log.changedGoal', 'access.log.changedGoalChild'),
            params: { ...params, actor: actorAtStart },
          };
        case 'HABIT':
          return {
            messageKey: pick('access.log.changedHabit', 'access.log.changedHabitChild'),
            params: { ...params, actor: actorAtStart },
          };
        default:
          return {
            messageKey: pick('access.log.changedOther', 'access.log.changedOtherChild'),
            params: { ...params, actor: actorAtStart, what: scopeList ?? t('access.scopeNoun.other') },
          };
      }

    case 'REFUSED': {
      const resourceKey = entry.resourceType ? REFUSED_RESOURCE_KEYS[entry.resourceType] : undefined;
      const what = scopeList ?? (resourceKey ? t(resourceKey) : t('access.scopeNoun.other'));
      return {
        messageKey: pick('access.log.refused', 'access.log.refusedChild'),
        params: { ...params, actor: entry.actorLabel ?? t('access.log.formerMemberInline'), what },
      };
    }

    default:
      // A kind this client does not know yet. Say only what is certain: someone accessed it.
      return {
        messageKey: pick('access.log.accessed', 'access.log.accessedChild'),
        params: { ...params, actor: actorAtStart, what: scopeList ?? t('access.scopeNoun.other') },
      };
  }
}

/** "As a caregiver" etc. for the entry's relationship at the time; `null` when not recorded. */
export function logRelationshipKey(relationship: string | null | undefined): MessageKey | null {
  switch (relationship) {
    case 'CAREGIVER':
      return 'access.log.via.CAREGIVER';
    case 'SHARED':
      return 'access.log.via.SHARED';
    case 'GUARDIAN':
      return 'access.log.via.GUARDIAN';
    default:
      return null;
  }
}
