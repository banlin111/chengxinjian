import { test, expect } from '@playwright/test';

test('cold launch opens all five tabs and home remains clickable after returning from practice', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  for (const [label, screen] of [['练习','10'],['课程','3'],['冥想','2'],['我的','12']]) {
    await page.locator('.uni-tabbar__item').filter({ hasText: label }).click();
    await expect(page.locator(`[data-screen="${screen}"]`)).toBeVisible();
    await expect(page.locator('.card-expansion')).toHaveCount(0);
  }
  await page.locator('.uni-tabbar__item').filter({ hasText: '推荐' }).click();
  await page.locator('.hero-button').click();
  await expect(page).toHaveURL(/pages\/player\/index/);
  await expect(page.locator('.player-title')).toContainText('慈心');
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await page.locator('.uni-tabbar__item').filter({ hasText: '练习' }).click();
  await page.getByRole('button', { name: '领取', exact: true }).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('当前为前端界面演示');
  expect(errors).toEqual([]);
});
