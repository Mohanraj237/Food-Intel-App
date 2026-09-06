/**
 * Category artwork.
 *
 * A shopping app is a visual medium — a wall of text does not read as a place you
 * can browse. But we have no product photography, and inventing one would mean
 * putting an image next to a real brand's name that the brand never produced.
 *
 * So every category gets a deliberately stylised gradient and glyph instead. It
 * gives a card and a tile the visual anchor they need, it is consistent for a
 * given product on every screen, and it is obviously a graphic rather than a
 * photograph of a pack — which is the honest position while `imageUrl` is null.
 * When real, sourced imagery exists, `ProductImage` uses it and this becomes the
 * fallback.
 */

export type CategoryCode =
  | 'BISCUITS'
  | 'BREAKFAST_CEREAL'
  | 'NAMKEEN'
  | 'BEVERAGES'
  | 'MALT_DRINK'
  | 'STAPLES'
  | 'DAIRY'
  | 'EDIBLE_OIL'
  | 'CHOCOLATE'
  | 'INSTANT_NOODLES'
  | 'SAUCES_SPREADS'
  | 'PROTEIN_FOODS'
  | 'CHIPS';

interface CategoryStyle {
  from: string;
  to: string;
  glyph: string;
}

const STYLES: Record<string, CategoryStyle> = {
  BISCUITS: { from: 'var(--cat-biscuits-a)', to: 'var(--cat-biscuits-b)', glyph: '🍪' },
  BREAKFAST_CEREAL: { from: 'var(--cat-cereal-a)', to: 'var(--cat-cereal-b)', glyph: '🥣' },
  NAMKEEN: { from: 'var(--cat-namkeen-a)', to: 'var(--cat-namkeen-b)', glyph: '🥨' },
  BEVERAGES: { from: 'var(--cat-beverages-a)', to: 'var(--cat-beverages-b)', glyph: '🧃' },
  MALT_DRINK: { from: 'var(--cat-malt-a)', to: 'var(--cat-malt-b)', glyph: '🥛' },
  STAPLES: { from: 'var(--cat-staples-a)', to: 'var(--cat-staples-b)', glyph: '🌾' },
  DAIRY: { from: 'var(--cat-dairy-a)', to: 'var(--cat-dairy-b)', glyph: '🧀' },
  EDIBLE_OIL: { from: 'var(--cat-oil-a)', to: 'var(--cat-oil-b)', glyph: '🫙' },
  CHOCOLATE: { from: 'var(--cat-chocolate-a)', to: 'var(--cat-chocolate-b)', glyph: '🍫' },
  INSTANT_NOODLES: { from: 'var(--cat-noodles-a)', to: 'var(--cat-noodles-b)', glyph: '🍜' },
  SAUCES_SPREADS: { from: 'var(--cat-sauces-a)', to: 'var(--cat-sauces-b)', glyph: '🥫' },
  PROTEIN_FOODS: { from: 'var(--cat-protein-a)', to: 'var(--cat-protein-b)', glyph: '🥜' },
  CHIPS: { from: 'var(--cat-chips-a)', to: 'var(--cat-chips-b)', glyph: '🥔' },
};

const DEFAULT_STYLE: CategoryStyle = {
  from: 'var(--cat-default-a)',
  to: 'var(--cat-default-b)',
  glyph: '🛒',
};

export function categoryStyle(code: string | null | undefined): CategoryStyle {
  return (code && STYLES[code]) || DEFAULT_STYLE;
}

/**
 * A stable 0–1 value from the product name, used to vary the gradient angle so a
 * rail of same-category products does not look like one repeated tile.
 */
function hashUnit(seed: string): number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return ((h >>> 0) % 1000) / 1000;
}

export function categoryGradient(code: string | null | undefined, seed = ''): string {
  const { from, to } = categoryStyle(code);
  const angle = Math.round(120 + hashUnit(seed) * 120); // 120deg–240deg
  return `linear-gradient(${angle}deg, ${from} 0%, ${to} 100%)`;
}

export function categoryGlyph(code: string | null | undefined): string {
  return categoryStyle(code).glyph;
}
