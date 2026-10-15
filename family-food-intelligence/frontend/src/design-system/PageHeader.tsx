import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/lib/i18n';
import { Icon } from './Icon';

interface PageHeaderProps {
  title: string;
  /** Small uppercase line above the title. */
  eyebrow?: string;
  /** One sentence under the title; never a paragraph. */
  lede?: string;
  /** A destination for the back control; omit for a top-level screen. */
  backTo?: string;
  backLabel?: string;
  /** Right-aligned controls (a day navigator, a primary action). */
  actions?: ReactNode;
  /** Heading level — `h1` for a screen, `h2` when nested. */
  as?: 'h1' | 'h2';
}

/**
 * One anatomy for every screen title, so headings land at the same place and weight on
 * every route instead of each screen re-inventing its own `<h1>` plus a lede plus a back
 * link. Styles live in base.css (`.page-head`).
 */
export function PageHeader({ title, eyebrow, lede, backTo, backLabel, actions, as: Heading = 'h1' }: PageHeaderProps) {
  return (
    <header className="page-head">
      {(backTo || actions) && (
        <div className="page-head__top">
          {backTo ? (
            <Link to={backTo} className="page-head__back">
              <Icon name="arrow-left" size={18} />
              {backLabel ?? t('action.back')}
            </Link>
          ) : (
            <span />
          )}
          {actions && <div className="page-head__actions">{actions}</div>}
        </div>
      )}
      {eyebrow && <p className="page-head__eyebrow">{eyebrow}</p>}
      <Heading className="page-head__title">{title}</Heading>
      {lede && <p className="page-head__lede">{lede}</p>}
    </header>
  );
}
