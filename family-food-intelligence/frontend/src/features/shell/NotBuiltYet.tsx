import { Link } from 'react-router-dom';
import './NotBuiltYet.css';

/**
 * An honest placeholder.
 *
 * The engineering constitution (§47, §65) forbids hiding unfinished functionality
 * behind UI that looks finished. A screen that renders a plausible-looking but
 * inert control is worse than one that says plainly what is not built — the
 * former wastes a reviewer's trust and can survive into a demo unnoticed.
 *
 * This component is removed as each screen is genuinely implemented. Its presence
 * in the tree is a deliberate, visible marker of remaining work.
 */
export function NotBuiltYet({
  title,
  phase,
  what,
}: {
  title: string;
  phase: string;
  what: string;
}) {
  return (
    <div className="page">
      <h1>{title}</h1>
      <div className="not-built">
        <p className="not-built__badge">Not built yet · {phase}</p>
        <p className="not-built__what">{what}</p>
        <p className="not-built__note">
          This screen is a placeholder. Nothing here is wired to real data, and it is
          labelled rather than mocked so it cannot be mistaken for working software.
        </p>
      </div>
      <p>
        <Link to="/">Back to home</Link>
      </p>
    </div>
  );
}
