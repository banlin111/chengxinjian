import { test, expect } from '@playwright/test';

test('expansion matches the thumbnail to the real player artwork', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  await page.evaluate(() => { (window as any).uni.navigateTo = () => {}; });
  await page.locator('.mini-card').first().click();
  await expect(page.locator('.expansion-destination .player-title')).toHaveText('晨间正念 10 分钟');
  await expect.poll(async () => {
    const cover = await page.locator('.expansion-cover').boundingBox();
    const artwork = await page.locator('.expansion-destination .player-artwork').boundingBox();
    if (!cover || !artwork) return 999;
    return Math.abs(cover.x - artwork.x) + Math.abs(cover.y - artwork.y) + Math.abs(cover.width - artwork.width) + Math.abs(cover.height - artwork.height);
  }).toBeLessThan(1);
  await expect(page.locator('.expansion-cover')).toHaveCSS('border-radius', '84px');
  await expect(page.locator('.expansion-destination')).toHaveCSS('opacity', '1');
  const destination = await page.locator('.expansion-destination').boundingBox();
  expect(destination!.y).toBeCloseTo(0, 0);
  expect(destination!.width).toBe(390);
  expect(destination!.height).toBe(844);
});

test('each home course expands its own artwork and opens the matching preview', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  for (const selector of ['.hero-button', '.mini-card:nth-child(1)', '.mini-card:nth-child(2)', '.mini-card:nth-child(3)']) {
    const card = page.locator(selector);
    const title = await card.locator('.hero-title, .mini-title').textContent();
    const source = await card.locator('.hero-image img, .mini-image img').getAttribute('src');
    await card.click();
    await expect(page.locator('.expansion-image img')).toHaveAttribute('src', source!);
    await expect(page.locator('.card-expansion')).toHaveClass(/is-expanded/);
    await page.waitForURL(/pages\/player\/index/);
    await expect(page.locator('.player-title')).toHaveText(title!);
    await expect(page.locator('.player-background img')).toHaveAttribute('src', source!);
    await page.getByRole('button', { name: '返回上一页', exact: true }).click();
    await expect(page.locator('.hero-title')).toBeVisible();
    await expect(page.locator('.card-expansion')).toHaveCount(0);
  }
});

test('navigation failure clears the expansion; reduced motion bypasses expansion', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  await page.evaluate(() => {
    const app = window as any;
    app.savedNavigateTo = app.uni.navigateTo;
    app.uni.navigateTo = (options: any) => options.fail({ errMsg: 'test failure' });
  });
  await page.locator('.hero-button').click();
  await expect(page.locator('.card-expansion')).toBeVisible();
  await expect(page.locator('.card-expansion')).toHaveCount(0);
  await expect(page.locator('.hero-button')).toHaveAttribute('aria-disabled', 'false');
  await page.evaluate(() => { const app = window as any; app.uni.navigateTo = app.savedNavigateTo; });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.locator('.hero-button').click();
  await page.waitForURL(/pages\/player\/index/);
  await expect(page.locator('.player-title')).toContainText('慈心唱诵');
  await expect(page.locator('.card-expansion')).toHaveCount(0);
});

test('leaving home mid-expansion cancels delayed navigation', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  await page.locator('.hero-button').click();
  await expect(page.locator('.card-expansion')).toBeVisible();
  await page.evaluate(() => (window as any).uni.switchTab({ url: '/pages/courses/index' }));
  await expect(page.locator('[data-screen="3"]')).toBeVisible();
  await page.waitForTimeout(450);
  await expect(page.locator('[data-screen="3"]')).toBeVisible();
  await expect(page.locator('[data-screen="5"]')).toHaveCount(0);
});

test('home ends with a small consistent gap above the actual fixed tab bar', async ({ page }) => {
  for (const [width, height] of [[320, 568], [375, 812], [390, 844], [430, 932], [1280, 900]]) {
    await page.setViewportSize({ width, height });
    await page.goto('/');
    await expect(page.locator('.checkin-card')).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await expect.poll(async () => {
      const card = await page.locator('.checkin-card').boundingBox();
      const bar = await page.locator('uni-tabbar .uni-tabbar').boundingBox();
      return Math.round(bar!.y - card!.y - card!.height);
    }).toBe(16);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
  }
});
