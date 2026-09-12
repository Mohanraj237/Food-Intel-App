/**
 * Making a catalogue row presentable.
 *
 * The catalogue is assembled from open data and contributions, so a share of
 * rows arrive with the barcode sitting in the name field ("8901063019201"), a
 * retail price fragment appended ("... 30 MRP"), or casing straight from the
 * source ("britannia"). None of that is a rendering bug, but all of it reaches
 * the user as one — and a row a shopper cannot identify is a row they cannot
 * choose, which is the first step of every journey in this product.
 *
 * This normalises presentation only. It never edits stored data and never
 * invents a name: where there is nothing meaningful to show, it says so
 * honestly, so the gap is visible to us and to the user rather than papered
 * over with a plausible-looking guess.
 */

/**
 * A name that is really just the product's barcode.
 *
 * GTIN-8 through GTIN-14, allowing the spaces and hyphens that creep in during
 * manual entry. Requires the *whole* name to be digits: "20-20" (a real
 * biscuit) is four digits but is caught by the length floor, while
 * "Maggi 8901058000252" keeps its brand words and is left alone.
 */
const BARCODE_LIKE = /^[\s-]*\d(?:[\s-]*\d){7,13}[\s-]*$/;

/** Trailing retail-price noise: "30 MRP", "MRP 45", "Rs. 20", "₹52". */
const PRICE_NOISE = /\s*(?:[-–—|,]\s*)?(?:(?:rs\.?|inr|₹)\s*\d+(?:\.\d+)?|\d+(?:\.\d+)?\s*mrp|mrp\s*\d+(?:\.\d+)?)\s*$/gi;

export function isBarcodeLikeName(name: string): boolean {
  return BARCODE_LIKE.test(name.trim());
}

/** Strips trailing price fragments; leaves everything else untouched. */
export function stripPriceNoise(name: string): string {
  let out = name;
  let previous: string;
  do {
    previous = out;
    out = out.replace(PRICE_NOISE, '').trim();
  } while (out !== previous && out.length > 0);
  return out.length > 0 ? out : name.trim();
}

/**
 * Brand names arrive in every casing the source happened to use — "britannia",
 * "PARLE", "Cadbury Bournville". Cards already render the brand in small caps,
 * so casing only matters where it is shown as running text. Title-casing a
 * lowercase source is safe; an already-mixed-case brand is left exactly as its
 * owner wrote it, because "PepsiCo" and "coCoa" must not become "Pepsico".
 */
export function normaliseBrand(brand: string): string {
  const trimmed = brand.trim();
  if (trimmed.length === 0) return trimmed;
  if (trimmed !== trimmed.toLowerCase()) return trimmed;

  return trimmed.replace(/\b[\p{L}][\p{L}'’]*/gu, (word) =>
    word.charAt(0).toUpperCase() + word.slice(1),
  );
}

export interface DisplayNameInput {
  name: string;
  brandName: string;
  // `| undefined` alongside the optional `?` because callers pass a field
  // destructured off a wider object, whose type already carries `undefined`
  // for "never present" as distinct from `null` for "present and empty".
  // `exactOptionalPropertyTypes` treats the two as different unless both are
  // named here — same reason as ProductImage's props.
  packSize?: { value: number | null; unit: string | null } | null | undefined;
  categoryName?: string | null | undefined;
}

export interface DisplayName {
  /** What to render as the product's name. */
  text: string;
  /**
   * `true` when the stored name was unusable and `text` falls back to the
   * category (or brand). Callers mark these rows so they read as "we do not
   * have a name yet" rather than as a confident label.
   */
  isPlaceholder: boolean;
}

/**
 * The name to show for a product row.
 *
 * Returns the name *without* the brand: every surface that shows a product
 * already renders the brand on its own line, and repeating it inside the name
 * reads as a duplication bug. When the stored name is only a barcode, the
 * category is the most specific true thing left, so the row says "Biscuits and
 * cookies" in a muted treatment rather than printing a number no shopper
 * recognises — and `isPlaceholder` lets the caller mark it as a known gap.
 */
export function displayProductName(product: DisplayNameInput): DisplayName {
  const raw = stripPriceNoise(product.name ?? '');

  if (raw.length > 0 && !isBarcodeLikeName(raw)) {
    return { text: raw, isPlaceholder: false };
  }

  const category = product.categoryName?.trim() ?? '';
  if (category.length > 0) {
    return { text: category, isPlaceholder: true };
  }

  const brand = normaliseBrand(product.brandName ?? '');
  if (brand.length > 0) {
    return { text: brand, isPlaceholder: true };
  }

  return { text: raw, isPlaceholder: true };
}
