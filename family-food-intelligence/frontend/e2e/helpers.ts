import type { Page, BrowserContext } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

/** Overridable so an audit can run against a second stack (e.g. a branch build on :8091). */
export const API = process.env.E2E_API ?? 'http://localhost:8090/api/v1';
/** Overridable so a redesign pass can capture beside the previous set rather than over it. */
export const SHOTS = path.resolve(process.env.E2E_SHOTS ?? 'e2e/screenshots');

export type Session = { accountId: string; refreshToken: string; accessToken: string; email: string };

export function shotDir(sub: string): string {
  const dir = path.join(SHOTS, sub);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

export const DEMO_PASSWORD = 'DemoFamily2026!seed';

/** Signs in an existing demo-family account. */
export async function login(email: string, password = DEMO_PASSWORD): Promise<Session> {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`login failed for ${email}: ${res.status} ${await res.text()}`);
  const body = (await res.json()) as { accountId: string; refreshToken: string; accessToken: string };
  return { ...body, email };
}

/** Registers a throwaway account straight against the API (faster and less brittle than driving the sign-up form). */
export async function registerAccount(prefix = 'audit'): Promise<Session> {
  const email = `${prefix}.${Date.now()}.${Math.floor(Math.random() * 10000)}@example.com`;
  const password = 'AuditPassphrase2026!';
  const res = await fetch(`${API}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`register failed: ${res.status} ${await res.text()}`);
  const body = (await res.json()) as { accountId: string; refreshToken: string; accessToken: string };
  return { ...body, email };
}

/**
 * Puts the session where the app looks for it (refresh token + account id in localStorage;
 * access token is memory-only by design).
 *
 * Written once via an evaluate + reload rather than an init script: the backend rotates the
 * refresh token on every use, so an init script that re-seeds the original token on each
 * navigation hands the app an already-spent token and every later page loads signed out.
 */
export async function applySession(page: Page, session: Session): Promise<void> {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.evaluate(
    ([refresh, account]) => {
      localStorage.setItem('ffi.refresh', refresh);
      localStorage.setItem('ffi.account', account);
    },
    [session.refreshToken, session.accountId] as const,
  );
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
}

/** Fails loudly if the app is not actually authenticated — a silent signed-out run invalidates every finding. */
export async function assertSignedIn(page: Page): Promise<void> {
  await page.goto('/you', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200);
  const text = await page.locator('body').innerText();
  if (/Sign in|Create account/i.test(text) && !/Sign out/i.test(text)) {
    throw new Error('Session not established — /you still offers sign-in. Aborting: findings would be signed-out state.');
  }
}

export type PageProbe = {
  route: string;
  url: string;
  title: string;
  headings: string[];
  navLabels: string[];
  buttons: string[];
  bodyText: string;
  consoleErrors: string[];
  failedRequests: string[];
  horizontalOverflow: boolean;
  mainMaxWidthPx: number | null;
  screenshot: string;
};

/** Attaches console/network error collectors; returns the arrays they fill. */
export function collectErrors(page: Page) {
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300));
  });
  page.on('pageerror', (e) => consoleErrors.push(`pageerror: ${String(e).slice(0, 300)}`));
  page.on('response', (r) => {
    if (r.status() >= 400) failedRequests.push(`${r.status()} ${r.request().method()} ${r.url()}`);
  });
  return { consoleErrors, failedRequests };
}

export async function probe(
  page: Page,
  route: string,
  label: string,
  sub: string,
  errors: { consoleErrors: string[]; failedRequests: string[] },
): Promise<PageProbe> {
  const before = { c: errors.consoleErrors.length, f: errors.failedRequests.length };
  await page.goto(route, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200); // let react-query settle

  const headings = await page.locator('h1, h2').allInnerTexts().catch(() => []);
  const navLabels = await page.locator('nav a, nav button').allInnerTexts().catch(() => []);
  const buttons = await page.locator('button:visible').allInnerTexts().catch(() => []);
  const bodyText = (await page.locator('body').innerText().catch(() => '')).slice(0, 2500);

  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  const mainMaxWidthPx = await page.evaluate(() => {
    const main = document.querySelector('main');
    if (!main) return null;
    return Math.round(main.getBoundingClientRect().width);
  });

  const file = path.join(shotDir(sub), `${label}.png`);
  await page.screenshot({ path: file, fullPage: true });

  return {
    route,
    url: page.url(),
    title: await page.title(),
    headings: headings.map((h) => h.trim()).filter(Boolean).slice(0, 12),
    navLabels: navLabels.map((h) => h.replace(/\s+/g, ' ').trim()).filter(Boolean).slice(0, 12),
    buttons: buttons.map((h) => h.replace(/\s+/g, ' ').trim()).filter(Boolean).slice(0, 20),
    bodyText,
    consoleErrors: errors.consoleErrors.slice(before.c),
    failedRequests: errors.failedRequests.slice(before.f),
    horizontalOverflow,
    mainMaxWidthPx,
    screenshot: file,
  };
}

export function writeReport(name: string, data: unknown): string {
  const dir = path.resolve('e2e/results');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, name);
  fs.writeFileSync(file, JSON.stringify(data, null, 2), 'utf8');
  return file;
}
