import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import './RecommendationCard.css';

interface RecommendationCardProps {
  /** What happened — evidence-backed, e.g. "Water logged on 5 of 7 days this week." */
  whatHappened: string;
  /** Why it matters — tied to the person's own goal, e.g. "That's short of your 2 L goal on
   *  most days." Omitted when there's nothing more specific to say than `whatHappened`. */
  whyItMatters?: string;
  /** One non-medical suggestion from a closed catalogue (plan §13) — never invented per-user. */
  suggestion?: string;
  /** Optional "see more" destination — a tracker history page, `/next/insights`, and so on. */
  href?: string;
  linkLabel?: string;
  /** `insufficient-data` renders the coverage-aware empty state instead of the three lines
   *  above — the card never invents a number when there isn't enough evidence yet. */
  coverage?: 'present' | 'insufficient-data';
  /** Overrides the default "Not enough data yet…" sentence for the insufficient state. */
  insufficientText?: string;
}

/**
 * The "what should I do next" content unit (plan §8.2/§13) — one insight or one nudge, rendered
 * as a plain sentence card. Icon-free and colour-neutral on purpose: `--attention-bg`/
 * `--attention-rule` (never `--danger`, never a verdict colour) are the only visual signal, and
 * every word is stated as fact, never as a grade.
 *
 * Insights are derived, never stored (plan §13) — there is no dismiss button here, because the
 * card is only ever rendered while its evidence holds; it is the caller's job to stop passing it
 * one once the underlying insight has expired.
 */
export function RecommendationCard({
  whatHappened,
  whyItMatters,
  suggestion,
  href,
  linkLabel,
  coverage = 'present',
  insufficientText,
}: RecommendationCardProps) {
  const body: ReactNode =
    coverage === 'insufficient-data' ? (
      <p className="recommendation-card__line">
        {insufficientText ?? t('recommendationCard.insufficientData')}
      </p>
    ) : (
      <>
        <p className="recommendation-card__line recommendation-card__line--lead">{whatHappened}</p>
        {whyItMatters && <p className="recommendation-card__line">{whyItMatters}</p>}
        {suggestion && <p className="recommendation-card__line recommendation-card__suggestion">{suggestion}</p>}
      </>
    );

  const content = (
    <div className="recommendation-card">
      {body}
      {href && coverage === 'present' && (
        <span className="recommendation-card__chevron" aria-hidden="true">
          ›
        </span>
      )}
    </div>
  );

  if (href && coverage === 'present') {
    return (
      <Link to={href} className="recommendation-card__link" aria-label={linkLabel}>
        {content}
      </Link>
    );
  }
  return content;
}
