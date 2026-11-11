// Run after enabling WeChat DevTools automation for the current build:
// node scripts/check-player-transition-native.cjs ws://127.0.0.1:9421
// This checks the native runtime/renderer state via our Vue component callbacks;
// it does not simulate a physical finger or certify device animation frame rate.
const automator = require('miniprogram-automator');
const assert = require('node:assert/strict');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
(async () => {
  const timeout = setTimeout(() => { console.error('Native check timed out'); process.exit(2); }, 45000);
  let mini;
  try {
    mini = await automator.connect({ wsEndpoint: process.argv[2] || 'ws://127.0.0.1:9421' });
    const cases = [
      ['/pages/home/index', '绿度母｜慈心唱诵冥想', false],
      ['/pages/course-detail/index', '01 什么是身体扫描'],
      ['/pages/meditation-detail/index?id=demo-meditation-morning', '开始练习'],
      ['/pages/practice/index', '今日打卡 · 完成 21 分钟冥想'],
      ['/pages/live/index', '秋分昼夜等长冥想夜'],
    ];
    for (const [route, label, scroll = true] of cases) {
      await mini.reLaunch(route);
      if (scroll) await mini.evaluate(() => { wx.pageScrollTo({ scrollTop: 10000, duration: 0 }); });
      await pause(250);
      await mini.evaluate(label => {
        function find(vm) {
          if (vm.$options.__name === 'MotionButton' && vm.$props.ariaLabel === label) return vm;
          for (const child of vm.$children || []) { const result = find(child); if (result) return result; }
        }
        const target = find(getCurrentPages().slice(-1)[0].$vm);
        if (!target) throw Error('Entry missing: ' + label);
        target.$emit('click');
      }, label);
      let result;
      for (let attempt = 0; attempt < 15; attempt++) {
        await pause(300);
        result = await mini.evaluate(() => {
          function find(vm) {
            if (vm.$options.__name === 'PlayerScene') return vm;
            for (const child of vm.$children || []) { const result = find(child); if (result) return result; }
          }
          const page = getCurrentPages().slice(-1)[0];
          const state = find(page.$vm)?.$props.transitionState;
          return { route: page.route, stack: getCurrentPages().length, ready: state?.ready,
            expanded: state?.expanded, origin: state?.expansion?.origin, title: state?.course?.title };
        });
        if (result.ready) break;
      }
      assert(result.ready && result.expanded && result.origin?.width > 0, JSON.stringify(result));
      assert.equal(result.stack, 1, 'Opening the native player must not add a second route');
      await mini.evaluate(() => {
        function find(vm) {
          if (vm.$options.__name === 'PlayerScene') return vm;
          for (const child of vm.$children || []) { const result = find(child); if (result) return result; }
        }
        const player = find(getCurrentPages().slice(-1)[0].$vm);
        player.$children.find(vm => vm.$options.__name === 'ScreenFrame').$props.onBack();
      });
      await pause(200);
      assert(await mini.evaluate(() => {
        function hasPlayer(vm) {
          return vm.$options.__name === 'PlayerScene' || (vm.$children || []).some(hasPlayer);
        }
        return !hasPlayer(getCurrentPages().slice(-1)[0].$vm);
      }), 'The player must be removed after going back');
      console.log('PASS', JSON.stringify(result));
    }
  } catch (error) { console.error(error); process.exitCode = 1; }
  finally { clearTimeout(timeout); if (mini) await mini.disconnect(); }
})();
