// Run from the real COS course probe, while its reviewed fixture still exists.
const { createRequire } = require('node:module');
const path = require('node:path');
const assert = require('node:assert/strict');
const [project, courseId, phase] = process.argv.slice(2);
const { chromium } = createRequire(path.resolve(project, 'package.json'))('@playwright/test');

async function verify() {
  const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const responsePromise = page.waitForResponse(r => r.url() === 'http://127.0.0.1:18082/api/v1/home');
    await page.goto('http://127.0.0.1:17173/#/pages/home/index');
    const response = await responsePromise;
    assert.equal(response.status(), 200);
    const body = await response.json();
    assert.equal(body.code, 'OK');
    assert.ok(body.requestId);
    await page.locator('.home-content').waitFor();
    const cards = [...body.data.recommendedCourses, ...body.data.newCourses];
    const fixture = cards.find(item => item.id === courseId);
    if (phase === 'visible') {
      assert.ok(fixture);
      assert.equal(body.data.recommendedCourses[0].id, courseId);
      assert.equal(await page.locator('.hero-title').innerText(), fixture.title);
      const image = page.locator('#home-hero-cover .hero-image img');
      await image.waitFor({ state: 'attached' });
      await page.waitForFunction(url => {
        const cover = document.querySelector('#home-hero-cover .hero-image img');
        return cover && cover.src === url && cover.complete && cover.naturalWidth > 0;
      }, fixture.coverUrl);
      // Record what the colleague's actual build does. These are observations, not pass criteria for unfinished pages.
      const badge = (await page.locator('#home-hero-cover .vip').allTextContents()).join('').trim();
      const apiPaths = [];
      page.on('request', request => {
        const url = new URL(request.url());
        if (url.origin === 'http://127.0.0.1:18082') apiPaths.push(url.pathname);
      });
      const origin = 'http://127.0.0.1:17173/';
      await page.goto(origin + '#/pages/courses/index');
      await page.locator('.featured-course').waitFor();
      const listCalls = apiPaths.filter(p => p === '/api/v1/courses').length;
      await page.goto(origin + '#/pages/course-detail/index?courseId=' + encodeURIComponent(courseId));
      await page.locator('.course-hero-image,.page-title').first().waitFor();
      const missing = await page.getByText('暂时找不到这门课程', {exact:true}).isVisible();
      console.log(JSON.stringify({ policy:fixture.accessPolicy, displaysVip:badge.includes('VIP'),
        listCalledApi:listCalls > 0, detailCalledApi:apiPaths.includes('/api/v1/courses/' + courseId),
        realDetailShowsMissing:missing }));

    } else {
      assert.equal(phase, 'withdrawn');
      assert.equal(fixture, undefined);
      const rendered = await page.locator('.hero-title,.mini-title').allTextContents();
      assert.deepEqual(rendered, cards.map(item => item.title));
      if (!cards.length) await page.getByText('新内容正在准备中', { exact: true }).waitFor();
    }
  } finally {
    await browser.close();
  }
}
verify().catch(error => { console.error(error.message); process.exitCode = 1; });
