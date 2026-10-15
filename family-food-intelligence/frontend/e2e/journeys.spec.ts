import { test } from '@playwright/test';
import { login, applySession, assertSignedIn, collectErrors, probe, writeReport, type PageProbe } from './helpers';

/**
 * Full journey audit against the seeded Iyer family.
 *
 * Three personas, chosen because each one exercises a different part of the
 * permission and experience model with real data behind it:
 *   - Raghav  (father)      SELF + GUARDIAN of a child + CAREGIVER for his father
 *   - Krishnan (grandfather) the OLDER_ADULT experience, with someone helping him
 *   - Arjun   (brother)      shares nothing with anyone — the "not shared" states
 */

const PERSONAS = [
  {
    key: 'father',
    email: 'raghav.iyer.demo@example.com',
    routes: ['/today', '/family', '/goals', '/habits', '/trackers/hydration', '/meals', '/food', '/next', '/you', '/you/sharing', '/you/access-log', '/you/household'],
  },
  {
    key: 'grandfather',
    email: 'krishnan.iyer.demo@example.com',
    routes: ['/today', '/family', '/goals', '/trackers/clinical', '/you/sharing', '/you/access-log'],
  },
  {
    key: 'brother',
    email: 'arjun.iyer.demo@example.com',
    routes: ['/today', '/family', '/you/sharing'],
  },
];

function slug(route: string): string {
  return route.replace(/^\//, '').replace(/\//g, '_') || 'root';
}

for (const persona of PERSONAS) {
  test(`journey: ${persona.key}`, async ({ page }, testInfo) => {
    const session = await login(persona.email);
    await applySession(page, session);
    await assertSignedIn(page);

    const errors = collectErrors(page);
    const results: PageProbe[] = [];
    for (const route of persona.routes) {
      results.push(await probe(page, route, slug(route), `${testInfo.project.name}/${persona.key}`, errors));
    }
    writeReport(`journey-${persona.key}-${testInfo.project.name}.json`, {
      persona: persona.key,
      email: persona.email,
      results,
    });
  });
}

/** The family overview is the screen most likely to leak another member's data — check it explicitly. */
test('journey: family member drill-down (father)', async ({ page }, testInfo) => {
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);
  const errors = collectErrors(page);

  await page.goto('/family', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1800);

  const memberLinks = await page.locator('a[href*="/family/"]').evaluateAll((els) =>
    els.map((e) => ({ href: e.getAttribute('href'), text: (e.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 80) })),
  );

  const drilldowns: PageProbe[] = [];
  const seen = new Set<string>();
  for (const link of memberLinks) {
    if (!link.href || seen.has(link.href)) continue;
    seen.add(link.href);
    drilldowns.push(await probe(page, link.href, `member${slug(link.href)}`, `${testInfo.project.name}/father-members`, errors));
  }

  writeReport(`journey-family-drilldown-${testInfo.project.name}.json`, { memberLinks, drilldowns });
});
