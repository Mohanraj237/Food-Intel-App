import { test, expect } from '@playwright/test';
import { login, applySession, assertSignedIn, collectErrors, shotDir, writeReport } from './helpers';
import path from 'node:path';

/**
 * Proves, in a real browser, each defect fixed in this pass. Written as assertions
 * (not evidence gathering) so a regression fails the suite rather than being noticed
 * in a screenshot months later.
 */

test('fix: search results follow typing, without pressing Enter', async ({ page }, testInfo) => {
  const dir = shotDir(`${testInfo.project.name}/verify`);
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);

  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(600);

  const input = page.locator('input[type="search"]:visible').first();
  await input.click();
  await input.type('milk', { delay: 60 });

  // No Enter. The URL should pick up the query and real results should render.
  await expect(page).toHaveURL(/\/search\?q=milk/, { timeout: 5000 });
  await expect(page.getByText(/Results for/i)).toBeVisible({ timeout: 5000 });

  const productLinks = page.locator('a[href*="/product/"]');
  await expect.poll(() => productLinks.count(), { timeout: 5000 }).toBeGreaterThan(0);

  await page.screenshot({ path: path.join(dir, 'search-live-results.png'), fullPage: false });
});

test('fix: focus ring does not deform the component it decorates', async ({ page }, testInfo) => {
  const dir = shotDir(`${testInfo.project.name}/verify`);
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);

  await page.goto('/search?q=milk', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);

  const card = page.locator('a[href*="/product/"]').first();
  await expect(card).toBeVisible();

  const radiusUnfocused = await card.evaluate((el) => getComputedStyle(el).borderRadius);
  await card.focus();
  await page.waitForTimeout(200);
  const focused = await card.evaluate((el) => ({
    borderRadius: getComputedStyle(el).borderRadius,
    outlineWidth: getComputedStyle(el).outlineWidth,
    outlineStyle: getComputedStyle(el).outlineStyle,
  }));

  // The ring must still be there...
  expect(focused.outlineStyle).toBe('solid');
  expect(focused.outlineWidth).toBe('2px');
  // ...but it must not have changed the card's shape (was forced to 6px by a global rule).
  expect(focused.borderRadius).toBe(radiusUnfocused);

  await page.screenshot({ path: path.join(dir, 'card-focused-radius-preserved.png'), fullPage: false });
  writeReport(`verify-focus-${testInfo.project.name}.json`, { radiusUnfocused, focused });
});

test('fix: no hard-coded English "(current)" in the navigation', async ({ page }) => {
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);
  await page.goto('/today', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  const html = await page.content();
  expect(html).not.toContain('(current)');

  // The accessible signal must still be present.
  const current = page.locator('[aria-current="page"]');
  await expect.poll(() => current.count()).toBeGreaterThan(0);
});

test('fix: durations and volumes read in human units on family cards', async ({ page }, testInfo) => {
  const dir = shotDir(`${testInfo.project.name}/verify`);
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);
  await assertSignedIn(page);

  await page.goto('/family', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2000);
  const text = await page.locator('body').innerText();

  // Sleep was rendered as raw canonical minutes ("400 min"); it should now read as hours.
  expect(text).toMatch(/\d+\s*h(\s+\d+\s*min)?/);
  expect(text).not.toMatch(/Sleep\s+\d{3,}\s*min/);

  await page.screenshot({ path: path.join(dir, 'family-human-units.png'), fullPage: true });
});

test('fix: Today renders recommendation cards without React key warnings', async ({ page }) => {
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);
  const errors = collectErrors(page);

  await page.goto('/today', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);

  const keyWarnings = errors.consoleErrors.filter(
    (e) => /same key|key.*prop is being spread/i.test(e),
  );
  expect(keyWarnings, `React key warnings: ${JSON.stringify(keyWarnings)}`).toHaveLength(0);
});

test('fix: goal metrics never render as raw enum codes', async ({ page }) => {
  const session = await login('krishnan.iyer.demo@example.com'); // has an ACTIVE_MINUTES goal
  await applySession(page, session);
  await page.goto('/goals', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2000);

  const text = await page.locator('body').innerText();
  expect(text).not.toContain('ACTIVE_MINUTES');
  expect(text).toMatch(/Active minutes/i);
});

test('fix: "You" tab and its page agree on the name', async ({ page }) => {
  const session = await login('raghav.iyer.demo@example.com');
  await applySession(page, session);
  await page.goto('/you', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200);

  await expect(page.locator('h1')).toContainText('You');
});
