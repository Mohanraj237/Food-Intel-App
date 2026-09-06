import { clearSession, request, setSession, storedRefreshToken } from './api';

/**
 * The auth contract. Mirrors {@code AuthResponse} in
 * backend/.../identity/api/AuthResponse.java exactly — see the note on
 * frontend/src/lib/catalog.ts for why these are hand-written rather than generated.
 *
 * Carries no profile, household or role data by design (D-018): those come from
 * their own consent-checked endpoints, never bundled into a session.
 */
export interface AuthSession {
  accountId: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export function register(email: string, password: string): Promise<AuthSession> {
  return request<AuthSession>('/auth/register', {
    method: 'POST',
    body: { email, password },
    anonymous: true,
  });
}

export function login(email: string, password: string): Promise<AuthSession> {
  return request<AuthSession>('/auth/login', {
    method: 'POST',
    body: { email, password },
    anonymous: true,
  });
}

function refresh(refreshToken: string): Promise<AuthSession> {
  return request<AuthSession>('/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    anonymous: true,
  });
}

export function logout(refreshToken: string): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST', body: { refreshToken } });
}

/** Persists a session returned by register/login/refresh. */
export function applySession(session: AuthSession): void {
  setSession(session);
}

export function signOutLocally(): void {
  clearSession();
}

/**
 * Restores a session from the stored refresh token on app start.
 *
 * The access token itself is never persisted (see api.ts), so without this a
 * signed-in user would appear signed out after every reload despite holding a
 * valid, unexpired refresh token. Silent and best-effort: a failure here just
 * means the user is asked to sign in again, exactly as if they had never had a
 * stored token.
 */
export async function restoreSession(): Promise<boolean> {
  const token = storedRefreshToken();
  if (!token) return false;
  try {
    applySession(await refresh(token));
    return true;
  } catch {
    clearSession();
    return false;
  }
}
