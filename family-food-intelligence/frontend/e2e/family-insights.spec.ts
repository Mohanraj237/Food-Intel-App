import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';
import { login, shotDir } from './helpers';

/**
 * The Family page with trends, goals and the family summary (D-054), against the seeded Iyer
 * family (tools/demo-seed/demo-family.sql — reset and re-seed first, so its week ends today).
 *
 * Raghav's view: himself, Aarav (a child he guards), Krishnan (he is caregiver), Meera and Vikram
 * (who share with him), and five people who share nothing with him.
 */

const PER_MEMBER_ENDPOINTS = /\/api\/v1\/(members|wellness|goals|habits|insights)\//;

/**
 * Stores the session where the app looks for it, without loading the app signed in yet. The
 * backend rotates the refresh token on every use, so the first authenticated load is the Family
 * page itself — and every later step navigates inside the app rather than reloading, which would
 * spend the token again (and, if a slow first refresh were cut off, hand the app a spent one).
 */
async function signIn(page: Page, email: string, theme: 'light' | 'dark' = 'light') {
  const session = await login(email);
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.evaluate(
    ([refresh, account, mode]) => {
      localStorage.setItem('ffi.refresh', refresh);
      localStorage.setItem('ffi.account', account);
      localStorage.setItem('ffi-theme', mode);
    },
    [session.refreshToken, session.accountId, theme] as const,
  );
}

async function openFamily(page: Page, query = '') {
  await page.goto(`/family${query}`, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { level: 1, name: 'Family' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'People' })).toBeVisible({ timeout: 20_000 });
}

function shot(project: string, name: string) {
  return path.join(shotDir(path.join(project, 'family-insights')), `${name}.png`);
}

