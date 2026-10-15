import { test } from '@playwright/test';
import { registerAccount, applySession, collectErrors, probe, writeReport, type PageProbe } from './helpers';

/**
 * Exploratory recon: walks every route the app exposes, signed out and signed in,
 * capturing structure, console/network errors, overflow and a full-page screenshot.
 * This is evidence gathering, not assertion — defects are judged from the output.
 */

const SIGNED_OUT = ['/', '/search', '/scan', '/you'];

const SIGNED_IN = [
  '/welcome',
  '/today',
  '/family',
  '/food',
  '/food/recent',
  '/search',
  '/you',
  '/you/profile',
  '/you/sharing',
  '/you/shared-with-me',
  '/you/access-log',
  '/you/household',
  '/you/appearance',
  '/goals',
  '/habits',
  '/trackers/hydration',
  '/meals',
  '/meals/new',
  '/next',
  '/next/insights',
  '/next/health-shelf',
  '/next/ask',
];

function slug(route: string): string {
  return route === '/' ? 'root' : route.replace(/^\//, '').replace(/\//g, '_');
}

test("recon: signed out routes", async ({ page }, testInfo) => {
  const errors = collectErrors(page);
  const results: PageProbe[] = [];
  for (const route of SIGNED_OUT) {
    results.push(await probe(page, route, slug(route), `${testInfo.project.name}/signed-out`, errors));
  }
  writeReport(`recon-signed-out-${testInfo.project.name}.json`, results);
});

test('recon: signed in routes', async ({ page }, testInfo) => {
  const session = await registerAccount('recon');
  await applySession(page, session);
  const errors = collectErrors(page);
  const results: PageProbe[] = [];
  for (const route of SIGNED_IN) {
    results.push(await probe(page, route, slug(route), `${testInfo.project.name}/signed-in`, errors));
  }
  writeReport(`recon-signed-in-${testInfo.project.name}.json`, { session: { email: session.email }, results });
});
