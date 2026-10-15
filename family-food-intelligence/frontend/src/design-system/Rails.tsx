import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { categoryGlyph, categoryTint } from './CategoryArt';
import { ProductCard, ProductCardSkeleton, type ProductCardData } from './ProductCard';
import { Icon } from './Icon';
import './Rails.css';

/* -------------------------------------------------------------------------- */

interface SectionHeaderProps {
  title: string;
  subtitle?: string;
  actionLabel?: string;
  actionTo?: string;
  /** `h2` by default; `h3` inside a section that already has an h2. */
  as?: 'h2' | 'h3';
}

export function SectionHeader({ title, subtitle, actionLabel, actionTo, as: Heading = 'h2' }: SectionHeaderProps) {
  return (
    <div className="section-head">
      <div className="section-head__text">
        <Heading className="section-head__title">{title}</Heading>
        {subtitle && <p className="section-head__sub">{subtitle}</p>}
      </div>
      {actionLabel && actionTo && (
        <Link className="section-head__action" to={actionTo}>
          {actionLabel}
          <Icon name="chevron-right" size={16} />
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
 * Category tiles: a glyph on a soft tint with the label underneath — "browse by category",
 * a wrapped grid so every category is visible without a horizontal scroll (D-074) — a
 * category is a destination worth seeing all of at a glance, unlike a product rail, which
 * is inherently a long, browsable list. Deliberately stylised, never a stock photo standing
 * in for a real pack (CategoryArt.tsx).
 */
export function CategoryGrid({ categories, linkTo }: { categories: CategoryTileData[]; linkTo?: (code: string) => string }) {
  const targetFor = linkTo ?? ((code: string) => `/category/${encodeURIComponent(code)}`);
  return (
    <div className="cat-grid" role="list">
      {categories.map((c) => (
        <Link key={c.code} to={targetFor(c.code)} className="cat-tile" role="listitem">
          <span className="cat-tile__art" style={{ backgroundColor: categoryTint(c.code) }} aria-hidden="true">
            <span className="cat-tile__glyph">{categoryGlyph(c.code)}</span>
          </span>
          <span className="cat-tile__label">{c.name}</span>
        </Link>
      ))}
    </div>
  );
}

export function CategoryGridSkeleton({ count = 13 }: { count?: number }) {
  return (
    <div className="cat-grid" aria-hidden="true">
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
 * Horizontal carousel: native scroll-snap for a finger swipe (mobile) plus a pair of
 * previous/next arrow buttons that only appear where a mouse, not a thumb, is the more
 * likely pointer (≥1024px, hidden below that in CSS). No carousel library — one ref, one
 * scroll listener to keep the arrows' enabled state honest at either end.
 */
function ProductCarousel({ ariaLabel, children }: { ariaLabel?: string; children: ReactNode }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const [canScrollPrev, setCanScrollPrev] = useState(false);
  const [canScrollNext, setCanScrollNext] = useState(false);

  const updateArrows = useCallback(() => {
    const el = scrollerRef.current;
    if (!el) return;
    const maxScroll = el.scrollWidth - el.clientWidth;
    setCanScrollPrev(el.scrollLeft > 4);
    setCanScrollNext(el.scrollLeft < maxScroll - 4);
  }, []);

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el) return;
    updateArrows();
    el.addEventListener('scroll', updateArrows, { passive: true });
    // Feature-detected, not just for the jsdom test environment — a defensive check is
    // also the right call in any real browser old enough to lack ResizeObserver: the
    // carousel degrades to native scroll/swipe with no arrows re-evaluated on resize,
    // never a crash.
    if (typeof ResizeObserver === 'undefined') {
      return () => el.removeEventListener('scroll', updateArrows);
    }
    const resizeObserver = new ResizeObserver(updateArrows);
    resizeObserver.observe(el);
    return () => {
      el.removeEventListener('scroll', updateArrows);
      resizeObserver.disconnect();
    };
  }, [updateArrows]);

  function scrollByPage(direction: 1 | -1) {
    const el = scrollerRef.current;
    if (!el) return;
    const reducedMotion =
      typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    el.scrollBy({ left: el.clientWidth * 0.9 * direction, behavior: reducedMotion ? 'auto' : 'smooth' });
  }

  return (
    <div className="carousel">
      <div className="rail rail--products scroll-x" role="list" aria-label={ariaLabel} ref={scrollerRef}>
        {children}
      </div>
      <button
        type="button"
        className="carousel__arrow carousel__arrow--prev"
        onClick={() => scrollByPage(-1)}
        disabled={!canScrollPrev}
        aria-label={t('action.previous')}
      >
        <Icon name="chevron-left" size={18} />
      </button>
      <button
        type="button"
        className="carousel__arrow carousel__arrow--next"
        onClick={() => scrollByPage(1)}
        disabled={!canScrollNext}
        aria-label={t('action.next')}
      >
        <Icon name="chevron-right" size={18} />
      </button>
    </div>
  );
}

/** A product rail, riding on {@link ProductCarousel} — the API every existing and future
 *  caller (Home, category pages, alternatives, "related") keeps using unchanged. */
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
    <ProductCarousel>
      {products.map((p) => (
        <div role="listitem" key={p.id} className="rail__item">
          <ProductCard product={p} layout="rail" />
        </div>
      ))}
    </ProductCarousel>
  );
}

/* -------------------------------------------------------------------------- */

/** Responsive product grid: two across on a phone, up to five on a wide desktop. */
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
      {active && <Icon name="check" size={14} />}
      {children}
    </button>
  );
}