test.describe('Family insights', () => {
  test('summary, cards with weekly strips, and no per-member fan-out', async ({ page }, info) => {
    await signIn(page, 'raghav.iyer.demo@example.com');

    const perMember: string[] = [];
    const overviewCalls: string[] = [];
    page.on('request', (request) => {
      const url = request.url();
      if (PER_MEMBER_ENDPOINTS.test(url)) perMember.push(url);
      if (url.includes('/overview')) overviewCalls.push(url);
    });

    const overviewResponse = page.waitForResponse((r) => r.url().includes('/overview') && r.ok());
    await openFamily(page);
    const served = (await (await overviewResponse).json()) as {
      members: { displayLabel: string; relationship: string }[];
    };

    // The family summary names whose records it is built from, and covers five people.
    const summary = page.getByRole('region', { name: 'Family today' });
    await expect(summary).toBeVisible();
    await expect(summary.getByText(/^From you, /)).toContainText('Aarav');
    for (const name of ['Krishnan', 'Meera', 'Vikram']) {
      await expect(summary.getByText(/^From you, /)).toContainText(name);
    }
    await expect(summary.getByText('Health readings, body measurements and mood are never combined across people.')).toBeVisible();
    await expect(summary.getByRole('img')).toHaveCount(4);

    // A card for each person who shares something — in exactly the server's order, never re-sorted.
    const cards = page.getByRole('article');
    await expect(cards).toHaveCount(5);
    const serverOrder = served.members.filter((m) => m.relationship !== 'NONE').map((m) => m.displayLabel);
    expect(serverOrder.slice(0, 2)).toEqual(['Raghav', 'Aarav']);
    expect(await cards.evaluateAll((els) => els.map((el) => el.getAttribute('aria-label')))).toEqual(serverOrder);

    // Every strip on a card covers exactly the seven days of the week.
    const slotCounts = await page
      .locator('article svg[role="img"]')
      .evaluateAll((svgs) => svgs.map((svg) => svg.querySelectorAll('g[data-day]').length));
    expect(slotCounts.length).toBeGreaterThan(8);
    expect(new Set(slotCounts)).toEqual(new Set([7]));

    // Readings are paired and uncoloured; a child's meals are a count, not kcal (UXD-08).
    const krishnan = cards.filter({ has: page.getByText('Krishnan', { exact: true }) });
    await expect(krishnan.getByText(/^\d+\/\d+ mmHg/)).toBeVisible();
    await expect(krishnan.getByText(/You can log: Water and Activity/)).toBeVisible();
    const aarav = cards.filter({ has: page.getByText('Aarav', { exact: true }) });
    await expect(aarav.getByText(/^\d+ meals?$/)).toBeVisible();
    await expect(aarav).not.toContainText('kcal');

    // Goals the person set, as a sentence with a bar.
    const raghav = cards.filter({ has: page.getByText('Raghav', { exact: true }) });
    await expect(raghav.getByText(/ of 8000 steps/)).toBeVisible();

    // People who share nothing: one quiet list, the child with the guardian line, nothing to open.
    const others = page.getByRole('region', { name: /^Also in / });
    for (const name of ['Lakshmi', 'Arjun', 'Divya', 'Kavya', 'Ananya']) {
      await expect(others.getByText(name, { exact: true })).toBeVisible();
    }
    await expect(others.getByText('Their guardians look after their records.')).toBeVisible();
    await expect(others.getByRole('link')).toHaveCount(0);

    // The updates feed is a timeline of what people you can see logged.
    const feed = page.getByRole('region', { name: 'Latest updates' });
    await expect(feed.getByRole('listitem').first()).toBeVisible();

    // One overview read, and no per-member probing (G-01, spec §7.2).
    expect(overviewCalls).toHaveLength(1);
    expect(perMember).toEqual([]);

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
    expect(overflow, 'page scrolls sideways').toBe(false);

    await page.screenshot({ path: shot(info.project.name, 'raghav-light'), fullPage: true });
  });

  test('the day navigator steps back and forward through the week', async ({ page }) => {
    await signIn(page, 'raghav.iyer.demo@example.com');
    await openFamily(page);

    const next = page.getByRole('button', { name: 'Next day' });
    await expect(next).toBeDisabled();

    await page.getByRole('button', { name: 'Previous day' }).click();
    await expect(page).toHaveURL(/\?date=\d{4}-\d{2}-\d{2}$/);
    await expect(page.getByRole('region', { name: /^Family on / })).toBeVisible();
    await expect(page.getByRole('region', { name: /^Updates on / })).toBeVisible();
    await expect(next).toBeEnabled();

    await page.getByRole('button', { name: 'Today' }).click();
    await expect(page).toHaveURL(/\/family$/);
    await expect(page.getByRole('region', { name: 'Family today' })).toBeVisible();
  });

  test("a member's page charts their week and its tracker links resolve", async ({ page }, info) => {
    await signIn(page, 'raghav.iyer.demo@example.com');
    await openFamily(page);

    await page.getByRole('link', { name: 'Open Krishnan' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Krishnan' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'This week' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Blood pressure' })).toBeVisible();
    await expect(page.getByText(/^We show Krishnan's readings exactly as they were recorded\./)).toBeVisible();
    await expect(page.getByText('What you can see')).toBeVisible();
    await expect(page.locator('.member-view__chart svg[role="img"]').first()).toBeVisible();
    await page.screenshot({ path: shot(info.project.name, 'krishnan-member'), fullPage: true });

    // The "See 30 or 90 days" link lands on a real tracker, not "There's no tracker for this yet".
    await page.getByRole('link', { name: /See 30 or 90 days/ }).first().click();
    await expect(page).toHaveURL(/\/trackers\/[A-Z_]+$/);
    await expect(page.getByText("There's no tracker for this yet.")).toHaveCount(0);
  });

  test('Meera sees what is waiting for her', async ({ page }) => {
    await signIn(page, 'meera.iyer.demo@example.com');
    await openFamily(page);
    await expect(page.getByText(/Your share of (a meal|\d+ meals) isn't in your log yet\./)).toBeVisible();
    await expect(page.getByRole('link', { name: /Review/ })).toHaveAttribute('href', '/meals?filter=awaiting');
  });

  test('dark theme', async ({ page }, info) => {
    await signIn(page, 'raghav.iyer.demo@example.com', 'dark');
    await openFamily(page);
    await expect(page.getByRole('region', { name: 'Family today' })).toBeVisible();
    await page.screenshot({ path: shot(info.project.name, 'raghav-dark'), fullPage: true });
  });
});
