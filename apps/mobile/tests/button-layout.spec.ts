import { test, expect } from '@playwright/test';

test('short text actions stay centered and rectangular on narrow and regular phones', async ({ page }) => {
  const examples = [
    ['practice', '领取'], ['points', '可领取'], ['points', '去完成'],
    ['orders', '确认收货'], ['live', '进入'], ['affiliate', '复制'], ['commissions', '去提现'],
  ];
  for (const width of [320, 375, 430]) {
    await page.setViewportSize({ width, height: 812 });
    for (const [route, name] of examples) {
      await page.goto(`/#/pages/${route}/index`);
      const button = page.getByRole('button', { name, exact: true });
      await expect(button).toBeVisible();
      const geometry = await button.evaluate(element => {
        const box = element.getBoundingClientRect();
        const range = document.createRange();
        range.selectNodeContents(element);
        const label = range.getBoundingClientRect();
        return {
          width: box.width, height: box.height,
          radius: parseFloat(getComputedStyle(element).borderRadius),
          dx: label.left + label.width / 2 - box.left - box.width / 2,
          dy: label.top + label.height / 2 - box.top - box.height / 2,
        };
      });
      expect(geometry.width, `${width}: ${route}/${name}`).toBeGreaterThanOrEqual(72);
      expect(geometry.height).toBeGreaterThanOrEqual(44);
      expect(geometry.radius).toBeLessThan(geometry.height / 2);
      expect(Math.abs(geometry.dx)).toBeLessThanOrEqual(2);
      expect(Math.abs(geometry.dy)).toBeLessThanOrEqual(2);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    }
  }
});

test('practice actions show the reward explanation and open the matching meditation', async ({ page }) => {
  await page.goto('/#/pages/practice/index');
  await page.getByRole('button', { name: '领取', exact: true }).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('不会提交订单');
  await page.locator('.uni-modal__btn_primary').click();
  await page.getByRole('button', { name: '今日打卡 · 完成 21 分钟冥想', exact: true }).click();
  await expect(page.locator('.player-title')).toContainText('慈心唱诵冥想');
  await expect(page.locator('[data-screen="5"]')).toBeVisible();
});
