import { test, expect } from '@playwright/test';

test('navigation locks preserve transparent home cards and links', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  await page.evaluate(() => {
    const app = window as any;
    app.navigationCount = 0;
    app.uni.navigateTo = () => { app.navigationCount++; };
  });
  await page.locator('.hero-button').click();
  for (const selector of ['.hero-button', '.mini-card', '.more-link']) {
    for (const button of await page.locator(selector).all()) {
      await expect(button).toHaveAttribute('aria-disabled', 'true');
      await expect(button).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
      await expect(button).toHaveCSS('opacity', '1');
    }
  }
  await expect(page.locator('.checkin-button')).not.toHaveCSS('background-image', 'none');
  await expect.poll(() => page.evaluate(() => (window as any).navigationCount)).toBe(1);
  await page.locator('.hero-button').dispatchEvent('click');
  expect(await page.evaluate(() => (window as any).navigationCount)).toBe(1);
});

test('press follows touch and releases on scroll or cancellation without navigating', async ({ page }) => {
  await page.goto('/');
  const card = page.locator('.hero-button');
  await expect(card).toBeVisible();
  await card.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 100, clientY: 250 }] });
  await expect(card).toHaveClass(/is-pressed/);
  await expect.poll(() => card.evaluate(e => getComputedStyle(e).transform)).toBe('matrix(0.985, 0, 0, 0.985, 0, 0)');
  await card.dispatchEvent('touchmove', { touches: [{ identifier: 1, clientX: 100, clientY: 280 }] });
  await expect(card).not.toHaveClass(/is-pressed/);
  await card.dispatchEvent('touchend', { touches: [] });
  await card.dispatchEvent('click');
  await expect(page.locator('.hero-title')).toBeVisible();
  await expect(page.locator('[data-screen="5"]')).toHaveCount(0);
  await card.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 100, clientY: 250 }] });
  await card.dispatchEvent('touchcancel', { touches: [] });
  await expect(card).not.toHaveClass(/is-pressed/);
  await expect.poll(() => card.evaluate(e => getComputedStyle(e).transform)).toBe('matrix(1, 0, 0, 1, 0, 0)');
});

test('horizontal position survives navigation; rapid activation opens only one page', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  const scroller = page.locator('.weekly-scroll .uni-scroll-view').nth(1);
  await scroller.evaluate(e => { e.scrollLeft = 120; });
  await expect.poll(() => scroller.evaluate(e => e.scrollLeft)).toBeGreaterThan(100);
  // Dispatch synchronously to exercise the guard before the disabled prop renders.
  await page.locator('.hero-button').evaluate(e => { e.dispatchEvent(new Event('click')); e.dispatchEvent(new Event('click')); });
  await expect(page.locator('[data-screen="5"]')).toBeVisible();
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await expect(page.locator('.hero-title')).toBeVisible();
  await expect.poll(() => scroller.evaluate(e => e.scrollLeft)).toBeGreaterThan(100);
  await expect(page.locator('.hero-button')).not.toHaveClass(/is-pressed/);
  await page.locator('.more-link').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('[data-screen="3"]')).toBeVisible();
});

test('system and stored reduced-motion preferences preserve feedback without movement', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  const card = page.locator('.hero-button');
  await expect(card).toBeVisible();
  await expect(page.locator('.home-content')).toHaveCSS('animation-name', 'none');
  await card.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 100, clientY: 250 }] });
  await expect(card).toHaveCSS('transform', 'none');
  await expect(card).toHaveCSS('opacity', '0.9');
  await card.dispatchEvent('touchcancel', { touches: [] });
  await page.evaluate(() => (window as any).uni.setStorageSync('cxj.reducedMotion', true));
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.reload();
  await expect(page.locator('.hero-title')).toBeVisible();
  await expect(page.locator('.home-content')).toHaveCSS('animation-name', 'none');
  await card.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 100, clientY: 250 }] });
  await expect(card).toHaveCSS('transform', 'none');
});
