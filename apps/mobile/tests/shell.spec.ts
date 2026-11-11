import { test, expect } from '@playwright/test';

test('homepage opens the designed player and fixed tabs keep their routes', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toContainText('慈心唱诵冥想');
  await page.locator('.hero-button').click();
  await expect(page.locator('[data-screen="5"]')).toBeVisible();
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await expect(page.locator('.hero-title')).toBeVisible();
  for (const [name,id] of [['冥想','2'], ['课程','3'], ['练习','10'], ['我的','12']]) {
    await page.locator('uni-tabbar').getByText(name, { exact: true }).click();
    await expect(page.locator(`[data-screen="${id}"]:visible`)).toBeVisible();
  }
});

test('loading failure can retry; empty data has an honest empty state', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  await page.evaluate(() => (window as any).uni.setStorageSync('cxj.mock.homeState', 'error'));
  await page.reload();
  await expect(page.getByText('暂时无法加载', { exact: true })).toBeVisible();
  await page.evaluate(() => (window as any).uni.removeStorageSync('cxj.mock.homeState'));
  await page.getByText('重新加载', { exact: true }).click();
  await expect(page.locator('.hero-title')).toBeVisible();
  await page.evaluate(() => (window as any).uni.setStorageSync('cxj.mock.homeState', 'empty'));
  await page.reload();
  await expect(page.getByText('新内容正在准备中', { exact: true })).toBeVisible();
  await expect(page.locator('.hero-button')).toHaveCount(0);
});

test('phone widths do not overflow horizontally', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  for (const width of [320, 375, 390, 430]) {
    await page.setViewportSize({ width, height: width === 320 ? 568 : 844 });
    await page.locator('.checkin-button').scrollIntoViewIfNeeded();
    const button = await page.locator('.checkin-button').boundingBox();
    const bar = await page.locator('uni-tabbar').boundingBox();
    expect(button!.y + button!.height).toBeLessThanOrEqual(bar!.y);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
  }
});
