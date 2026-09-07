import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import './RowGroup.css';

/**
 * A titled group of rows — the consumer "Settings list" pattern (Account,
 * Privacy, Your Data, About…). Before this existed, every such group was its
 * own bordered `Card`, and a screen with several groups nested a border inside
 * a border inside a border. One group is one rounded surface; rows inside it
 * are separated by a single hairline, not a box each.
 */
export function RowGroup({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="row-group">
      {title && <h2 className="row-group__title">{title}</h2>}
      <div className="row-group__list">{children}</div>
    </section>
  );
}

interface RowProps {
  label: string;
  description?: string;
  /** Custom trailing content (e.g. a Switch, a value). Suppresses the default
   *  chevron on a navigable row — supply your own if you still want one. */
  trailing?: ReactNode;
  tone?: 'default' | 'danger';
  to?: string;
  onClick?: () => void;
}

/** One row. Renders as a link (`to`), a button (`onClick`), or a static row
 *  (neither) — a navigable row gets a trailing chevron unless `trailing` is
 *  supplied. */
export function Row({ label, description, trailing, tone = 'default', to, onClick }: RowProps) {
  const navigable = Boolean(to || onClick);
  const content = (
    <>
      <span className="row__text">
        <span className="row__label">{label}</span>
        {description && <span className="row__desc">{description}</span>}
      </span>
      {trailing ? (
        <span className="row__trailing">{trailing}</span>
      ) : (
        navigable && (
          <span className="row__chevron" aria-hidden="true">
            ›
          </span>
        )
      )}
    </>
  );

  const className = tone === 'danger' ? 'row row--danger' : 'row';

  if (to) {
    return (
      <Link to={to} className={className}>
        {content}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button type="button" className={className} onClick={onClick}>
        {content}
      </button>
    );
  }
  return <div className={`${className} row--static`}>{content}</div>;
}
