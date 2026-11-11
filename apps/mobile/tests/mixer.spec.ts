import { test, expect, type Page } from '@playwright/test';
async function volume(page: Page, track: string, percent: number) {
  const slider = page.locator(`[data-track="${track}"] .uni-slider-handle-wrapper`);
  const box = (await slider.boundingBox())!;
  const thumb = (await page.locator(`[data-track="${track}"] .uni-slider-thumb`).boundingBox())!;
  await page.mouse.move(thumb.x + thumb.width/2,thumb.y+thumb.height/2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * percent/100 + (percent === 0 ? -20 : 0),box.y+box.height/2,{steps:4});
  await page.mouse.up();
}

test('mixer keeps zero-volume controls and synchronizes the player across returns', async ({page}) => {
  await page.goto('/#/pages/player/index');
  await page.locator('[data-action="打开助眠混音"]').click();
  await expect(page.locator('[data-track="rain"] .track-value')).toHaveText('60%');
  await volume(page,'rain',0);
  await expect(page.locator('[data-track="rain"] .track-value')).toHaveText('0%');
  await expect(page.locator('[data-track="rain"] .track-slider')).toBeVisible();
  await expect(page.locator('[data-track="rain"] .track-tag')).toHaveText('已静音');
  await page.getByRole('button',{name:'添加虫鸣',exact:true}).click();
  await expect(page.locator('.track-slider')).toHaveCount(3);
  await page.getByRole('button',{name:'移除虫鸣',exact:true}).click();
  await expect(page.locator('.track-slider')).toHaveCount(2);
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await expect(page.locator('[data-action="打开助眠混音"]')).toHaveText('雨声 0%');
  await page.locator('[data-action="打开助眠混音"]').click();
  await page.getByRole('button',{name:'移除细雨',exact:true}).click();
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await expect(page.locator('[data-action="打开助眠混音"]')).toHaveText('雨声关闭');
});

test('recipe snapshots survive reload, restore track states and keep sharing a placeholder', async ({page}) => {
  const writes: string[] = [];
  page.on('request', request=>{if(['POST','PUT','PATCH','DELETE'].includes(request.method())) writes.push(request.url());});
  await page.goto('/#/pages/mixer/index');
  await page.getByRole('button',{name:'移除篝火',exact:true}).click();
  await page.getByRole('button',{name:'添加晚风',exact:true}).click();
  await page.getByRole('button',{name:'调整助眠定时',exact:true}).click();
  await page.getByText('关闭定时',{exact:true}).click();
  await page.getByRole('button',{name:'我的配方',exact:true}).click();
  await page.getByText('保存当前配方到本机',{exact:true}).click();
  await expect(page.locator('.recipe .option-note')).toHaveText('已保存 · 点击管理');
  await page.reload();
  await page.getByRole('button',{name:'我的配方',exact:true}).click();
  await page.getByText('恢复本机配方',{exact:true}).click();
  await expect(page.getByRole('button',{name:'添加篝火',exact:true})).toBeVisible();
  await expect(page.locator('[data-track="wind"] .track-value')).toHaveText('30%');
  await expect(page.locator('.mixer-option').first()).toContainText('已关闭');
  await page.getByRole('button',{name:'我的配方',exact:true}).click();
  await page.getByText('分享配方（预留）',{exact:true}).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('不会向他人发送');
  await page.locator('.uni-modal__btn_primary').click();
  expect(writes).toEqual([]);
  await page.evaluate(()=>(window as any).uni.setStorageSync('cxj.mixer.recipe.v1',{volumes:[-1],enabled:[],timer:1}));
  await page.reload();
  await expect(page.locator('.recipe .option-note')).toHaveText('示例配方 · 点击管理');
});

test('mixer fits small phones, stops with silent tracks and honors reduced motion', async ({page}) => {
  await page.goto('/#/pages/mixer/index');
  await page.getByRole('button',{name:'开始助眠演示',exact:true}).click();
  await expect(page.locator('.start-sleep')).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button',{name:'移除细雨',exact:true}).click();
  await page.getByRole('button',{name:'移除篝火',exact:true}).click();
  await expect(page.locator('.start-sleep')).toHaveAttribute('aria-pressed','false');
  await page.getByRole('button',{name:'开始助眠演示',exact:true}).click();
  await expect(page.locator('.start-sleep')).toHaveAttribute('aria-pressed','false');
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.getByRole('button',{name:'添加虫鸣',exact:true}).click();
  await expect(page.locator('.track-slider-reveal').first()).toHaveCSS('transition-duration','0s');
  for(const [width,height] of [[320,568],[375,812],[430,932]]) {
    await page.setViewportSize({width,height});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(width);
    for (const selector of ['.add-track','.remove-track','.mixer-option','.start-sleep']) {
      const boxes = await page.locator(selector).evaluateAll(nodes=>nodes.map(node=>{const b=node.getBoundingClientRect();return {w:b.width,h:b.height};}));
      expect(boxes.every(box=>box.w>=44&&box.h>=44)).toBe(true);
    }
    await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
    const start = (await page.locator('.start-sleep').boundingBox())!;
    expect(start.y+start.height).toBeLessThanOrEqual(height);
  }
});
