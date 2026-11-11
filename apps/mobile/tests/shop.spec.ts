import { test, expect } from '@playwright/test';

test('shop keeps one example per section and opens the matching product', async ({page}) => {
  await page.goto('/#/pages/shop/index');
  await expect(page.locator('.flash-card')).toHaveCount(1);
  await expect(page.locator('.shop-product')).toHaveCount(1);
  await page.locator('.flash-card').click();
  await expect(page.locator('.product-detail-title')).toContainText('藏式手工铜颂钵');
  await expect(page.locator('.product-detail-price')).toHaveText('¥129');
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await page.locator('.shop-product').click();
  await expect(page.locator('.product-detail-title')).toHaveText('天然亚麻冥想垫');
  await expect(page.locator('.product-detail-price')).toHaveText('¥168');
  await expect(page.locator('.product-detail-cover img')).toHaveAttribute('src','/static/prototype/0ad22dca1722.jpg');
  await expect(page.getByText('12cm 进阶款',{exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await page.getByRole('button',{name:'去开通',exact:true}).click();
  await expect(page.locator('[data-screen="9"]')).toBeVisible();
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await page.getByRole('button',{name:'积分兑换',exact:true}).click();
  await expect(page.locator('[data-screen="16"]')).toBeVisible();
  await page.goto('/#/pages/product/index?productId=missing');
  await page.reload();
  await expect(page.getByText('暂时找不到这件商品',{exact:true})).toBeVisible();
  await expect(page.locator('.screen-footer')).toHaveCount(0);
});

test('shop search preserves its results on return and handles absent products honestly', async ({page}) => {
  await page.goto('/#/pages/shop/index');
  await page.getByRole('searchbox',{name:'搜索商城商品'}).fill('冥想垫');
  await page.getByRole('button',{name:'搜索商品',exact:true}).click();
  await expect(page.locator('.flash-card')).toHaveCount(0);
  await expect(page.locator('.shop-product')).toHaveCount(1);
  await page.locator('.shop-product').click();
  await page.getByRole('button',{name:'返回上一页',exact:true}).click();
  await expect(page.getByRole('searchbox',{name:'搜索商城商品'})).toHaveValue('冥想垫');
  await expect(page.locator('.flash-card')).toHaveCount(0);
  await page.getByRole('searchbox',{name:'搜索商城商品'}).fill('不存在的香薰xyz');
  await page.getByRole('searchbox',{name:'搜索商城商品'}).press('Enter');
  await expect(page.locator('.shop-empty-title')).toBeVisible();
  await expect(page.locator('.shop-product')).toHaveCount(0);
  await page.getByRole('button',{name:'查看全部示例',exact:true}).click();
  await expect(page.locator('.flash-card')).toHaveCount(1);
});

test('shop controls stay stable on narrow screens and placeholder actions send no writes', async ({page}) => {
  const writes: string[] = [];
  page.on('request',request=>{if(['POST','PUT','PATCH','DELETE'].includes(request.method())) writes.push(request.url());});
  await page.goto('/#/pages/shop/index');
  for(const name of ['购物车','卡券中心','更多秒杀商品']) {
    await page.getByRole('button',{name,exact:true}).click();
    await expect(page.locator('.uni-modal__bd')).toBeVisible();
    await page.locator('.uni-modal__btn_primary').click();
    await expect(page.locator('[data-screen="7"]')).toBeVisible();
  }
  expect(writes).toEqual([]);
  for(const [width,height] of [[320,568],[375,812],[430,932]]) {
    await page.setViewportSize({width,height});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(width);
    for(const selector of ['.shop-cart','.shop-shortcut','.membership-open','.shop-more']) {
      const boxes=await page.locator(selector).evaluateAll(nodes=>nodes.map(n=>{const b=n.getBoundingClientRect();return {w:b.width,h:b.height};}));
      expect(boxes.every(b=>b.w>=44&&b.h>=44)).toBe(true);
    }
  }
  await page.evaluate(()=>{const app=window as any;app.calls=0;app.uni.navigateTo=()=>{app.calls++;};});
  const card=page.locator('.flash-card');
  await card.dispatchEvent('touchstart',{touches:[{identifier:1,clientX:80,clientY:420}]});
  await expect(card).toHaveCSS('opacity','1');
  await card.dispatchEvent('touchend',{touches:[]});
  await card.dispatchEvent('click');
  await expect(card).toHaveAttribute('aria-disabled','true');
  await expect(card).not.toHaveAttribute('disabled',/.*/);
  await page.locator('.shop-product').dispatchEvent('click');
  expect(await page.evaluate(()=>(window as any).calls)).toBe(1);
  await expect(page.locator('.shop-content')).toHaveCSS('animation-name','none');
});
