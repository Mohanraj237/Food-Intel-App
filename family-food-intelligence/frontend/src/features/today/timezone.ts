/**
 * Household-local day arithmetic for Today (plan §5.2/G-03).
 *
 * Today's day boundary must come from the household's IANA time zone
 * (`MemberToday.timeZone`), never the device clock — a person in Chennai whose
 * phone is still set to a previous trip's zone must see the same "today" the
 * server means. `lib/access.ts` already has `dateKeyInZone`/`previousDateKey`
 * for the access-log feature; this file adds the two operations Today itself
 * needs on top of those — the next day, and the UTC instant range a calendar
 * day covers in a given zone — without editing that file (out of C1's
 * ownership).
 */

/** The day after a `YYYY-MM-DD` key, by calendar arithmetic (mirrors `previousDateKey`). */
export function nextDateKey(dateKey: string): string {
  const [year, month, day] = dateKey.split('-').map(Number);
  const date = new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1));
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

/**
 * The UTC instant at which local midnight begins for `dateKey` in `timeZone`.
 *
 * There is no direct "parse this wall-clock time in this zone" API in the
 * browser, so this converges on it: guess the instant, read what wall-clock
 * date/time that guess reads as in `timeZone`, and correct the guess by the
 * difference. Two passes are enough even across a DST transition, because the
 * offset can change by at most a couple of hours between passes.
 */
export function zonedMidnightUtc(dateKey: string, timeZone: string): Date {
  const [year, month, day] = dateKey.split('-').map(Number);
  let guess = new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1, 0, 0, 0));

  for (let pass = 0; pass < 2; pass++) {
    let formatter: Intl.DateTimeFormat;
    try {
      formatter = new Intl.DateTimeFormat('en-US', {
        timeZone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      });
    } catch {
      // An unrecognised zone: the guess (a UTC-as-local reading) is the best available answer.
      return guess;
    }
    const parts = formatter.formatToParts(guess);
    const part = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? '0');
    const hour = part('hour') % 24; // some engines report midnight as "24"
    const wallAsUtcMs = Date.UTC(part('year'), part('month') - 1, part('day'), hour, part('minute'), part('second'));
    const driftMs = wallAsUtcMs - guess.getTime();
    if (driftMs === 0) break;
    guess = new Date(guess.getTime() - driftMs);
  }

  return guess;
}

/** The `[from, to)` instant range one household-local calendar day covers, as ISO strings. */
export function dayInstantRange(dateKey: string, timeZone: string): { from: string; to: string } {
  return {
    from: zonedMidnightUtc(dateKey, timeZone).toISOString(),
    to: zonedMidnightUtc(nextDateKey(dateKey), timeZone).toISOString(),
  };
}

/** "Sat, 12 Sep" — the `DayNavigator`'s own label, read as a date in `timeZone` (not the
 *  device's), formatted from the calendar key directly so no zone can shift it onto a
 *  neighbouring day. */
export function formatDayLabel(dateKey: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(`${dateKey}T00:00:00Z`));
}
