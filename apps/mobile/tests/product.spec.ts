import { test, expect } from '@playwright/test';

test('product specifications and demo checkout agree without sending writes', async ({page}) => {
  const writes: string[] = [];
  page.on('request',request=>{if(['POST','PUT','PATCH','DELETE'].includes(request.method())) writes.push(request.url());});
  await page.goto('/#/pages/product/index');
  await expect(page.getByRole('button',{name:'10cm 入门款',exact:true})).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button',{name:'12cm 进阶款',exact:true}).click();
  await expect(page.locator('.product-spec[aria-pressed="true"]')).toHaveCount(1);
  await expect(page.locator('.product-detail-price')).toHaveText('价格待配置');
  await page.getByRole('button',{name:'立即购买',exact:true}).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('12cm 进阶款');
  await expect(page.locator('.uni-modal__bd')).toContainText('不会提交购物车、生成订单或发起支付');
  await page.locator('.uni-modal__btn_primary').click();
  await expect(page.getByRole('button',{name:'12cm 进阶款',exact:true})).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button',{name:'10cm 入门款',exact:true}).click();
  await expect(page.locator('.product-detail-price')).toHaveText('¥129');
  await page.getByRole('button',{name:'加入购物车',exact:true}).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('10cm 入门款');
  await expect(page.locator('.uni-modal__bd')).toContainText('示例价格 ¥129');
  await page.locator('.uni-modal__btn_primary').click();
  await page.getByRole('button',{name:'查看商品服务说明',exact:true}).click();
  await expect(page.locator('.uni-modal__bd')).toContainText('正式服务规则待商家确认');
  await page.locator('.uni-modal__btn_primary').click();
  expect(writes).toEqual([]);
});

test('product images preview correctly and modal locks prevent repeat actions', async ({page}) => {
  await page.goto('/#/pages/product/index');
  await page.evaluate(()=>{const app=window as any; app.previews=[]; app.uni.previewImage=(options:any)=>app.previews.push(options.urls);});
  await page.getByRole('button',{name:'查看商品大图',exact:true}).click({position:{x:100,y:140}});
  expect(await page.evaluate(()=>(window as any).previews)).toEqual([['/static/prototype/aa74e248a33e.jpg']]);
  await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
  await page.getByRole('button',{name:'查看评价示例图片',exact:true}).click();
  expect(await page.evaluate(()=>(window as any).previews[1])).toEqual(['/static/prototype/8549d3ee0646.jpg']);
  await page.evaluate(()=>{const app=window as any;app.modals=0;app.uni.showModal=(options:any)=>{app.modals++;app.modalOptions=options;};});
  await page.getByRole('button',{name:'加入购物车',exact:true}).click();
  await expect(page.locator('.product-buy')).toHaveAttribute('aria-disabled','true');
  await expect(page.locator('.product-buy')).not.toHaveAttribute('disabled',/.*/);
  await page.locator('.product-buy').dispatchEvent('click');
  expect(await page.evaluate(()=>(window as any).modals)).toBe(1);
  await page.evaluate(()=>(window as any).modalOptions.complete());
  await expect(page.locator('.product-buy')).toHaveAttribute('aria-disabled','false');
});

test('product footer leaves all content reachable on small screens with stable press feedback', async ({page}) => {
  for(const [width,height] of [[320,568],[375,812],[430,932]]) {
    await page.setViewportSize({width,height});
    await page.goto('/#/pages/product/index');
    await page.locator('.product-detail-title').waitFor();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(width);
    for(const selector of ['.product-spec','.product-mini-action','.product-add','.product-buy']) {
      const boxes = await page.locator(selector).evaluateAll(nodes=>nodes.map(n=>{const b=n.getBoundingClientRect();return {w:b.width,h:b.height};}));
      expect(boxes.every(b=>b.w>=44&&b.h>=44)).toBe(true);
    }
    await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
    const content=(await page.locator('.product-detail-content').boundingBox())!;
    const footer=(await page.locator('.product-footer').boundingBox())!;
    expect(content.y+content.height).toBeLessThanOrEqual(footer.y);
    expect(footer.y+footer.height).toBeLessThanOrEqual(height);
  }
  const spec=page.getByRole('button',{name:'14cm 专业款',exact:true});
  await spec.dispatchEvent('touchstart',{touches:[{identifier:1,clientX:240,clientY:450}]});
  await expect(spec).toHaveCSS('opacity','1');
  await spec.dispatchEvent('touchcancel',{touches:[]});
  await page.emulateMedia({reducedMotion:'reduce'});
  await expect(page.locator('.product-spec text, .product-spec uni-text').first()).toHaveCSS('transition-duration','0s');
});
