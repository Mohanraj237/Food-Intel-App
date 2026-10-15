import { allMessages, t, type MessageKey } from './i18n';
import { formatAmount, formatMetricMeasure } from './nutrition';

/**
 * How a wellness metric reads to a person: its name and its value, for any of the server's
 * metric codes (`MetricCode.java`) — not just the handful a goal can be set for.
 *
 * Names are the spec's plain-language labels (wellness-experience §5.3). A code the bundle has
 * no label for yet is humanised rather than shown as a raw enum ("ACTIVE_MINUTES"), so a metric
 * the server adds before the copy does still reads as a name.
 */

const UNIT_SUFFIX = /_(G|MG|ML|KG|KCAL|CM|PCT|BPM|MGDL|MMHG|C|MIN|MINUTES)$/;

export function metricName(code: string): string {
  const label = allMessages()[`metricName.${code}`];
  if (label) return label;
  const words = code.replace(UNIT_SUFFIX, '').toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

const MOOD_KEYS: Record<number, MessageKey> = {
  1: 'mood.label.1',
  2: 'mood.label.2',
  3: 'mood.label.3',
  4: 'mood.label.4',
  5: 'mood.label.5',
};

/**
 * One value in the units a person speaks (spec §6.1): "2.2 L", "6 h 40 min", "4 of 5 · Good",
 * "37.1 °C", "11:30 pm". A clinical reading is never reshaped beyond its unit symbol (D-037).
 */
export function formatMetricValue(code: string, value: number, unit: string): string {
  if (code === 'MOOD_SCORE') {
    const score = Math.round(value);
    const labelKey = MOOD_KEYS[score];
    return labelKey ? t('mood.value', { value: score, label: t(labelKey) }) : t('score.value', { value: score });
  }
  if (unit === 'score') return t('score.value', { value: Math.round(value) });
  if (code === 'BEDTIME_OFFSET_MIN' || code === 'WAKE_OFFSET_MIN') return clockFromOffset(value);
  if (unit === 'C') return `${formatAmount(value)} °C`;
  // BMI reads as a bare number in a tile ("BMI 24.1"); kg/m² belongs in detail views.
  if (unit === 'kg/m2') return formatAmount(value);
  return formatMetricMeasure(value, unit);
}

/** Minutes from midnight (−30 is 11:30 pm the evening before) as a clock time. */
function clockFromOffset(offsetMinutes: number): string {
  const minutes = ((Math.round(offsetMinutes) % 1440) + 1440) % 1440;
  const date = new Date(Date.UTC(2000, 0, 1, Math.floor(minutes / 60), minutes % 60));
  return new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' }).format(date);
}

/** A time of day in the household's zone (D-053): "8:10 am". */
export function formatTimeInZone(instant: string, timeZone: string, locale = 'en-IN'): string {
  return new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', timeZone }).format(new Date(instant));
}
