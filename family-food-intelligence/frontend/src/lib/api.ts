import { t, type MessageKey } from './i18n';

/**
 * The HTTP client.
 *
 * Two things it deliberately does:
 *
 * 1. Turns an RFC 7807 problem document into an {@link ApiError} whose message is
 *    resolved from the locale bundle by the server's `messageKey`. The server
 *    never sends English prose, so error copy is reviewed in one place alongside
 *    the rest of the strings (D-019).
 *
 * 2. Distinguishes a transport failure from a 404. `offline` and `serverUnreachable`
 *    are separate states, because telling a user "we do not have this product"
 *    when in fact the network dropped is a lie the product must not tell
 *    (Constitution §46).
 */

const API_BASE = '/api/v1';

export interface ProblemDocument {
  type?: string;
  title?: string;
  status?: number;
  code?: string;
  messageKey?: string;
  correlationId?: string;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly messageKey: string;
  readonly correlationId: string | undefined;
  readonly details: Record<string, unknown> | undefined;
  /** True when the request never reached the server. */
  readonly isTransportFailure: boolean;

  constructor(init: {
    status: number;
    code: string;
    messageKey: string;
    correlationId?: string | undefined;
    details?: Record<string, unknown> | undefined;
    isTransportFailure?: boolean;
  }) {
    const key = init.messageKey as MessageKey;
    super(t(key, init.correlationId ? { correlationId: init.correlationId } : undefined));
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.messageKey = init.messageKey;
    this.correlationId = init.correlationId;
    this.details = init.details;
    this.isTransportFailure = init.isTransportFailure ?? false;
  }

  /** True when retrying the same request could plausibly succeed. */
  get isRetryable(): boolean {
    return this.isTransportFailure || this.status >= 500 || this.status === 429;
  }

  /** True when the user needs to sign in again. */
  get requiresSignIn(): boolean {
    return this.status === 401;
  }
}

// ---------------------------------------------------------------- token storage

/**
 * Access token in memory only — a token in localStorage is readable by any XSS,
 * and this API reaches health data. The refresh token is the one persisted value
 * and it is single-use and revocable, so a theft is detectable and containable.
 *
 * The account id is not a secret — it identifies who is signed in, not what they
 * can do — so it is persisted alongside the refresh token. It lets a screen tell
 * "you" apart from another adult in the same household without a dedicated
 * endpoint for it.
 */
let accessToken: string | null = null;
let accountId: string | null = null;

const REFRESH_KEY = 'ffi.refresh';
const ACCOUNT_KEY = 'ffi.account';

try {
  accountId = localStorage.getItem(ACCOUNT_KEY);
} catch {
  /* private mode or storage blocked — accountId stays null until a session sets it */
}

/**
 * Sign-in state lives outside React (it has to — `request()` reads it on every
 * call, from anywhere), so a screen that renders it needs a way to hear about a
 * change made elsewhere (`useSession.ts` subscribes via `useSyncExternalStore`).
 */
const sessionListeners = new Set<() => void>();

export function subscribeToSession(listener: () => void): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

function notifySessionChanged(): void {
  sessionListeners.forEach((listener) => listener());
}

export function setSession(tokens: {
  accountId: string;
  accessToken: string;
  refreshToken: string;
}): void {
  accessToken = tokens.accessToken;
  accountId = tokens.accountId;
  try {
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    localStorage.setItem(ACCOUNT_KEY, tokens.accountId);
  } catch {
    // Private mode, or site data blocked. The session still works for this tab;
    // the user simply has to sign in again next time.
  }
  notifySessionChanged();
}

export function clearSession(): void {
  accessToken = null;
  accountId = null;
  try {
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(ACCOUNT_KEY);
  } catch {
    /* nothing to clear */
  }
  notifySessionChanged();
}

export function storedRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export function isSignedIn(): boolean {
  return accessToken !== null;
}

/** The signed-in account's id, or `null` when signed out. */
export function currentAccountId(): string | null {
  return accountId;
}

// --------------------------------------------------------------------- request

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  /** Set for endpoints that are usable anonymously (D-020). */
  anonymous?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, anonymous = false } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (accessToken && !anonymous) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      ...(signal ? { signal } : {}),
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    // Never surfaced as "not found". The product genuinely does not know
    // whether the resource exists.
    throw new ApiError({
      status: 0,
      code: 'TRANSPORT_FAILURE',
      messageKey: 'error.service_unavailable',
      isTransportFailure: true,
    });
  }

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDocument = {};
  try {
    problem = (await response.json()) as ProblemDocument;
  } catch {
    // A non-JSON error body (a proxy's HTML 502, say). Fall through to defaults.
  }

  return new ApiError({
    status: response.status,
    code: problem.code ?? 'UNKNOWN',
    messageKey: problem.messageKey ?? 'error.internal',
    correlationId: problem.correlationId,
    details: problem.details,
  });
}
