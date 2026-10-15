import { t } from '@/lib/i18n';
import './StepIndicator.css';

interface StepIndicatorProps {
  /** 1-based. */
  current: number;
  total: number;
  /** Overrides the default "Step {current} of {total}" text — rarely needed. */
  label?: string;
}

/**
 * "Step 2 of 4" for a multi-step flow (onboarding, the sharing review sheet, the goal/habit add
 * sheet, the ask-for-help wizard — plan §6.3). A flow with no going back and no visible progress
 * reads as a dead end even when it isn't; this is the one small piece that fixes that everywhere
 * it's dropped in.
 *
 * The visible dots are decoration — the heading text already says "Step 2 of 4" and is what a
 * screen reader announces on its own. Each dot still carries a real (visually-hidden) name and,
 * on the current one, `aria-current="step"` (plan §14/2.4.11), so a reading order that lands on
 * the list directly — rather than the heading above it — is not left with unlabelled bullets.
 */
export function StepIndicator({ current, total, label }: StepIndicatorProps) {
  const text = label ?? t('stepIndicator.label', { current, total });
  const steps = Array.from({ length: Math.max(total, 0) }, (_, i) => i + 1);

  return (
    <div className="step-indicator">
      <p className="step-indicator__text">{text}</p>
      {steps.length > 1 && (
        <ol className="step-indicator__dots">
          {steps.map((step) => (
            <li
              key={step}
              className={step === current ? 'step-indicator__dot step-indicator__dot--current' : 'step-indicator__dot'}
              aria-current={step === current ? 'step' : undefined}
            >
              <span className="sr-only">{t('stepIndicator.stepN', { n: step })}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
