import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/design-system/Button';
import { EmptyState } from '@/design-system/States';
import { PageHeader } from '@/design-system/PageHeader';
import { t } from '@/lib/i18n';
import { ask, isAskEnabled, ASK_INTENTS, type AskIntent } from '@/lib/ai';
import './AskScreen.css';

interface Turn {
  id: string;
  prompt: string;
  answer: string | null;
  loading: boolean;
}

/**
 * `/next/ask` — natural-language questions, answered from a closed catalogue (plan §3/§18 4.4).
 * BACKEND DEPENDENCY REQUIRED, and permanently MOCKED until a model credential and a privacy
 * review exist (D-011, D-052) — see `lib/ai.ts`'s header for the full constraint list.
 *
 * There is deliberately no free-text input anywhere on this screen: D-052 allows only a closed,
 * named set of intents, so the only way to "ask" something here is to tap one of the chips
 * below — there is nothing to type, and nothing this screen forwards anywhere.
 *
 * Gated the same way as the health shelf and notifications: {@link isAskEnabled} defaults false.
 * Owned by C5 (workstreams doc §1/§3).
 */
export function AskScreen() {
  if (!isAskEnabled()) {
    return <AskGated />;
  }
  return <AskShell />;
}

function AskGated() {
  return (
    <div className="page layout-list">
      <PageHeader title={t('ask.heading')} backTo="/today" backLabel={t('nav.today')} />
      <EmptyState
        icon="sparkles"
        title={t('ask.gate.badge')}
        body={t('ask.gate.body')}
        action={
          <Link to="/today" className="btn btn--secondary btn--sm">
            {t('next.backToToday')}
          </Link>
        }
      />
    </div>
  );
}

function AskShell() {
  const [turns, setTurns] = useState<Turn[]>([]);

  async function handleAsk(intent: AskIntent, prompt: string) {
    const id = `${intent}-${Date.now()}`;
    setTurns((prev) => [...prev, { id, prompt, answer: null, loading: true }]);
    const result = await ask(intent);
    setTurns((prev) => prev.map((turn) => (turn.id === id ? { ...turn, answer: result.answer, loading: false } : turn)));
  }

  return (
    <div className="page layout-list">
      <h1>Ask</h1>
      <p className="ask-screen__mocked-banner" role="status">
        MOCKED — Ask isn't connected to a real model or to your data. Every answer below is a
        sample, and none of this is medical advice — talk to a healthcare professional for that.
      </p>

      <ul className="ask-screen__conversation" role="list" aria-live="polite">
        {turns.length === 0 && (
          <li className="ask-screen__hint">Tap a question below to see a sample answer.</li>
        )}
        {turns.map((turn) => (
          <li key={turn.id} className="ask-screen__turn">
            <p className="ask-screen__bubble ask-screen__bubble--question">{turn.prompt}</p>
            {turn.loading ? (
              <p className="ask-screen__bubble ask-screen__bubble--answer" aria-busy="true">
                Thinking…
              </p>
            ) : (
              <p className="ask-screen__bubble ask-screen__bubble--answer">
                <span className="ask-screen__mocked-tag">MOCKED reply</span>
                {turn.answer}
              </p>
            )}
          </li>
        ))}
      </ul>

      <div className="ask-screen__chips" role="group" aria-label="Questions you can ask">
        {ASK_INTENTS.map((option) => (
          <Button
            key={option.intent}
            variant="secondary"
            size="sm"
            onClick={() => void handleAsk(option.intent, option.prompt)}
          >
            {option.prompt}
          </Button>
        ))}
      </div>

      <p>
        <Link to="/today">Back to Today</Link>
      </p>
    </div>
  );
}
