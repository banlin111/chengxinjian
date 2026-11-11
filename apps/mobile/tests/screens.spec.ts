import { test, expect } from '@playwright/test';
import { screens } from '../src/config/screens';

for (const width of [320, 430]) {
  test(`all 22 PDF screens render with local assets and no overflow at ${width}px`, async ({ page }) => {
    test.setTimeout(90000);
    await page.setViewportSize({ width, height: width === 320 ? 568 : 932 });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    for (const screen of screens) {
      await page.goto(`/#/pages/${screen.path}/index`);
      await expect(page.locator(screen.id === 1 ? '.hero-title' : `[data-screen="${screen.id}"]`)).toBeVisible();
      await expect.poll(() => page.locator('.page-shell img').evaluateAll(images => images.every(image => (image as HTMLImageElement).complete && (image as HTMLImageElement).naturalWidth > 0))).toBe(true);
      expect(await page.evaluate(() => document.documentElement.scrollWidth), screen.title).toBe(width);
      await expect(page.getByText('这一页正在准备', { exact: true })).toHaveCount(0);
      if (screen.id === 4 || screen.id === 8) {
        await expect(page.locator('.screen-footer')).toHaveCount(1);
        await expect(page.locator('.screen-content')).toHaveCSS('position', 'relative');
        // At the end of a natural page scroll, the entire body must clear the fixed CTA.
        // scrollIntoViewIfNeeded treats content behind a fixed footer as visible.
        await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
        const contentBox = await page.locator('.screen-content').boundingBox();
        const footerBox = await page.locator('.screen-footer').boundingBox();
        expect(contentBox!.y + contentBox!.height).toBeLessThanOrEqual(footerBox!.y);
      }
    }
    expect(errors).toEqual([]);
  });
}

test('course, membership, profile, commerce, and affiliate routes form complete round trips', async ({ page }) => {
  await page.goto('/#/pages/courses/index');
  await page.locator('[data-action="身体扫描完全指南"]').click();
  await expect(page.locator('[data-screen="4"]')).toBeVisible();
  await page.locator('[data-action="开通会员 0 元学"]').click();
  await expect(page.locator('[data-screen="9"]')).toBeVisible();
  await page.locator('[data-action="月卡 ¥25 ¥0.83/天"]').click();
  await expect(page.locator('uni-button').filter({ hasText: '立即续费 ¥25' })).toBeVisible();
  await page.locator('uni-button').filter({ hasText: '立即续费 ¥25' }).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('不会发起支付');
  await page.locator('.uni-modal__btn_primary').click();
  await page.goto('/#/pages/profile/index');
  await page.getByText('澄心商城', { exact: true }).click();
  await expect(page.locator('[data-screen="7"]')).toBeVisible();
  await page.locator('[data-action="藏式铜颂钵"]').click();
  await expect(page.locator('[data-screen="8"]')).toBeVisible();
  await page.locator('[data-action="12cm 进阶款"]').click();
  await expect(page.locator('[data-action="12cm 进阶款"]')).toHaveAttribute('aria-pressed','true');
  await page.goto('/#/pages/affiliate/index');
  await page.locator('[data-action="生成推广海报"]').click();
  await expect(page.locator('[data-screen="20"]')).toBeVisible();
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await page.locator('[data-action="推广人员"]').click();
  await expect(page.locator('[data-screen="21"]')).toBeVisible();
  await page.locator('[data-action="二级 71"]').click();
  await expect(page.getByText('暂无二级好友的示例明细', { exact: true })).toBeVisible();
});

test('search, orders, and commission filters display matching local states', async ({ page }) => {
  await page.goto('/#/pages/search/index');
  await page.locator('input').fill('身体扫描');
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(page.getByText('“身体扫描”的示例结果', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '清除输入', exact: true }).click();
  await page.locator('[data-action="清空搜索历史"]').click();
  await expect(page.getByText('暂无搜索历史', { exact: true })).toBeVisible();
  await page.goto('/#/pages/orders/index');
  await page.locator('[data-action="待付款"]').click();
  await expect(page.getByText('暂无该状态的示例订单', { exact: true })).toBeVisible();
  await expect(page.getByText('天然亚麻冥想垫 · 鼠尾草绿', { exact: true })).toBeHidden();
  await page.goto('/#/pages/commissions/index');
  await page.locator('[data-action="待结算 3"]').click();
  await expect(page.getByText('一级好友 阿泽 购买季卡', { exact: true })).toBeVisible();
  await expect(page.getByText('一级好友 小满 购买年卡', { exact: true })).toBeHidden();
});

test('playback and live controls change UI only; money actions never send writes', async ({ page }) => {
  const writes: string[] = [];
  page.on('request', request => { if (['POST','PUT','PATCH','DELETE'].includes(request.method())) writes.push(request.url()); });
  await page.goto('/#/pages/player/index');
  await page.locator('[data-action="播放状态切换"]').click();
  await expect(page.locator('[data-action="播放状态切换"] img')).toHaveAttribute('src', /pause/);
  await page.locator('[data-action="打开助眠混音"]').click();
  await expect(page.locator('[data-screen="6"]')).toBeVisible();
  await expect(page.locator('uni-slider')).toHaveCount(2);
  await page.getByText('点击添加', { exact: true }).first().click();
  await expect(page.locator('uni-slider')).toHaveCount(3);
  await page.goto('/#/pages/live/index');
  await page.locator('[data-action="提醒我"]').click();
  await expect(page.locator('[data-action="提醒我"]')).toContainText('已预约 · 演示');
  await page.locator('[data-action="进入"]').click();
  await expect(page.locator('[data-screen="17"]')).toBeVisible();
  await page.locator('input').fill('本机评论测试');
  await page.locator('[data-action="发送本机演示评论"]').click();
  await page.locator('.uni-modal__btn_primary').click();
  await expect(page.locator('.local-comment')).toContainText('本机评论测试');
  await page.goto('/#/pages/commissions/index');
  await page.locator('[data-action="去提现"]').click();
  await expect(page.locator('.uni-modal__bd')).toContainText('不会发起提现');
  expect(writes).toEqual([]);
});

