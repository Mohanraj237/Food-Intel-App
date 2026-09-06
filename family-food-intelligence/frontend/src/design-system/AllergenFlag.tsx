import { t } from '@/lib/i18n';
import './AllergenFlag.css';

/**
 * The allergen hard flag.
 *
 * Structurally and visually separate from every verdict (D-004). It sits at the
 * top of the screen, uses a colour used nowhere else in the product, and is a
 * banner rather than a chip so it cannot be mistaken for one consideration among
 * several.
 *
 * The wording is "contains", never "will harm you", and the cross-contamination
 * limit is always stated. We report what the manufacturer declared; we never
 * claim a product is free from an allergen (PANTRY 05 §2).
 */
interface AllergenFlagProps {
  /** Display names of declared allergens matching this person's profile. */
  allergens: string[];
  /** True when the label says "may contain" rather than "contains". */
  mayContain?: boolean;
}

export function AllergenFlag({ allergens, mayContain = false }: AllergenFlagProps) {
  if (allergens.length === 0) {
    return null;
  }

  const names = allergens.join(', ');

  return (
    <div className="allergen-flag" role="status">
      <span className="allergen-flag__mark" aria-hidden="true">
        ▲
      </span>
      <div className="allergen-flag__body">
        <p className="allergen-flag__heading">
          {mayContain
            ? t('allergen.mayContain', { allergen: names })
            : t('allergen.heading', { allergen: names })}
        </p>
        <p className="allergen-flag__note">{t('allergen.body')}</p>
      </div>
    </div>
  );
}
