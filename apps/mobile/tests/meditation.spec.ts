import { test, expect } from '@playwright/test';

const back = async (page: import('@playwright/test').Page) => page.getByRole('button', { name: '返回上一页', exact: true }).click();

test('S2 opens category, detail and matching player in sequence and returns through the same stack', async ({ page }) => {
  await page.goto('/#/pages/meditation/index');
  await expect(page.locator('.mood-button')).toHaveCount(8);
  await expect(page.locator('.meditation-track')).toHaveCount(1);
  await page.getByRole('button', { name: '晨间唤醒', exact: true }).click();
  await expect(page.locator('.category-title')).toHaveText('晨间唤醒');
  await expect(page.locator('.category-track')).toHaveCount(1);
  await page.locator('.category-track').click();
  await expect(page.locator('.detail-title')).toHaveText('山雾晨光 · 呼吸觉察');
  await expect(page.locator('.detail-access')).toContainText('免费');
  await page.getByRole('button', { name: '开始练习', exact: true }).click();
  await expect(page.locator('.player-title')).toHaveText('山雾晨光 · 呼吸觉察');
  await expect(page.locator('.player-times')).toContainText('-15:00');
  await back(page);
  await expect(page.locator('.detail-title')).toHaveText('山雾晨光 · 呼吸觉察');
  await back(page);
  await expect(page.locator('.category-title')).toHaveText('晨间唤醒');
  await back(page);
  await expect(page.locator('.track-title')).toHaveText('森林雨夜 · 深度睡眠引导');
  await page.getByRole('button', { name: '全部', exact: true }).click();
  await expect(page.locator('.category-title')).toHaveText('全部冥想');
  await expect(page.locator('.category-track')).toHaveCount(7);
  await back(page);
  await page.getByRole('button', { name: '更多冥想', exact: true }).click();
  await expect(page.locator('.category-title')).toHaveText('全部冥想');
});

test('S2 cancels scroll presses and recovers from navigation failure', async ({ page }) => {
  await page.goto('/#/pages/meditation/index');
  const banner = page.locator('.sleep-banner');
  await banner.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 100, clientY: 150 }] });
  await expect(banner).toHaveClass(/is-pressed/);
  await banner.dispatchEvent('touchmove', { touches: [{ identifier: 1, clientX: 100, clientY: 180 }] });
  await banner.dispatchEvent('touchend', { touches: [] });
  await banner.dispatchEvent('click');
  await expect(page.locator('.meditation-title')).toBeVisible();
  await page.evaluate(() => { (window as any).uni.navigateTo = (o: any) => o.fail({ errMsg: 'test' }); });
  await banner.click();
  await expect(banner).toHaveAttribute('aria-disabled', 'false');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(banner).toHaveCSS('transform', 'none');
});

test('S2 and its added subpages fit small phones, including invalid links', async ({ page }) => {
  for (const [width, height] of [[320,568], [375,812], [430,932]]) {
    await page.setViewportSize({ width, height });
    await page.goto('/#/pages/meditation/index');
    await expect(page.locator('.track-title')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    const boxes = await page.locator('.mood-button').evaluateAll(nodes => nodes.map(node => {
      const r = node.getBoundingClientRect(); return { x:r.x, y:r.y, width:r.width, height:r.height };
    }));
    expect(boxes[0].y).toBe(boxes[3].y);
    expect(boxes[4].y).toBeGreaterThan(boxes[0].y);
    expect(boxes.every(box => box.width >= 44 && box.height >= 44)).toBe(true);
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    const card = await page.locator('.meditation-track').boundingBox();
    const tab = await page.locator('uni-tabbar .uni-tabbar').boundingBox();
    expect(card!.y + card!.height).toBeLessThanOrEqual(tab!.y);
    for (const url of ['meditation-category/index?category=all', 'meditation-detail/index?id=demo-meditation-sleep']) {
      await page.goto(`/#/pages/${url}`);
      await expect(page.locator('.category-title, .detail-title')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    }
  }
  await page.goto('/#/pages/meditation-category/index?category=invalid');
  await page.reload(); // Exercise a fresh deep link, not a cached hash-router page.
  await expect(page.locator('.category-track')).toHaveCount(0);
  await expect(page.locator('.category-title')).toHaveText('分类不存在');
  await page.goto('/#/pages/meditation-detail/index?id=invalid');
  await page.reload();
  await expect(page.locator('.detail-title')).toContainText('暂时找不到');
  await expect(page.locator('.detail-start')).toHaveCount(0);
});

test('tab returns show settled content without replaying entrance movement', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero-title')).toBeVisible();
  for (const name of ['冥想','课程','练习','我的','推荐','冥想','推荐']) {
    await page.locator('uni-tabbar').getByText(name, { exact: true }).click();
    const content = page.locator('.home-content:visible, .meditation-body:visible, .screen-content:visible').first();
    await expect(content).toBeVisible();
    await expect(content).toHaveCSS('animation-name', 'none');
    await expect(content).toHaveCSS('transform', 'none');
    if (name === '冥想') await expect(page.locator('.track-transition')).toHaveCSS('animation-name', 'none');
  }
});

test('meditation background fills the viewport up to the tab bar', async ({ page }) => {
  for (const [width, height] of [[375,812], [430,932]]) {
    await page.setViewportSize({ width, height });
    await page.goto('/#/pages/meditation/index');
    await expect(page.locator('.meditation-title')).toBeVisible();
    const shell = await page.locator('.page-shell').boundingBox();
    const bar = await page.locator('uni-tabbar .uni-tabbar').boundingBox();
    expect(shell!.y + shell!.height).toBeGreaterThanOrEqual(bar!.y);
    // Model the native viewport: its tab bar is outside the available height.
    await page.locator('.page-shell').evaluate(el => (el as HTMLElement).style.setProperty('--window-bottom', '0px'));
    const nativeShell = await page.locator('.page-shell').boundingBox();
    expect(nativeShell!.height).toBeGreaterThanOrEqual(height);
  }
});

test('mood presses keep brightness stable and lock duplicate navigation without native disabled styling', async ({ page }) => {
  await page.goto('/#/pages/meditation/index');
  const mood = page.getByRole('button', { name: '晨间唤醒', exact: true });
  await mood.dispatchEvent('touchstart', { touches: [{ identifier: 1, clientX: 220, clientY: 350 }] });
  await expect(mood).toHaveClass(/is-pressed/);
  await expect(mood).toHaveCSS('opacity', '1');
  await mood.dispatchEvent('touchend', { touches: [] });
  await page.evaluate(() => {
    const app = window as any;
    app.navigationCalls = 0;
    app.uni.navigateTo = () => { app.navigationCalls++; };
  });
  await mood.dispatchEvent('click');
  for (const button of await page.locator('.mood-button').all()) {
    await expect(button).toHaveAttribute('aria-disabled', 'true');
    await expect(button).not.toHaveAttribute('disabled', /.*/);
    await expect(button).toHaveCSS('opacity', '1');
    await expect(button).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  }
  await mood.dispatchEvent('click');
  await page.getByRole('button', { name: '平复焦虑', exact: true }).dispatchEvent('click');
  expect(await page.evaluate(() => (window as any).navigationCalls)).toBe(1);
});
