import { test, expect } from '@playwright/test';

test('profile order shortcuts preserve the requested filter', async ({ page }) => {
  for (const [entry, filter] of [['待付款','待付款'],['待发货','待发货'],['待收货','待收货 2'],['退换/售后','售后']]) {
    await page.goto('/#/pages/profile/index');
    await page.getByRole('button', { name: entry, exact: true }).click();
    await expect(page.locator('[data-screen="15"]')).toBeVisible();
    await expect(page.getByRole('button', { name: filter, exact: true })).toHaveAttribute('aria-pressed','true');
    if (entry !== '待收货') await expect(page.getByText('暂无该状态的示例订单', {exact:true})).toBeVisible();
  }
});

test('affiliate level, poster wording and commission routes agree with their entry', async ({ page }) => {
  await page.goto('/#/pages/affiliate/index');
  await page.getByRole('button',{name:'二级好友',exact:true}).click();
  await expect(page.getByRole('button',{name:'二级 71',exact:true})).toHaveAttribute('aria-pressed','true');
  await expect(page.getByText('暂无二级好友的示例明细',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await page.getByRole('button',{name:'本月新增',exact:true}).click();
  await expect(page.locator('[data-screen="22"]')).toBeVisible();
  await page.goto('/#/pages/poster/index');
  await page.locator('.u-choice-group .motion-button').nth(1).click();
  await expect(page.locator('.screen-content')).toContainText('留给自己一段安静的时间');
  await expect(page.locator('.u-choice-group .motion-button').nth(1)).toHaveAttribute('aria-pressed','true');
});

test('live reservation survives room round trip and replay has its own content', async ({ page }) => {
  await page.setViewportSize({width:320,height:568});
  await page.goto('/#/pages/live/index');
  await page.getByRole('button',{name:'提醒我',exact:true}).click();
  await page.getByRole('button',{name:'进入',exact:true}).click();
  await expect(page.getByText('直播画面占位 · 尚未接入直播流',{exact:true})).toBeVisible();
  const send = page.getByRole('button',{name:'发送本机演示评论',exact:true});
  const box = await send.boundingBox();
  expect(box!.y + box!.height).toBeLessThanOrEqual(568);
  await page.getByRole('button',{name:'关注主播',exact:true}).click();
  await expect(page.getByRole('button',{name:'取消关注主播',exact:true})).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await expect(page.getByRole('button',{name:'取消预约',exact:true})).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button',{name:'更多',exact:true}).click();
  await expect(page.locator('.player-title')).toContainText('秋分昼夜等长冥想夜');
});

test('settings persist themes and default speed and feed newly opened players', async ({ page }) => {
  await page.goto('/#/pages/settings/index');
  await page.getByRole('button',{name:'默认倍速',exact:true}).click();
  await page.locator('.uni-actionsheet__cell').filter({hasText:/^1\.5x$/}).click();
  await page.getByRole('button',{name:'浅色宣纸',exact:true}).click();
  await expect(page.locator('.page-shell')).toHaveClass(/theme-paper/);
  await page.reload();
  await expect(page.getByRole('button',{name:'默认倍速',exact:true})).toContainText('1.50x');
  await expect(page.getByRole('button',{name:'浅色宣纸',exact:true})).toHaveAttribute('aria-pressed','true');
  await page.goto('/#/pages/player/index');
  await expect(page.locator('[data-action="切换倍速"]')).toContainText('1.5');
});

test('membership keeps the chosen plan in its demonstration and prevents duplicate modals', async ({ page }) => {
  await page.goto('/#/pages/membership/index');
  await page.getByRole('button',{name:'季卡 ¥68 ¥0.75/天',exact:true}).click();
  await page.getByRole('button',{name:'立即续费 ¥68',exact:true}).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('季卡 · 示例 ¥68');
  await expect(page.getByRole('button',{name:'立即续费 ¥68',exact:true})).toHaveAttribute('aria-disabled','true');
  await page.locator('.uni-modal__btn_primary').click();
  await expect(page.getByRole('button',{name:'立即续费 ¥68',exact:true})).toHaveAttribute('aria-disabled','false');
});
