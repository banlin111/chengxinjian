import { test, expect } from '@playwright/test';

test('player choices, progress and favourite respond without starting real audio', async ({ page }) => {
  const media: string[] = [];
  page.on('request', request => { if (request.resourceType() === 'media') media.push(request.url()); });
  await page.goto('/#/pages/player/index');
  await expect(page.locator('.player-times')).toContainText('11:24');
  await expect(page.locator('.player-times')).toContainText('-18:36');
  await page.getByRole('button',{name:'播放状态切换',exact:true}).click();
  await expect(page.locator('.player-halo')).toHaveClass(/is-breathing/);
  await expect(page.locator('.player-artwork')).toHaveCSS('transform','none');
  await page.getByRole('button',{name:'切换倍速',exact:true}).click();
  await page.getByText('1.5 倍速',{exact:true}).click();
  await expect(page.locator('.speed-control')).toHaveText('1.5x');
  await page.getByRole('button',{name:'调整定时',exact:true}).click();
  await page.getByText('关闭定时',{exact:true}).click();
  await expect(page.getByRole('button',{name:'调整定时',exact:true})).toHaveText('定时关闭');
  await page.getByRole('button',{name:'取消喜欢',exact:true}).click();
  await expect(page.locator('.player-like img')).toHaveAttribute('src',/far-heart/);
  await expect(page.locator('.player-like')).toHaveAttribute('aria-pressed','false');
  await page.getByRole('button',{name:'随机播放',exact:true}).click();
  await expect(page.locator('.mode-dot')).toHaveCSS('opacity','1');
  const slider = await page.locator('.player-slider').boundingBox();
  await page.mouse.click(slider!.x + slider!.width * .75,slider!.y + slider!.height / 2);
  await expect(page.locator('.player-times')).not.toContainText('11:24');
  await page.getByRole('button',{name:'下一首',exact:true}).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('一条试听占位');
  await page.locator('.uni-modal__btn_primary').click();
  await page.getByRole('button',{name:'播放状态切换',exact:true}).click();
  await expect(page.locator('.player-halo')).not.toHaveClass(/is-breathing/);
  expect(media).toEqual([]);
});

test('player keeps choices after mixer return, pauses while hidden and respects reduced motion', async ({ page }) => {
  await page.goto('/#/pages/player/index?from=course&courseId=demo-course-body');
  await expect(page.locator('.player-times')).toContainText('-12:30');
  await page.getByRole('button',{name:'播放状态切换',exact:true}).click();
  await page.getByRole('button',{name:'喜欢',exact:true}).click();
  await page.getByRole('button',{name:'雨声 60%',exact:true}).click();
  await expect(page.locator('[data-screen="6"]')).toBeVisible();
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await expect(page.locator('.player-like')).toHaveAttribute('aria-pressed','true');
  await expect(page.locator('.player-toggle')).toHaveAttribute('aria-pressed','false');
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.getByRole('button',{name:'播放状态切换',exact:true}).click();
  await expect(page.locator('.player-halo')).toHaveCSS('animation-name','none');
  await expect(page.locator('.player-content')).toHaveCSS('animation-name','none');
});

test('player fits narrow screens, keeps accessible controls and handles unknown audio', async ({ page }) => {
  for (const [width,height] of [[320,568],[375,812],[430,932]]) {
    await page.setViewportSize({width,height});
    await page.goto('/#/pages/player/index?from=course&courseId=demo-course-camp');
    await expect(page.locator('.player-title')).toHaveText('21 天正念入门训练营');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(width);
    await expect(page.locator('.player-times')).toContainText('时长待配置');
    for (const selector of ['.player-control','.player-toggle','.player-tool']) {
      const boxes = await page.locator(selector).evaluateAll(nodes=>nodes.map(node=>{const b=node.getBoundingClientRect();return {width:b.width,height:b.height};}));
      expect(boxes.every(box=>box.width>=44&&box.height>=44)).toBe(true);
    }
    await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
    const tools = await page.locator('.player-tools').boundingBox();
    expect(tools!.y + tools!.height).toBeLessThanOrEqual(height);
  }
  await page.goto('/#/pages/player/index?from=course&courseId=missing');
  await page.reload();
  await expect(page.getByText('暂时找不到这段音频',{exact:true})).toBeVisible();
  await expect(page.locator('.player-toggle')).toHaveCount(0);
});

