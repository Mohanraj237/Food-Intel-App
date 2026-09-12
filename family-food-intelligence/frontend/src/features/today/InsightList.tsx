import { useQuery } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { insightsForMember, type Insight, type InsightWindow } from '@/lib/insights';
import { Skeleton } from '@/design-system/States';
import './InsightList.css';

/**
 * Derived statements about one member.
 *
 * <p>The design decision worth defending here: an insight whose `availability` is `NO_DATA`
 * or `PARTIAL` is rendered as ordinary content, not hidden and not styled as a warning.
 * "We do not have enough yet to show a trend" is the honest answer early on, and it is also
 * the answer that tells someone what to do next. Hiding it would leave a blank panel that
 * reads as a bug.
 *
 * <p>Evidence is shown on demand rather than always: the number and the sentence are the
 * point, but a reader who wants to check the arithmetic can, which is the whole reason the
 * backend carries evidence on every statement.
 */
export function InsightList({
  memberId,
  window = 'WEEK',
}: {
  memberId: string;
  window?: InsightWindow;
}) {
  const insights = useQuery({
    queryKey: ['insights', memberId, window],
    queryFn: () => insightsForMember(memberId, window),
  });

  if (insights.isPending) {
    return <Skeleton lines={2} label={t('loading.default')} />;
  }

  if (insights.isError) {
    // Deliberately quiet. An insight panel failing is not worth an alarming error box in the
    // middle of a page whose other sections are fine.
    return <p className="insight__unavailable">{t('insight.unavailable')}</p>;
  }

  if (insights.data.length === 0) {
    return <p className="insight__unavailable">{t('insight.none')}</p>;
  }

  return (
    <ul className="insight-list" role="list">
      {insights.data.map((insight, index) => (
        <InsightRow key={`${insight.type}-${index}`} insight={insight} />
      ))}
    </ul>
  );
}

function InsightRow({ insight }: { insight: Insight }) {
  const isFinding = insight.availability === 'SUFFICIENT';

  return (
    <li className={`insight insight--${insight.availability.toLowerCase()}`}>
      <p className="insight__headline">{insight.headline}</p>
      {insight.detail && <p className="insight__detail">{insight.detail}</p>}

      {insight.evidence.length > 0 && (
        <details className="insight__evidence">
          <summary>{t('insight.showEvidence')}</summary>
          <ul className="insight__evidence-list" role="list">
            {insight.evidence.map((e, i) => (
              <li key={i}>
                <span className="insight__evidence-label">{e.label}</span>
                <span className="insight__evidence-value">
                  {formatValue(e.value)} {e.unit}
                </span>
                <span className="insight__evidence-days">
                  {t('insight.evidenceDays', { count: e.observationDays })}
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}

      {!isFinding && <p className="insight__note">{t('insight.notEnoughYet')}</p>}
    </li>
  );
}

/** Whole numbers read as whole numbers; everything else keeps one decimal. */
function formatValue(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}
