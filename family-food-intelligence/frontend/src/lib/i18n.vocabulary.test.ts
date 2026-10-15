import { describe, expect, it } from 'vitest';
import { allMessages } from './i18n';

/**
 * The banned-vocabulary gate (D-004, AC-9.5).
 *
 * The product's single largest liability is a sentence that reads as a medical
 * instruction or as disparagement of a named brand's product. PANTRY 05 §3
 * documents the FMCG exposure; Pack 06 §4 documents the health-language rules.
 * Both reduce to: certain words must not appear in front of a user.
 *
 * Enforcing that in a style guide fails, because copy is written in a hurry by
 * whoever is closest to the feature. Enforcing it in a test that fails the build
 * works. This is that test.
 */

/**
 * Words whose problem is the *claim*, not the word. "We do not diagnose anything"
 * is exactly the disclaimer we want; "we diagnose" is the thing that must never
 * ship. For these, a match is only a violation when it is not negated.
 *
 * The rest of the list has no acceptable form at all — there is no sentence in
 * which telling a user a food is "toxic" becomes appropriate.
 */
const NEGATION = /\b(?:not|never|cannot|can't|don't|doesn't|won't|no)\b[^.]{0,40}$/i;

function isNegated(text: string, matchIndex: number): boolean {
  return NEGATION.test(text.slice(0, matchIndex));
}

/** Each entry says what would go wrong if the word shipped. */
const BANNED: ReadonlyArray<{ pattern: RegExp; reason: string; claimOnly?: boolean }> = [
  { pattern: /\btoxic\b/i, reason: 'implies a safety claim we cannot support' },
  { pattern: /\bpoison(ous)?\b/i, reason: 'implies a safety claim we cannot support' },
  { pattern: /\bdangerous\b/i, reason: 'implies a safety claim we cannot support' },
  { pattern: /\bharmful\b/i, reason: 'a claim about a person’s body, not about food' },
  { pattern: /\bwill harm\b/i, reason: 'a claim about a person’s body, not about food' },
  { pattern: /\bnever eat\b/i, reason: 'a directive instruction; we report, we do not instruct' },
  { pattern: /\bcannot (eat|consume)\b/i, reason: 'the single highest-liability phrasing in the concept' },
  { pattern: /\bmust not (eat|consume)\b/i, reason: 'a directive instruction' },
  { pattern: /\bsafe for everyone\b/i, reason: 'we never claim universal safety' },
  { pattern: /\ballergen[- ]free\b/i, reason: 'we report what a label declares; we never claim free-from' },
  { pattern: /\bfree from allergens\b/i, reason: 'we report what a label declares; we never claim free-from' },
  { pattern: /\bunhealthy\b/i, reason: 'moralises food; the product reports frequency and fit' },
  { pattern: /\bbad food\b/i, reason: 'moralises food' },
  { pattern: /\bjunk food\b/i, reason: 'moralises food' },
  { pattern: /\bclean (food|label|eating)\b/i, reason: 'unscientific marketing framing' },
  { pattern: /\bdetox\b/i, reason: 'unscientific marketing framing' },
  { pattern: /\bsuperfood\b/i, reason: 'unscientific marketing framing' },
  { pattern: /\bcures?\b/i, reason: 'a disease claim', claimOnly: true },
  { pattern: /\bprevents? (disease|diabetes|cancer)\b/i, reason: 'a disease claim', claimOnly: true },
  { pattern: /\byou are deficient\b/i, reason: 'a diagnosis' },
  { pattern: /\bdeficien(t|cy)\b/i, reason: 'a diagnosis; describe the recorded diet instead', claimOnly: true },
  { pattern: /\bdiagnos(e|is|ed)\b/i, reason: 'the product does not diagnose', claimOnly: true },
  { pattern: /\blose weight\b/i, reason: 'no weight targets exist in this product (D-017)', claimOnly: true },
  { pattern: /\bweight loss\b/i, reason: 'no weight targets exist in this product (D-017)', claimOnly: true },
  { pattern: /\bcalorie (target|limit|goal)\b/i, reason: 'no numeric restriction targets (D-017)', claimOnly: true },
  { pattern: /\bburn (calories|fat)\b/i, reason: 'fitness framing is out of scope' },

  // --- plan §9 "Banned-vocabulary extensions" ---
  // claimOnly: a reassurance/disclaimer sentence naming the word while denying it is
  // legitimate copy (e.g. "you haven't missed anything" or "we never say a reading is
  // abnormal"); the affirmative/labelling form is the defect.
  { pattern: /\bmissed\b/i, reason: 'streak/logging-miss shaming language', claimOnly: true },
  { pattern: /\boff track\b/i, reason: 'gamified shaming framing', claimOnly: true },
  { pattern: /\boverweight\b/i, reason: 'a clinical label about a person’s body, not ours to assign', claimOnly: true },
  { pattern: /\bobese\b/i, reason: 'a clinical label about a person’s body, not ours to assign', claimOnly: true },
  { pattern: /\bunderweight\b/i, reason: 'a clinical label about a person’s body, not ours to assign', claimOnly: true },
  { pattern: /\bnormal range\b/i, reason: 'a clinical judgement about a reading, not ours to make', claimOnly: true },
  { pattern: /\babnormal\b/i, reason: 'a clinical judgement about a reading, not ours to make', claimOnly: true },
  { pattern: /\bideal weight\b/i, reason: 'no weight targets exist in this product (D-017)', claimOnly: true },
  { pattern: /\btarget weight\b/i, reason: 'no weight targets exist in this product (D-017)', claimOnly: true },
  { pattern: /\bcalories left\b/i, reason: 'no numeric restriction targets (D-017)', claimOnly: true },
  // The rest have no acceptable form at all — even a negated sentence still leans on the
  // shaming/moralising frame the word carries, so no claimOnly exception is offered.
  { pattern: /\byou broke\b/i, reason: 'a direct-address blame phrase; we report, we do not blame' },
  { pattern: /\bstreak lost\b/i, reason: 'gamified loss framing' },
  { pattern: /\bcheat (meal|day)\b/i, reason: 'moralises food/eating' },
  { pattern: /\bguilt[- ]free\b/i, reason: 'moralising marketing framing' },
  { pattern: /\bpicky\b/i, reason: 'moralises a person’s (often a child’s) eating' },
  { pattern: /\bovereating\b/i, reason: 'moralises a person’s eating' },
];

