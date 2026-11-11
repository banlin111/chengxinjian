import { test, expect } from '@playwright/test';

const entries = [
  { route: 'course-detail/index', button: '.course-lesson', title: '身体扫描完全指南', from: 'course' },
  { route: 'meditation-detail/index?id=demo-meditation-morning', button: '.detail-start', title: '山雾晨光 · 呼吸觉察', from: 'meditation' },
  { route: 'practice/index', button: '#practice-player-origin .motion-button', title: '绿度母｜慈心唱诵冥想', from: 'practice' },
  { route: 'live/index', button: '[data-action="秋分昼夜等长冥想夜"]', title: '秋分昼夜等长冥想夜', from: 'replay' },
];

test('all content entry points expand into the matching player before routing once', async ({ page }) => {
  for (const entry of entries) {
    await page.goto(`/#/pages/${entry.route}`);
    await page.evaluate(() => {
      const app = window as any;
      const original = app.uni.navigateTo;
      app.playerCalls = [];
      app.uni.navigateTo = (options: any) => { app.playerCalls.push(options); };
      app.restoreNavigation = () => { app.uni.navigateTo = original; };
    });
    await page.locator(entry.button).first().click();
    await expect(page.locator('.card-expansion')).toBeVisible();
    await expect(page.locator('.expansion-destination .player-title')).toHaveText(entry.title);
    await expect.poll(() => page.evaluate(() => (window as any).playerCalls.length)).toBe(1);
    const image = await page.locator('.expansion-cover').boundingBox();
    const destination = await page.locator('.expansion-destination .player-artwork').boundingBox();
    expect(Math.abs(image!.x - destination!.x)).toBeLessThan(2);
    expect(Math.abs(image!.y - destination!.y)).toBeLessThan(2);
    expect(Math.abs(image!.width - destination!.width)).toBeLessThan(2);
    await page.evaluate(() => { const app = window as any; const options = app.playerCalls[0]; app.restoreNavigation(); app.uni.navigateTo(options); });
    await expect(page).toHaveURL(new RegExp(`pages/player/index.*from=${entry.from}`));
    await expect(page.locator('.player-title')).toHaveText(entry.title);
    await page.getByRole('button', { name: '返回上一页', exact: true }).click();
    await expect(page.locator(entry.button).first()).toHaveAttribute('aria-disabled', 'false');
  }
});

test('button-only transition keeps artwork at final size instead of stretching the button into an image', async ({ page }) => {
  await page.goto('/#/pages/practice/index');
  await page.evaluate(() => { (window as any).uni.navigateTo = () => {}; });
  await page.locator('#practice-player-origin .motion-button').click();
  await expect(page.locator('.expansion-cover')).toHaveCSS('width', '168px');
  await expect(page.locator('.expansion-cover')).toHaveCSS('height', '168px');
  await expect(page.locator('.expansion-cover')).toHaveCSS('opacity', '1');
});

test('leaving during a transition cancels the delayed jump and reduced motion skips it', async ({ page }) => {
  await page.goto('/#/pages/practice/index');
  await page.locator('#practice-player-origin .motion-button').click();
  await expect(page.locator('.card-expansion')).toBeVisible();
  await page.evaluate(() => (window as any).uni.switchTab({ url: '/pages/courses/index' }));
  await page.waitForTimeout(450);
  await expect(page.locator('[data-screen="3"]')).toBeVisible();
  await expect(page.locator('[data-screen="5"]')).toHaveCount(0);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/#/pages/course-detail/index');
  await page.locator('.course-lesson').first().click();
  await expect(page).toHaveURL(/pages\/player\/index/);
  await expect(page.locator('.card-expansion')).toHaveCount(0);
});
