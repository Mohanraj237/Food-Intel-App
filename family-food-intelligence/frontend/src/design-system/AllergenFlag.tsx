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

/** Lower-cases a leading capital unless the word is an acronym or a proper noun
 *  that keeps its capital wherever it appears. */
function sentenceCase(name: string): string {
  const trimmed = name.trim();
  if (trimmed.length === 0) return trimmed;

  const [first] = trimmed.split(/[\s-]/);
  // All-caps tokens (SO2, INS) and multi-capital words are left alone.
  if (first && first.length > 1 && first === first.toUpperCase()) return trimmed;

  return trimmed.charAt(0).toLowerCase() + trimmed.slice(1);
}

export function AllergenFlag({ allergens, mayContain = false }: AllergenFlagProps) {
  if (allergens.length === 0) {
    return null;
  }

  // These names come from the catalogue in title case ("Gluten-containing
  // cereals"), and are interpolated into the middle of a sentence, which
  // rendered as "Contains Gluten-containing cereals". Lower-casing only a
  // leading capital keeps acronyms and proper nouns intact ("Brazil nuts",
  // "SO2") while letting the sentence read as one.
  const names = allergens.map(sentenceCase).join(', ');

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
