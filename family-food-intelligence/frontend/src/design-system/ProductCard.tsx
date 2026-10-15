import { Link } from 'react-router-dom';
import { VerdictChip, type Verdict } from './VerdictChip';
import { ProductImage } from './ProductImage';
import { Icon } from './Icon';
import { t } from '@/lib/i18n';
import { formatAmount } from '@/lib/nutrition';
import { displayProductName, normaliseBrand } from '@/lib/productName';
import { toggleSaved, useIsSaved, type SavedProduct } from '@/lib/saved';
import { MAX_COMPARE, toggleTray, useIsInTray } from '@/lib/compareTray';
import { useToast } from './Toast';
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
  /** The product-level star rating, when scored — shown small and after the verdict line (D-003). */
  ratingStars?: number | null;
}

interface ProductCardProps {
  product: ProductCardData;
  /** `rail` is a fixed-width card inside a horizontal carousel. */
  layout?: 'grid' | 'rail';
  /** Hides the save control (e.g. inside a picker). */
  saveable?: boolean;
  /** Shows the add-to-compare control (D-066). On by default wherever saving is. */
  comparable?: boolean;
}

/**
 * The card is the unit of browsing. Media first, then brand, name, size, then the decisive
 * line — the verdict, or the single nutrient that most often decides it. Where a grocery app
 * puts price and "ADD", this puts the fact that matters and a Save.
 *
 * The whole card is one link (a stretched pseudo-element on `.pcard__link`), and the Save
 * control is a sibling button rather than a button nested inside the link — valid HTML and
 * reliably tappable.
 */
export function ProductCard({ product, layout = 'grid', saveable = true, comparable = saveable }: ProductCardProps) {
  const { id, brandName, name, packSize, categoryCode, imageUrl, verdict, headlineFact, allergenFlagged, dataQuality } =
    product;

  const size = packSize?.value != null && packSize.unit ? `${formatAmount(packSize.value)} ${packSize.unit}` : null;
  const needsVerification = dataQuality === 'CONFLICTING' || dataQuality === 'NEEDS_REVIEW';
  const brand = normaliseBrand(brandName);
  const displayName = displayProductName({ name, brandName, packSize, categoryName: product.categoryName ?? null });

  return (
    <article className={`pcard pcard--${layout}`}>
      <Link
        to={`/product/${id}`}
        className="pcard__link"
        aria-label={`${brand} ${displayName.text}${size ? `, ${size}` : ''}${allergenFlagged ? `. ${t('card.allergenFlag')}` : ''}`}
      >
        <span className="sr-only">{displayName.text}</span>
      </Link>

      <div className="pcard__media" aria-hidden="true">
        <ProductImage
          imageUrl={imageUrl}
          categoryCode={categoryCode}
          seed={id + name}
          alt=""
          classNames={{ img: 'pcard__img', art: 'pcard__art', glyph: 'pcard__glyph' }}
        />
        {allergenFlagged && (
          <span className="pcard__allergen">
            <Icon name="alert" size={12} />
          </span>
        )}
      </div>

      {saveable && <SaveButton product={product} />}
      {comparable && <CompareToggle product={product} />}

      <div className="pcard__body" aria-hidden="true">
        <p className="pcard__brand">{brand}</p>
        <p className={displayName.isPlaceholder ? 'pcard__name pcard__name--placeholder' : 'pcard__name'}>
          {displayName.text}
        </p>
        {displayName.isPlaceholder && <p className="pcard__size">{t('product.nameMissing')}</p>}
        {size && <p className="pcard__size">{size}</p>}

        <div className="pcard__decision">
          {verdict ? (
            <VerdictChip verdict={verdict} size="sm" />
          ) : headlineFact ? (
            <span className="pcard__fact">
              <strong>{headlineFact.value}</strong> {headlineFact.label}
            </span>
          ) : (
            <span className="pcard__fact pcard__fact--muted">{t('product.notDeclared')}</span>
          )}
        </div>

        {product.ratingStars != null && (
          <p className="pcard__rating">{t('card.rating', { stars: formatAmount(product.ratingStars) })}</p>
        )}

        {needsVerification && <p className="pcard__flag">{t('product.dataNeedsVerificationShort')}</p>}
      </div>
    </article>
  );
}

function SaveButton({ product }: { product: ProductCardData }) {
  const saved = useIsSaved(product.id);
  const toast = useToast();

  function toggle() {
    const entry: SavedProduct = {
      id: product.id,
      brandName: product.brandName,
      name: product.name,
      packSize: product.packSize ?? null,
      categoryCode: product.categoryCode ?? null,
      categoryName: product.categoryName ?? null,
      imageUrl: product.imageUrl ?? null,
      savedAt: new Date().toISOString(),
    };
    const nowSaved = toggleSaved(entry);
    toast.show(nowSaved ? t('saved.added') : t('saved.removed'), nowSaved ? 'heart-filled' : 'check');
  }

  return (
    <button
      type="button"
      className={saved ? 'pcard__save pcard__save--on' : 'pcard__save'}
      aria-pressed={saved}
      aria-label={saved ? t('saved.removeAction') : t('saved.addAction')}
      onClick={toggle}
    >
      <Icon name={saved ? 'heart-filled' : 'heart'} size={18} />
    </button>
  );
}

/** A sibling button (never inside the card's link), like Save. */
function CompareToggle({ product }: { product: ProductCardData }) {
  const inTray = useIsInTray(product.id);
  const toast = useToast();

  function toggle() {
    const result = toggleTray({
      id: product.id,
      brandName: product.brandName,
      name: product.name,
      imageUrl: product.imageUrl ?? null,
      categoryCode: product.categoryCode ?? null,
    });
    if (result === 'full') toast.show(t('compare.trayFull', { max: MAX_COMPARE }), 'info');
    else toast.show(result === 'added' ? t('compare.addedToTray') : t('compare.removedFromTray'), 'compare');
  }

  return (
    <button
      type="button"
      className={inTray ? 'pcard__compare pcard__compare--on' : 'pcard__compare'}
      aria-pressed={inTray}
      aria-label={inTray ? t('compare.removeFromTray') : t('compare.addToTray')}
      onClick={toggle}
    >
      <Icon name={inTray ? 'check' : 'compare'} size={16} />
    </button>
  );
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
