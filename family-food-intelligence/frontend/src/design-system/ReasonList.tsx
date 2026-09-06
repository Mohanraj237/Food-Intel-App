import { describeReason } from '@/lib/reasons';
import type { NutrientRow } from '@/lib/catalog';
import type { Reason } from '@/lib/scoring';
import './ReasonList.css';

/**
 * Renders a closed set of structured, deterministic reasons (D-011 — never
 * free text) as plain sentences. Shared by the per-person "why" disclosure and
 * the product-level health assessment, since both carry the same `Reason[]`
 * shape and need the same honest rendering.
 */
export function ReasonList({ reasons, nutrients }: { reasons: Reason[]; nutrients: NutrientRow[] }) {
  if (reasons.length === 0) {
    return null;
  }

  const lines = reasons.flatMap((reason, i) =>
    describeReason(reason, nutrients).map((line, j) => ({ key: `${i}-${j}`, line })),
  );

  return (
    <ul className="reason-list">
      {lines.map(({ key, line }) => (
        <li key={key} className="reason-list__item">
          {line}
        </li>
      ))}
    </ul>
  );
}
