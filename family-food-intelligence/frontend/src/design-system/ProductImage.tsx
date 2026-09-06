import { useState } from 'react';
import { categoryGlyph, categoryGradient } from './CategoryArt';

interface ProductImageProps {
  // `| undefined` alongside the optional `?` because callers destructure an
  // optional field off a wider object (e.g. `ProductCardData.imageUrl`) and
  // pass the result straight through — that value's type already carries
  // `undefined` for "the field was never present", distinct from `null` for
  // "present and explicitly absent". `exactOptionalPropertyTypes` treats the
  // two as different unless `undefined` is named here too.
  imageUrl?: string | null | undefined;
  categoryCode?: string | null | undefined;
  /** Varies the fallback gradient's angle so a rail of same-category products
   *  doesn't render as one repeated tile. */
  seed: string;
  /** `''` for a purely decorative image — e.g. a card whose visible text
   *  already names the product. */
  alt: string;
  classNames?: { img?: string; art?: string; glyph?: string };
}

/**
 * The real-image/fallback branch shared by every product visual — a card, the
 * product-detail hero, a future compare column. Previously each consumer
 * duplicated this branch inline (`ProductCard`, `ProductScreen`) with no
 * `onError` handling, so a broken `imageUrl` would render a broken-image icon
 * instead of falling back to `CategoryArt`. This closes that gap for every
 * consumer at once, not just the one being edited.
 *
 * Deliberately owns neither sizing nor responsive breakpoints — the
 * surrounding box (`.pcard__media`, `.pdp__art`, ...) decides how large the
 * image is; this only decides *what* renders inside it. `classNames` lets
 * each consumer keep its own existing class names so their sizing CSS needs
 * no change.
 */
export function ProductImage({ imageUrl, categoryCode, seed, alt, classNames }: ProductImageProps) {
  const [broken, setBroken] = useState(false);

  if (imageUrl && !broken) {
    return (
      <img
        src={imageUrl}
        alt={alt}
        loading="lazy"
        decoding="async"
        className={classNames?.img}
        onError={() => setBroken(true)}
      />
    );
  }

  return (
    <div
      className={classNames?.art}
      style={{ backgroundImage: categoryGradient(categoryCode, seed) }}
      aria-hidden={alt === '' ? true : undefined}
    >
      <span className={classNames?.glyph}>{categoryGlyph(categoryCode)}</span>
    </div>
  );
}
