import type { Insight } from './insights';

/**
 * Shapes `lib/insights.ts`'s `Insight[]` into `RecommendationCard` props (plan §10.2/§13,
 * workstreams doc §4.5) — a pure, no-network composition layer, not a new endpoint. This is the
 * one place that formatting happens, so Today's teaser, Family's teaser and the full
 * `/next/insights` history all read as the same kind of sentence; every caller passes the same
 * `Insight[]` a plain `insightsForMember(memberId, window)` call already returns and gets back
 * `RecommendationCard`-ready data, never a hand-rolled mapping of its own.
 *
 * Two things this deliberately does not do:
 *
 * - **Invent a suggestion.** The plan's pipeline (§13) describes a closed suggestion catalogue
 *   keyed by a `suggestionKey` the backend would attach to an insight; the live `Insight` type
 *   (`lib/insights.ts`) has no such field yet (noted in plan §18 4.1 as a P3 backend
 *   enhancement, not yet shipped). `suggestion` is therefore never populated here — a card with
 *   nothing to suggest just omits that line, rather than this file guessing one.
 * - **Rank or total across members.** Every `Insight` is already about exactly one
 *   `subjectMemberId`; nothing here combines two members' insights into one card or orders
 *   members by any measure (D-038). A per-member feed (Family's teaser, one call per visible
 *   member) is the caller's own composition — this file only ever turns one member's insights
 *   into cards for that member.
 */

export interface RecommendationCardData {
  /** Stable across a re-render of the same list — `${type}-${window}-${subjectMemberId}`. */
  key: string;
  whatHappened: string;
  /** Absent, never `undefined`-valued — matches `RecommendationCardProps`' own optional-field
   *  shape exactly under this codebase's `exactOptionalPropertyTypes`, so `{...card}` spreads
   *  cleanly into `<RecommendationCard>` (design-system, frozen) with no further massaging. */
  whyItMatters?: string;
  coverage: 'present' | 'insufficient-data';
  insufficientText?: string;
  href?: string;
  linkLabel?: string;
}

export interface RecommendationLinkOptions {
  /** A "see more" destination for every card in the list (e.g. `/next/insights`). Omit on the
   *  full history screen itself, which has nowhere further to send the reader. */
  href?: string | undefined;
  linkLabel?: string | undefined;
}

/** One `Insight` as one `RecommendationCard`'s props.
 *
 * Builds the object by conditional spread rather than ever writing an explicit `undefined`
 * into an optional field: `RecommendationCard` (design-system, frozen) declares its optional
 * props as plain `field?: string`, and this codebase's `exactOptionalPropertyTypes` treats a key
 * present with value `undefined` as a different, disallowed thing from the key being absent —
 * so a card with nothing to link to must not have `href` at all, not `href: undefined`. */
export function toRecommendationCard(insight: Insight, options: RecommendationLinkOptions = {}): RecommendationCardData {
  // Type + window + subject is not unique: one member can have two insights of the same type
  // in the same window for different metrics (two NUTRITION_TREND-WEEK cards, say), which
  // collided on one React key and made the second card silently disappear from the list. The
  // evidence labels are what actually distinguish them.
  const evidenceKey = insight.evidence.map((e) => e.label).join('|');
  const key = `${insight.type}-${insight.window}-${insight.subjectMemberId}-${evidenceKey}`;
  const link = {
    ...(options.href !== undefined ? { href: options.href } : {}),
    ...(options.linkLabel !== undefined ? { linkLabel: options.linkLabel } : {}),
  };

  if (insight.availability !== 'SUFFICIENT') {
    // Coverage statements ("not enough recorded yet") are true and useful, but they are not a
    // finding — the card's coverage-aware empty state carries that distinction all the way to
    // the screen, rather than a border colour doing it as the previous `InsightList` did.
    return {
      key,
      whatHappened: insight.headline,
      coverage: 'insufficient-data',
      insufficientText: insight.headline,
      ...link,
    };
  }

  return {
    key,
    whatHappened: insight.headline,
    ...(insight.detail !== null ? { whyItMatters: insight.detail } : {}),
    coverage: 'present',
    ...link,
  };
}

/** `insights.map(toRecommendationCard)`, spelled out once so every caller passes the same
 *  link options to the whole list instead of repeating `.map` at each call site. */
export function toRecommendationCards(
  insights: readonly Insight[],
  options: RecommendationLinkOptions = {},
): RecommendationCardData[] {
  return insights.map((insight) => toRecommendationCard(insight, options));
}
