import { test, expect } from '@playwright/test';

test('course filters show one matching preview and preserve selection after details and playback', async ({ page }) => {
  await page.goto('/#/pages/courses/index');
  await expect(page.locator('.course-filter')).toHaveCount(4);
  for (const [filter, title] of [['全部','身体扫描完全指南'], ['入门 7 天','身体扫描完全指南'], ['进阶','行走冥想 · 动中禅'], ['大师课','情绪急救 · 焦虑应对 8 课']]) {
    await page.getByRole('button', { name: filter, exact: true }).click();
    await expect(page.locator('.course-row')).toHaveCount(1);
    await expect(page.locator('.course-row-title')).toHaveText(title);
    await expect(page.getByRole('button', { name: filter, exact: true })).toHaveAttribute('aria-pressed', 'true');
    await page.locator('.course-row').click();
    await expect(page.locator('.course-detail-title')).toHaveText(title);
    if (filter === '进阶' || filter === '大师课') await expect(page.getByRole('button', { name: '价格待确认', exact: true })).toBeVisible();
    await page.getByRole('button', { name: '返回上一页', exact: true }).click();
    await expect(page.getByRole('button', { name: filter, exact: true })).toHaveAttribute('aria-pressed', 'true');
  }
  await page.locator('.featured-course').click();
  await expect(page.locator('.course-detail-title')).toHaveText('21 天正念入门训练营');
  await expect(page.getByRole('button', { name: '¥199 买断', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '01 认识正念，从当下开始', exact: true }).click();
  await expect(page.locator('.player-title')).toHaveText('21 天正念入门训练营');
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await page.locator('.courses-search').click();
  await expect(page.locator('[data-screen="13"]')).toBeVisible();
});

test('course filter, navigation lock and reduced motion keep brightness and layout stable', async ({ page }) => {
  await page.goto('/#/pages/courses/index');
  const top = (await page.locator('.featured-course').boundingBox())!.y;
  const filter = page.getByRole('button', { name: '进阶', exact: true });
  await filter.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 205, clientY: 180 }] });
  await expect(filter).toHaveCSS('opacity', '1');
  await filter.dispatchEvent('touchend', { touches: [] });
  await filter.dispatchEvent('click');
  expect((await page.locator('.featured-course').boundingBox())!.y).toBe(top);
  await page.evaluate(() => { const app = window as any; app.calls = 0; app.uni.navigateTo = () => { app.calls++; }; });
  await page.locator('.course-row').click();
  await expect(filter).toHaveAttribute('aria-disabled', 'true');
  await expect(filter).not.toHaveAttribute('disabled', /.*/);
  await page.locator('.course-row').dispatchEvent('click');
  expect(await page.evaluate(() => (window as any).calls)).toBe(1);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('.filter-pill').first()).toHaveCSS('transition-duration', '0s');
  await expect(page.locator('.courses-content')).toHaveCSS('animation-name', 'none');
});

test('course library fits phone sizes, clears the tab bar and handles unknown detail ids', async ({ page }) => {
  for (const [width,height] of [[320,568],[375,812],[390,844],[430,932]]) {
    await page.setViewportSize({ width,height });
    await page.goto('/#/pages/courses/index');
    await page.getByRole('button', { name: '大师课', exact: true }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    const filters = await page.locator('.course-filter').evaluateAll(nodes => nodes.map(n => { const r=n.getBoundingClientRect(); return { y:r.y,w:r.width,h:r.height }; }));
    expect(filters.every(f => f.y === filters[0].y && f.w >= 44 && f.h >= 44)).toBe(true);
    await page.evaluate(() => window.scrollTo(0,document.documentElement.scrollHeight));
    const row = await page.locator('.course-row').boundingBox();
    const bar = await page.locator('uni-tabbar .uni-tabbar').boundingBox();
    expect(row!.y + row!.height).toBeLessThanOrEqual(bar!.y);
  }
  await page.goto('/#/pages/course-detail/index?courseId=missing');
  await page.reload();
  await expect(page.getByText('暂时找不到这门课程', { exact: true })).toBeVisible();
  await expect(page.locator('.screen-footer')).toHaveCount(0);
});
