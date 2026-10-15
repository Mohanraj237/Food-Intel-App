/**
 * Small, local date/time formatters matching the plan's wireframe ("8pm", "Fri", "2 Sep") — not
 * shared with any other workstream's files, kept here rather than in a lib module because they
 * are presentation-only and specific to how `HealthShelfCard`'s `dateTime` line reads.
 */

export function formatDoseTime(iso: string): string {
  const d = new Date(iso);
  const hours24 = d.getHours();
  const minutes = d.getMinutes();
  const ampm = hours24 >= 12 ? 'pm' : 'am';
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12;
  return minutes === 0 ? `${hours12}${ampm}` : `${hours12}:${String(minutes).padStart(2, '0')}${ampm}`;
}

export function formatWeekdayShort(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { weekday: 'short' });
}

export function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}
