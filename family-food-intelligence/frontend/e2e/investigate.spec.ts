import { test, expect } from '@playwright/test';
import { registerAccount, applySession, assertSignedIn, collectErrors, writeReport, shotDir, API, type Session } from './helpers';
import path from 'node:path';

/**
 * Targeted investigation of the two reported defects:
 *   1. Search — "internal outline/focus treatment when an item is selected" and
 *      "does not render dynamic search results correctly".
 *   2. Top navigation — "does not behave or communicate like professional,
 *      user-centric consumer navigation".
 *
 * Evidence-gathering, not pass/fail: findings are written to e2e/results.
 */

async function provisionIndividual(session: Session) {
  const res = await fetch(`${API}/me/individual`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.accessToken}` },
    body: JSON.stringify({ displayLabel: 'Audit User' }),
  });
  if (!res.ok && res.status !== 200) throw new Error(`provision failed ${res.status} ${await res.text()}`);
}

test('investigate: search behaviour', async ({ page, context }, testInfo) => {
  const dir = shotDir(`${testInfo.project.name}/search`);
  const session = await registerAccount('search');
  await provisionIndividual(session);
  await applySession(page, session);
  await assertSignedIn(page);

  const errors = collectErrors(page);
  const findings: Record<string, unknown> = {};

  // --- 1. Land on search
  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(dir, '01-search-empty.png'), fullPage: true });

  // The shell renders BOTH a mobile and a desktop search input; only one is visible per breakpoint.
  const input = page.locator('input[type="search"]:visible, input[type="text"]:visible').first();
  findings.searchInputs = await page.evaluate(() =>
    Array.from(document.querySelectorAll('input')).map((el) => ({
      id: el.id,
      type: el.type,
      placeholder: el.placeholder,
      visible: !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length),
    })),
  );

  // --- 2. Type a query and watch whether results appear WITHOUT submitting (dynamic search)
  await input.click();
  await input.fill('milk');
  await page.waitForTimeout(1500); // generous: debounce + network
  const afterTypingText = await page.locator('body').innerText();
  const afterTypingUrl = page.url();
  await page.screenshot({ path: path.join(dir, '02-after-typing-no-submit.png'), fullPage: true });
  findings.dynamicSearch = {
    urlAfterTyping: afterTypingUrl,
    resultsRenderedWithoutSubmit: /result|₹|per 100|Everyday|Occasional|Limit/i.test(afterTypingText),
    bodySample: afterTypingText.slice(0, 900),
  };

  // --- 3. Submit and see whether results render
  await input.press('Enter');
  await page.waitForTimeout(2000);
  const afterSubmitUrl = page.url();
  const afterSubmitText = await page.locator('body').innerText();
  await page.screenshot({ path: path.join(dir, '03-after-submit.png'), fullPage: true });

  // Count plausible result cards (links to a product detail route)
  const resultLinks = page.locator('a[href*="/product/"]');
  const resultCount = await resultLinks.count();
  findings.submittedSearch = {
    urlAfterSubmit: afterSubmitUrl,
    productLinkCount: resultCount,
    bodySample: afterSubmitText.slice(0, 900),
  };

  // --- 4. Focus/outline treatment when an item is selected
  if (resultCount > 0) {
    const first = resultLinks.first();
    await first.focus();
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(dir, '04-result-focused.png'), fullPage: false });

    const focusStyles = await first.evaluate((el) => {
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        outlineStyle: cs.outlineStyle,
        outlineWidth: cs.outlineWidth,
        outlineColor: cs.outlineColor,
        outlineOffset: cs.outlineOffset,
        boxShadow: cs.boxShadow,
        overflow: cs.overflow,
        borderRadius: cs.borderRadius,
        rect: { w: Math.round(r.width), h: Math.round(r.height) },
        tag: el.tagName,
        // does an ancestor clip the outline?
        clippingAncestor: (() => {
          let p = el.parentElement;
          while (p && p !== document.body) {
            const pcs = getComputedStyle(p);
            if (pcs.overflow !== 'visible' && pcs.overflow !== '') {
              return { cls: p.className, overflow: pcs.overflow };
            }
            p = p.parentElement;
          }
          return null;
        })(),
      };
    });
    findings.focusTreatment = focusStyles;

    // Keyboard-select it and see where focus/outline lands after navigation
    await first.press('Enter');
    await page.waitForTimeout(1800);
    findings.afterSelect = { url: page.url() };
    await page.screenshot({ path: path.join(dir, '05-after-select-product.png'), fullPage: true });
  }

  // --- 5. Search input's own focus ring
  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(600);
  const inputStyles = await page.locator("input:visible").first().evaluate((el) => {
    el.focus();
    const cs = getComputedStyle(el);
    return { outlineStyle: cs.outlineStyle, outlineWidth: cs.outlineWidth, outlineColor: cs.outlineColor, boxShadow: cs.boxShadow };
  });
  findings.searchInputFocus = inputStyles;
  await page.screenshot({ path: path.join(dir, '06-input-focused.png'), fullPage: false });

  findings.consoleErrors = errors.consoleErrors;
  findings.failedRequests = [...new Set(errors.failedRequests)];
  writeReport(`investigate-search-${testInfo.project.name}.json`, findings);
});

test('investigate: navigation behaviour', async ({ page, context }, testInfo) => {
  const dir = shotDir(`${testInfo.project.name}/nav`);
  const session = await registerAccount('nav');
  await provisionIndividual(session);
  await applySession(page, session);
  await assertSignedIn(page);

  const errors = collectErrors(page);
  const findings: Record<string, unknown> = {};

  await page.goto('/today', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(dir, '01-today-nav.png'), fullPage: false });

  // Inventory every nav target and how it presents itself
  findings.navItems = await page.evaluate(() => {
    const navs = Array.from(document.querySelectorAll('nav'));
    return navs.map((nav) => {
      const cs = getComputedStyle(nav);
      return {
        ariaLabel: nav.getAttribute('aria-label'),
        className: nav.className,
        position: cs.position,
        display: cs.display,
        visible: cs.display !== 'none' && cs.visibility !== 'hidden',
        rect: (() => { const r = nav.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }; })(),
        items: Array.from(nav.querySelectorAll('a, button')).map((el) => {
          const r = el.getBoundingClientRect();
          const ecs = getComputedStyle(el);
          return {
            tag: el.tagName,
            text: (el.textContent || '').replace(/\s+/g, ' ').trim(),
            href: el.getAttribute('href'),
            ariaCurrent: el.getAttribute('aria-current'),
            ariaLabel: el.getAttribute('aria-label'),
            w: Math.round(r.width),
            h: Math.round(r.height),
            color: ecs.color,
            hasIcon: !!el.querySelector('svg') ,
            glyphText: (el.querySelector('[aria-hidden="true"]')?.textContent || '').trim(),
          };
        }),
      };
    });
  });

  // Active-state communication: visit each tab, record what marks the current one
  const tabs = ['/today', '/family', '/food', '/you'];
  const activeStates: unknown[] = [];
  for (const t of tabs) {
    await page.goto(t, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(900);
    activeStates.push(
      await page.evaluate((route) => {
        const marked = Array.from(document.querySelectorAll('[aria-current]')).map((el) => ({
          text: (el.textContent || '').replace(/\s+/g, ' ').trim(),
          value: el.getAttribute('aria-current'),
          href: el.getAttribute('href'),
        }));
        return { route, ariaCurrent: marked, title: document.title };
      }, t),
    );
    await page.screenshot({ path: path.join(dir, `tab-${t.replace(/\//g, '_')}.png`), fullPage: false });
  }
  findings.activeStates = activeStates;

  // Does the centre Log control actually open something?
  await page.goto('/today', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);
  const logBtn = page.locator('nav button:visible', { hasText: /log/i }).first();
  findings.logButtonExists = (await logBtn.count()) > 0;
  if (findings.logButtonExists) {
    await logBtn.click();
    await page.waitForTimeout(1200);
    findings.logSheet = {
      urlAfterClick: page.url(),
      dialogCount: await page.locator('[role="dialog"]').count(),
      bodySample: (await page.locator('body').innerText()).slice(0, 700),
    };
    await page.screenshot({ path: path.join(dir, '02-log-sheet-open.png'), fullPage: false });
  }

  findings.consoleErrors = errors.consoleErrors;
  findings.failedRequests = [...new Set(errors.failedRequests)];
  writeReport(`investigate-nav-${testInfo.project.name}.json`, findings);
});
