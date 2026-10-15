import type { ReactNode } from 'react';
import './ActivityFeedRow.css';

/**
 * "Who's seen your stuff" (plan §8.2/§18 3.3) — one plain sentence per event, replacing an
 * audit-log table row. No icon-only meaning: the glyph slot is decorative and the sentence
 * always carries the whole fact on its own.
 */
export function ActivityFeedRow({
  sentence,
  time,
  glyph,
}: {
  sentence: ReactNode;
  time: string;
  /** A single decorative character/shape — never the only signal for what kind of event this
   *  is; the sentence itself already says that. */
  glyph?: string;
}) {
  return (
    <li className="activity-feed-row">
      {glyph && (
        <span className="activity-feed-row__glyph" aria-hidden="true">
          {glyph}
        </span>
      )}
      <span className="activity-feed-row__sentence">{sentence}</span>
      <span className="activity-feed-row__time">{time}</span>
    </li>
  );
}

/** One day's worth of rows, under its own heading — the grouping every access/activity feed in
 *  the plan uses (§18 3.3). `headingLevel` keeps the outline from skipping a level when this is
 *  nested inside a screen that already has its own h1/h2. */
export function ActivityFeedDay({
  heading,
  headingLevel = 3,
  children,
}: {
  heading: string;
  headingLevel?: 2 | 3 | 4;
  children: ReactNode;
}) {
  const Heading = `h${headingLevel}` as 'h2' | 'h3' | 'h4';
  return (
    <section className="activity-feed-day">
      <Heading className="activity-feed-day__heading">{heading}</Heading>
      <ul className="activity-feed-day__list" role="list">
        {children}
      </ul>
    </section>
  );
}
