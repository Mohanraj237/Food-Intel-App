/**
 * Ask — natural-language questions, AI-phrased summaries (plan §10.3, §18 Journey 4.4).
 *
 * MOCKED — BACKEND DEPENDENCY REQUIRED, and unlike the rest of C5's surfaces this one stays
 * mocked on purpose even after a backend `ai` module exists: D-011 requires a model credential
 * and a privacy review to be in place first, and confirmed on 2026-09-12, the backend `ai`
 * package is an empty stub directory — no code at all.
 *
 * Suggested contract to hand to backend (plan §10.3):
 *   POST /api/v1/ask                              { intent, memberId } -> AskAnswer
 *   GET  /api/v1/members/{id}/summary?period=      -> nothing typed here yet; out of scope
 *                                                     for this pass beyond the note above
 *
 * Hard, permanent constraints this file is written to make structurally hard to violate, not
 * just documented:
 *   - D-052: only a closed, named set of intents is ever answered. There is no free-text input
 *     forwarded anywhere in this file — {@link ask} takes an {@link AskIntent}, not a string,
 *     so a future edit cannot accidentally wire a text box straight into a "prompt".
 *   - D-011: AI never originates a number or a verdict. Every canned answer below is a fixed
 *     template with no interpolated figure, and each one is prefixed with an explicit "this is a
 *     sample answer" disclaimer so it can never be mistaken for a real computed result.
 *   - Never medical advice. No answer states a diagnosis, a treatment, a dosage, or a safety
 *     claim about a food or a body — only what the product already reports elsewhere, restated
 *     in a sentence.
 *   - Never sent to a model provider. This module makes no network call, and never will be
 *     given a child's data — there is no `memberType` parameter to accidentally check and skip
 *     here at all, because there is nothing to send to anyone.
 */

// --------------------------------------------------------------------------- types

export type AskIntent =
  | 'HOW_AM_I_DOING_TODAY'
  | 'WATER_THIS_WEEK'
  | 'NEXT_DOSE'
  | 'NEXT_APPOINTMENT'
  | 'WHAT_SHOULD_I_EAT';

export interface AskIntentOption {
  intent: AskIntent;
  /** The plain-language question shown as a tappable chip — the entire "input" this screen
   *  offers (D-052: closed intents only, no free-text box). */
  prompt: string;
}

export const ASK_INTENTS: readonly AskIntentOption[] = [
  { intent: 'HOW_AM_I_DOING_TODAY', prompt: 'How am I doing today?' },
  { intent: 'WATER_THIS_WEEK', prompt: 'How is my water this week?' },
  { intent: 'NEXT_DOSE', prompt: "When's my next dose?" },
  { intent: 'NEXT_APPOINTMENT', prompt: "What's my next appointment?" },
  { intent: 'WHAT_SHOULD_I_EAT', prompt: 'What should I eat next?' },
];

export interface AskAnswer {
  intent: AskIntent;
  /** A fixed template, never an interpolated figure — see the file header. */
  answer: string;
  mocked: true;
}

const CANNED_ANSWERS: Record<AskIntent, string> = {
  HOW_AM_I_DOING_TODAY:
    'Once this is connected, this would summarize what you logged today in plain language — no score, no grade.',
  WATER_THIS_WEEK:
    "Once this is connected, this would say how many of the last 7 days you logged water, the same way Today's cards do.",
  NEXT_DOSE:
    'Once this is connected, this would name your next scheduled dose and when it is due, exactly as you entered it — never a calculated dosage.',
  NEXT_APPOINTMENT:
    'Once this is connected, this would name your next appointment and when it is — exactly what you or a family member entered.',
  WHAT_SHOULD_I_EAT:
    'Once this is connected, this would suggest something from the Food tab based on your own recent choices — never a medical or nutritional directive.',
};

// --------------------------------------------------------------------------- mock call

const MOCK_LATENCY_MS = 500;

/**
 * Always answers, always mocked, never calls anything. `intent` is a closed enum, not a string —
 * the type system is the enforcement for D-052 here, not a runtime check that could be forgotten.
 */
export function ask(intent: AskIntent): Promise<AskAnswer> {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({ intent, answer: CANNED_ANSWERS[intent], mocked: true });
    }, MOCK_LATENCY_MS);
  });
}

/**
 * MOCKED, off by default, same pattern as the sibling BACKEND DEPENDENCY REQUIRED modules — kept
 * here even though {@link ask} can never call a real provider anyway, so the Ask screen is
 * gated the same visible way as the health shelf rather than being a special case to remember.
 */
const ENV_FLAG_KEY = 'VITE_FEATURE_ASK';
const STORAGE_FLAG_KEY = 'ffi.flag.ask';

export function isAskEnabled(): boolean {
  const envValue = (import.meta.env as Record<string, string | boolean | undefined>)[ENV_FLAG_KEY];
  if (envValue === true || envValue === 'true') return true;
  try {
    return localStorage.getItem(STORAGE_FLAG_KEY) === '1';
  } catch {
    return false;
  }
}
