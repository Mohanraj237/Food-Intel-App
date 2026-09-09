import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { categoryGlyph, categoryTint } from './CategoryArt';
import { ProductCard, ProductCardSkeleton, type ProductCardData } from './ProductCard';
import './Rails.css';

/* -------------------------------------------------------------------------- */

interface SectionHeaderProps {
  title: string;
  subtitle?: string;
  actionLabel?: string;
  actionTo?: string;
}

export function SectionHeader({ title, subtitle, actionLabel, actionTo }: SectionHeaderProps) {
  return (
    <div className="section-head">
      <div className="section-head__text">
        <h2 className="section-head__title">{title}</h2>
        {subtitle && <p className="section-head__sub">{subtitle}</p>}
      </div>
      {actionLabel && actionTo && (
        <Link className="section-head__action" to={actionTo}>
          {actionLabel}
        </Link>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */

export interface CategoryTileData {
  code: string;
  name: string;
}

/**
 * Category tiles.
 *
 * The fastest route into a catalogue for someone who does not yet have a
 * product in mind — and the reason a home screen feels like a shop rather
 * than a search box. A small icon on a soft, flat tint with its label
 * underneath — the same "small icon + caption" shape a grocery app's own
 * category row uses, since that's the pattern that actually reads as
 * "browse by category" rather than "here are some big colourful shapes".
 * Deliberately stylised, never a stock photo standing in for a real pack —
 * see CategoryArt.tsx.
 */
export function CategoryRail({ categories }: { categories: CategoryTileData[] }) {
  return (
    <div className="rail rail--categories scroll-x" role="list">
      {categories.map((c) => (
        <Link
          key={c.code}
          to={`/search?category=${encodeURIComponent(c.code)}`}
          className="cat-tile"
          role="listitem"
        >
          <span
            className="cat-tile__art"
            style={{ backgroundColor: categoryTint(c.code) }}
            aria-hidden="true"
          >
            <span className="cat-tile__glyph">{categoryGlyph(c.code)}</span>
          </span>
          <span className="cat-tile__label">{c.name}</span>
        </Link>
      ))}
    </div>
  );
}

export function CategoryRailSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="rail rail--categories scroll-x" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="cat-tile">
          <span className="cat-tile__art pcard__shimmer" />
          <span className="cat-tile__label pcard__shimmer" style={{ height: 10, width: 44 }} />
        </div>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */

/**
 * Horizontal product carousel with scroll snapping.
 *
 * Native overflow scrolling rather than a carousel library: it is keyboard
 * scrollable, screen-reader traversable and momentum-correct on touch for free,
 * where a JS carousel has to reimplement all three and usually gets one wrong.
 */
export function ProductRail({
  products,
  loading = false,
  skeletonCount = 4,
}: {
  products: ProductCardData[];
  loading?: boolean;
  skeletonCount?: number;
}) {
  if (loading) {
    return (
      <div className="rail rail--products scroll-x" aria-busy="true">
        {Array.from({ length: skeletonCount }, (_, i) => (
          <ProductCardSkeleton key={i} layout="rail" />
        ))}
      </div>
    );
  }

  return (
    <div className="rail rail--products scroll-x" role="list">
      {products.map((p) => (
        <div role="listitem" key={p.id} className="rail__item">
          <ProductCard product={p} layout="rail" />
        </div>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */

/**
 * Responsive product grid: two across on a phone, up to five on a wide desktop.
 * `auto-fill` with a min track means the count adapts to the container rather
 * than to a breakpoint list that drifts out of step with the card width.
 */
export function ProductGrid({
  products,
  loading = false,
  skeletonCount = 8,
}: {
  products: ProductCardData[];
  loading?: boolean;
  skeletonCount?: number;
}) {
  if (loading) {
    return (
      <div className="pgrid" aria-busy="true">
        {Array.from({ length: skeletonCount }, (_, i) => (
          <ProductCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  return (
    <div className="pgrid" role="list">
      {products.map((p) => (
        <div role="listitem" key={p.id}>
          <ProductCard product={p} />
        </div>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */

export function Chip({
  children,
  active = false,
  onClick,
  as = 'button',
  to,
}: {
  children: ReactNode;
  active?: boolean;
  onClick?: () => void;
  as?: 'button' | 'link';
  to?: string;
}) {
  const className = active ? 'chip chip--active' : 'chip';

  if (as === 'link' && to) {
    return (
      <Link to={to} className={className}>
        {children}
      </Link>
    );
  }
  return (
    <button type="button" className={className} onClick={onClick} aria-pressed={active}>
      {children}
    </button>
  );
}
