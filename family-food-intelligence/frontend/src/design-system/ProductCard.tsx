import { Link } from 'react-router-dom';
import { VerdictChip, type Verdict } from './VerdictChip';
import { categoryGlyph, categoryGradient } from './CategoryArt';
import { t } from '@/lib/i18n';
import './ProductCard.css';

export interface ProductCardData {
  id: string;
  brandName: string;
  name: string;
  packSize?: { value: number | null; unit: string | null } | null;
  categoryCode?: string | null;
  categoryName?: string | null;
  imageUrl?: string | null;
  dataQuality?: string | null;
  /** Present only for a signed-in member with an active consent. */
  verdict?: Verdict | null;
  /** The one number worth reading on a card. */
  headlineFact?: { label: string; value: string } | null;
  allergenFlagged?: boolean;
}

interface ProductCardProps {
  product: ProductCardData;
  /** `rail` is a fixed-width card inside a horizontal carousel. */
  layout?: 'grid' | 'rail';
}

/**
 * The card is the unit of browsing, so its anatomy is the most important layout
 * decision in the product.
 *
 * It follows the shape a quick-commerce shopper already knows — media, brand,
 * name, size, then the decisive line — because that muscle memory is the whole
 * reason to reference those apps. What differs is the last line: where a grocery
 * app puts price and an ADD button, this puts the verdict and the single nutrient
 * that drove it. That substitution is the product.
 *
 * The verdict is absent for anonymous visitors by design (D-020) — a personal
 * result requires a profile, and a profile requires consent. The card then shows
 * the product-level fact instead, which is still useful and still honest.
 */
export function ProductCard({ product, layout = 'grid' }: ProductCardProps) {
  const {
    id, brandName, name, packSize, categoryCode, imageUrl,
    verdict, headlineFact, allergenFlagged, dataQuality,
  } = product;

  const size =
    packSize?.value != null && packSize.unit
      ? `${formatNumber(packSize.value)} ${packSize.unit}`
      : null;

  const needsVerification = dataQuality === 'CONFLICTING' || dataQuality === 'NEEDS_REVIEW';

  return (
    <Link
      to={`/product/${id}`}
      className={`pcard pcard--${layout}`}
      aria-label={`${brandName} ${name}${size ? `, ${size}` : ''}`}
    >
      <div className="pcard__media">
        {imageUrl ? (
          <img src={imageUrl} alt="" loading="lazy" decoding="async" className="pcard__img" />
        ) : (
          <div
            className="pcard__art"
            style={{ backgroundImage: categoryGradient(categoryCode, id + name) }}
            aria-hidden="true"
          >
            <span className="pcard__glyph">{categoryGlyph(categoryCode)}</span>
          </div>
        )}

        {allergenFlagged && (
          <span className="pcard__allergen" title={t('allergen.heading', { allergen: '' })}>
            <span aria-hidden="true">▲</span>
            <span className="sr-only">{t('allergen.heading', { allergen: '' })}</span>
          </span>
        )}
      </div>

      <div className="pcard__body">
        <p className="pcard__brand">{brandName}</p>
        <p className="pcard__name">{name}</p>
        {size && <p className="pcard__size">{size}</p>}

        <div className="pcard__decision">
          {verdict ? (
            <VerdictChip verdict={verdict} size="sm" />
          ) : headlineFact ? (
            <span className="pcard__fact">
              <strong>{headlineFact.value}</strong> {headlineFact.label}
            </span>
          ) : (
            <span className="pcard__fact pcard__fact--muted">
              {t('product.notDeclared')}
            </span>
          )}
        </div>

        {needsVerification && (
          <p className="pcard__flag">{t('product.dataNeedsVerification')}</p>
        )}
      </div>
    </Link>
  );
}

function formatNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

/** Matches the card's exact box sizes so a grid does not reflow when data lands. */
export function ProductCardSkeleton({ layout = 'grid' }: { layout?: 'grid' | 'rail' }) {
  return (
    <div className={`pcard pcard--${layout} pcard--skeleton`} aria-hidden="true">
      <div className="pcard__media">
        <div className="pcard__art pcard__shimmer" />
      </div>
      <div className="pcard__body">
        <div className="pcard__shimmer pcard__sk-line pcard__sk-line--short" />
        <div className="pcard__shimmer pcard__sk-line" />
        <div className="pcard__shimmer pcard__sk-line pcard__sk-line--short" />
        <div className="pcard__shimmer pcard__sk-chip" />
      </div>
    </div>
  );
}