describe('user-facing copy', () => {
  const messages = allMessages();

  it('contains no banned vocabulary', () => {
    const violations: string[] = [];

    for (const [key, value] of Object.entries(messages)) {
      for (const { pattern, reason, claimOnly } of BANNED) {
        const scan = new RegExp(pattern.source, `${pattern.flags.replace('g', '')}g`);
        let match: RegExpExecArray | null;

        while ((match = scan.exec(value)) !== null) {
          // "We do not diagnose anything" is the disclaimer we want; "we diagnose"
          // is the claim we must never make. Only the affirmative form is a defect.
          if (claimOnly === true && isNegated(value, match.index)) {
            continue;
          }
          violations.push(`${key}: matched ${pattern} — ${reason}\n    "${value}"`);
          break;
        }
      }
    }

    expect(violations, `banned vocabulary in locale bundle:\n  ${violations.join('\n  ')}`)
      .toHaveLength(0);
  });

  it('defines a label and a meaning for every verdict in the closed enum', () => {
    // If the enum grows, the copy must grow with it — a missing label would
    // render a raw enum value like DOESNT_FIT to a user.
    const verdicts = [
      'EVERYDAY',
      'OCCASIONAL',
      'LIMIT',
      'DOESNT_FIT',
      'INSUFFICIENT_DATA',
    ] as const;

    for (const verdict of verdicts) {
      expect(messages[`verdict.${verdict}`], `missing verdict.${verdict}`).toBeTruthy();
      expect(
        messages[`verdict.${verdict}.meaning`],
        `missing verdict.${verdict}.meaning`,
      ).toBeTruthy();
    }
  });

  it('has no verdict label for CONTAINS_ALLERGEN — an allergen is not a verdict', () => {
    // D-004: the allergen flag is a separate field with separate treatment. A
    // verdict label for it would invite rendering it as a chip alongside the others.
    expect(messages['verdict.CONTAINS_ALLERGEN']).toBeUndefined();
    expect(messages['allergen.heading']).toBeTruthy();
  });

  it('states the cross-contamination limit wherever an allergen is declared', () => {
    expect(messages['allergen.body']).toMatch(/cross-contamination/i);
  });

  it('carries a not-medical-advice notice', () => {
    expect(messages['medical.notice']).toMatch(/not medical advice/i);
    expect(messages['medical.notice']).toMatch(/healthcare professional/i);
  });

  it('distinguishes "we do not have this product" from "our service is unavailable"', () => {
    // Constitution §46: conflating these tells a user a product does not exist
    // when in fact we simply could not reach our own service.
    const notFound = messages['error.product_not_found'] ?? '';
    const unavailable = messages['error.service_unavailable'] ?? '';

    expect(notFound).not.toEqual(unavailable);
    expect(unavailable).toMatch(/not the same as the product being missing/i);
  });

  it('has a message for every error code the API can return', () => {
    // Kept in step with ErrorCode.java by hand; a mismatch shows up here rather
    // than as a raw key on a user's screen.
    const required = [
      'error.validation_failed',
      'error.authentication_required',
      'error.invalid_credentials',
      'error.access_denied',
      'error.consent_required',
      'error.guardian_consent_required',
      'error.product_not_found',
      'error.barcode_ambiguous',
      'error.product_data_insufficient',
      'error.image_unreadable',
      'error.ocr_failed',
      'error.rate_limited',
      'error.ai_unavailable',
      'error.service_unavailable',
      'error.internal',
    ];

    for (const key of required) {
      expect(messages[key], `missing ${key}`).toBeTruthy();
    }
  });

  it('never leaves a placeholder unbalanced', () => {
    for (const [key, value] of Object.entries(messages)) {
      const opens = (value.match(/\{/g) ?? []).length;
      const closes = (value.match(/\}/g) ?? []).length;
      expect(opens, `unbalanced placeholder braces in ${key}`).toEqual(closes);
    }
  });
});
