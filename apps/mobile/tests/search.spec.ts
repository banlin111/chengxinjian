import { test, expect } from '@playwright/test';

test('search matches actual local content, handles no results, and keeps results below the input', async ({ page }) => {
  await page.goto('/#/pages/search/index');
  await page.getByRole('searchbox', { name: '搜索关键词' }).fill('身体扫描');
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(page.locator('.result-title')).toHaveText('身体扫描完全指南');
  const field = await page.locator('.search-toolbar').boundingBox();
  const results = await page.locator('.search-results').boundingBox();
  expect(results!.y).toBeGreaterThan(field!.y + field!.height);
  await page.locator('.search-result-card').click();
  await expect(page.locator('.course-detail-title')).toHaveText('身体扫描完全指南');
  await page.getByRole('button', { name: '返回上一页', exact: true }).click();
  await expect(page.locator('.search-input input')).toHaveValue('身体扫描');
  await page.getByRole('searchbox', { name: '搜索关键词' }).fill('找不到的条目xyz');
  await page.getByRole('searchbox', { name: '搜索关键词' }).press('Enter');
  await expect(page.locator('.empty-title')).toHaveText('暂无匹配的示例内容');
  await expect(page.locator('.search-result-card')).toHaveCount(0);
  await page.getByRole('button', { name: '换个关键词', exact: true }).click();
  await page.getByRole('button', { name: '颂钵音疗', exact: true }).click();
  await expect(page.locator('.result-title')).toHaveText('溪涧入定 · 颂钵长音');
  await page.locator('.search-result-card').click();
  await expect(page.locator('.detail-title')).toHaveText('溪涧入定 · 颂钵长音');
});

test('search history starts empty, persists, deduplicates and can be deleted permanently', async ({ page }) => {
  await page.goto('/#/pages/search/index');
  await expect(page.locator('.history-empty')).toHaveText('暂无搜索历史');
  await page.getByRole('searchbox', { name: '搜索关键词' }).fill('   ');
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(page.locator('.search-results')).toHaveCount(0);
  for (const term of ['身体扫描','颂钵音疗','身体扫描']) {
    await page.getByRole('button', { name: term, exact: true }).click();
    await page.getByRole('button', { name: '清除输入', exact: true }).click();
  }
  await expect(page.locator('.history-label')).toHaveText(['身体扫描','颂钵音疗']);
  await page.reload();
  await expect(page.locator('.history-label')).toHaveText(['身体扫描','颂钵音疗']);
  await page.getByRole('button', { name: '删除历史：身体扫描', exact: true }).click();
  await expect(page.locator('.history-label')).toHaveText(['颂钵音疗']);
  await page.getByRole('button', { name: '搜索历史：颂钵音疗', exact: true }).click();
  await expect(page.locator('.result-title')).toHaveText('溪涧入定 · 颂钵长音');
  await page.getByRole('button', { name: '清除输入', exact: true }).click();
  await page.getByRole('button', { name: '清空搜索历史', exact: true }).click();
  await page.reload();
  await expect(page.locator('.history-empty')).toHaveText('暂无搜索历史');
});

test('cancel returns to the originating course filter and search sends no remote requests', async ({ page }) => {
  const calls: string[] = [];
  page.on('request', request => { if (['xhr','fetch'].includes(request.resourceType()) && !request.url().startsWith('http://localhost:5173')) calls.push(request.url()); });
  await page.goto('/#/pages/courses/index');
  await page.getByRole('button', { name: '进阶', exact: true }).click();
  await page.locator('.courses-search').click();
  await page.getByRole('button', { name: '行走冥想', exact: true }).click();
  await expect(page.locator('.result-title')).toHaveText('行走冥想 · 动中禅');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(page.getByRole('button', { name: '进阶', exact: true })).toHaveAttribute('aria-pressed','true');
  expect(calls).toEqual([]);
});

test('search stays within narrow screens with long terms and keeps history bounded', async ({ page }) => {
  await page.goto('/#/pages/search/index');
  await page.evaluate(() => (window as any).uni.setStorageSync('cxj.search.history', ['林涧 身体扫描 12 节','亚麻冥想垫', null, 123]));
  await page.reload();
  await expect(page.locator('.history-label')).toHaveCount(2);
  for (const [width,height] of [[320,568],[375,812],[430,932]]) {
    await page.setViewportSize({width,height});
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    await page.getByRole('searchbox', { name: '搜索关键词' }).fill('很长的关键词'.repeat(12));
    await page.getByRole('button', { name: '搜索', exact: true }).click();
    await expect(page.locator('.empty-title')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    await page.getByRole('button', { name: '清除输入', exact: true }).click();
  }
  for (let i=0;i<10;i++) {
    await page.getByRole('searchbox', { name: '搜索关键词' }).fill(`示例${i}`);
    await page.getByRole('button', { name: '搜索', exact: true }).click();
    await page.getByRole('button', { name: '清除输入', exact: true }).click();
  }
  await expect(page.locator('.history-label')).toHaveCount(8);
  await expect(page.locator('.history-label').first()).toHaveText('示例9');
});

