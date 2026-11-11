import { test, expect } from '@playwright/test';

test('detail actions preserve local state across playback and membership without purchasing', async ({ page }) => {
  const writes: string[] = [];
  page.on('request', request => { if (['POST','PUT','PATCH','DELETE'].includes(request.method())) writes.push(request.url()); });
  await page.goto('/#/pages/course-detail/index');
  await page.getByRole('button', { name: '关注林涧', exact: true }).click();
  await expect(page.getByRole('button', { name: '取消关注林涧', exact: true })).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button', { name: '收藏', exact: true }).click();
  await expect(page.getByRole('button', { name: '取消收藏', exact: true })).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button', { name: '01 什么是身体扫描', exact: true }).click();
  await expect(page.locator('.player-title')).toHaveText('身体扫描完全指南');
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await expect(page.getByRole('button', { name: '取消收藏', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '02 脚与小腿的觉察', exact: true }).click();
  await expect(page.locator('[data-screen="9"]')).toBeVisible();
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await expect(page.getByRole('button', { name: '取消关注林涧', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '¥68 买断', exact: true }).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('不会提交订单');
  await page.locator('.uni-modal__btn_primary').click();
  await page.evaluate(() => window.scrollTo(0,document.documentElement.scrollHeight));
  await page.getByRole('button', { name: '展开全部 12 节', exact: true }).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('当前为示例目录');
  await page.locator('.uni-modal__btn_primary').click();
  await expect(page.locator('.course-lesson')).toHaveCount(3);
  expect(writes).toEqual([]);
});

test('detail navigation cancels swipes, blocks repeat taps and recovers from failures', async ({ page }) => {
  await page.goto('/#/pages/course-detail/index');
  const lesson = page.locator('.course-lesson').first();
  await lesson.dispatchEvent('touchstart', { touches: [{ identifier:1, clientX:80,clientY:520 }] });
  await expect(lesson).toHaveCSS('opacity','1');
  await lesson.dispatchEvent('touchmove', { touches: [{ identifier:1, clientX:80,clientY:540 }] });
  await lesson.dispatchEvent('touchend', { touches: [] });
  await lesson.dispatchEvent('click');
  await expect(page.locator('.course-detail-title')).toBeVisible();
  await page.evaluate(() => { const app = window as any; app.calls = 0; app.uni.navigateTo = (options: any) => { app.calls++; app.lastNavigation = options; }; });
  await lesson.dispatchEvent('touchstart', { touches: [{ identifier:1, clientX:80,clientY:520 }] });
  await lesson.dispatchEvent('touchend', { touches: [] });
  await lesson.dispatchEvent('click');
  await expect(lesson).toHaveAttribute('aria-disabled','true');
  await expect(lesson).not.toHaveAttribute('disabled',/.*/);
  await lesson.dispatchEvent('click');
  await page.locator('.course-membership').dispatchEvent('click');
  await expect.poll(() => page.evaluate(() => (window as any).calls)).toBe(1);
  await page.evaluate(() => (window as any).lastNavigation.fail());
  await expect(lesson).toHaveAttribute('aria-disabled','false');
  await lesson.dispatchEvent('click');
  await expect.poll(() => page.evaluate(() => (window as any).calls)).toBe(2);
});

test('detail adapts to phone widths and keeps the last chapter and actions clear of the footer', async ({ page }) => {
  for (const [width,height] of [[320,568],[375,812],[430,932]]) {
    await page.setViewportSize({width,height});
    await page.goto('/#/pages/course-detail/index');
    await page.locator('.course-detail-title').waitFor();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    for (const selector of ['.teacher-follow','.course-save','.course-buy','.course-membership','.course-lesson']) {
      const bounds = await page.locator(selector).first().boundingBox();
      expect(bounds!.height).toBeGreaterThanOrEqual(44);
      expect(bounds!.width).toBeGreaterThanOrEqual(44);
    }
    await page.evaluate(() => window.scrollTo(0,document.documentElement.scrollHeight));
    const content = await page.locator('.course-detail-content').boundingBox();
    const footer = await page.locator('.course-footer').boundingBox();
    expect(content!.y + content!.height).toBeLessThanOrEqual(footer!.y);
    expect(footer!.y + footer!.height).toBeLessThanOrEqual(height);
  }
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.getByRole('button',{name:'收藏',exact:true}).click();
  await expect(page.locator('.save-heart')).toHaveCSS('transition-duration','0s');
  await expect(page.locator('.course-detail-content')).toHaveCSS('animation-name','none');
});

