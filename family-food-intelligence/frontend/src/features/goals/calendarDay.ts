/**
 * Calendar-day helpers that resolve "today" and "n days ago" in a given IANA zone, not the
 * device's own clock.
 *
 * `HabitList.tsx`'s day strip and `TrackerHistoryScreen.tsx`'s range both need a short run of
 * consecutive calendar dates ending in "today" — and "today" is a household-local concept
 * (D-053), not whatever zone the phone happens to be set to. This was flagged directly against
 * `HabitList.tsx` (plan §19 G-03: "`HabitList.tsx` ... compute 'today' from the device clock,
 * not household time zone"). Every date this module hands back is a plain `YYYY-MM-DD`, exactly
 * the shape `/habits/{id}/completions` and the wellness observation endpoints already use.
 */

/** "Today" in `timeZone`, as `YYYY-MM-DD`. `en-CA` is the one built-in locale whose date
 *  formatting is already ISO order, so no manual re-assembly of Y/M/D is needed. */
export function todayInZone(timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

/**
 * Which household-local calendar day an instant (`effectiveAt`/`recordedAt`, an ISO-8601
 * timestamp) falls on. Bucketing a raw timestamp by its first ten characters instead — the
 * UTC date the string happens to start with — is exactly the device/UTC-vs-household-zone
 * confusion this module exists to avoid: an entry made late in the evening in a zone ahead of
 * UTC (India included) can carry a UTC date one day behind the household's own "today".
 */
export function dayInZone(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(instant));
}

/**
 * `n` calendar days before `iso` (`YYYY-MM-DD`). Constructing the `Date` from the parsed
 * components (never from the ISO string or `new Date()` directly) keeps this a pure calendar
 * calculation: once `iso` itself was resolved in the household's zone, subtracting whole days
 * from its Y/M/D parts cannot drift back into the device's zone.
 */
export function isoDaysBefore(iso: string, n: number): string {
  const [year, month, day] = iso.split('-').map(Number) as [number, number, number];
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() - n);
  return toIso(date);
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** `count` dates ending in `todayIso`, oldest first. */
export function lastDaysInZone(todayIso: string, count: number): string[] {
  return Array.from({ length: count }, (_, i) => isoDaysBefore(todayIso, count - 1 - i));
}

function fromIso(date: string): Date {
  const [y = 1970, m = 1, d = 1] = date.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** A single narrow glyph for a day-of-week column header ("M", "T", ...). */
export function narrowDow(date: string, locale = 'en-IN'): string {
  return fromIso(date).toLocaleDateString(locale, { weekday: 'narrow' });
}

/** The day-of-month number, for the cell's own label. */
export function dayOfMonth(date: string): number {
  return fromIso(date).getDate();
}

/** "Mon, 8 Sep" — the accessible name for one day cell. */
export function longDayLabel(date: string, locale = 'en-IN'): string {
  return fromIso(date).toLocaleDateString(locale, { weekday: 'short', day: 'numeric', month: 'short' });
}

/** "Mon 8 Sep" — a chart point's label (no comma, so it reads well beside a value). */
export function shortDayLabel(date: string, locale = 'en-IN'): string {
  return fromIso(date).toLocaleDateString(locale, { weekday: 'short', day: 'numeric', month: 'short' }).replace(',', '');
}
