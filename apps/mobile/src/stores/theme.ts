import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { Theme } from '../../../../contracts/models';

export const palettes = {
  forest: { name: '墨绿', bg: '#0F1E18', surface: '#16302A', primary: '#2F6B52', accent: '#57A184', gold: '#C9A063', text: '#F7F5EF', muted: '#ADC2B5', bar: '#0B1712', line: '#293E33' },
  turquoise: { name: '松石夜色', bg: '#0F1D1F', surface: '#16302F', primary: '#2C8C82', accent: '#3FB3A3', gold: '#E9C08A', text: '#FFFFFF', muted: '#A8C5BD', bar: '#0B191B', line: '#294342' },
  paper: { name: '浅色宣纸', bg: '#F7F5EF', surface: '#FFFFFF', primary: '#0E2A26', accent: '#6F8F7E', gold: '#C9A25A', text: '#0E2A26', muted: '#65736B', bar: '#F7F5EF', line: '#DDE4DC' },
} as const;
export const useThemeStore = defineStore('theme', () => {
  const current = ref<Theme>('turquoise');
  const reducedMotion = ref(false);
  let appliedTabTheme: Theme | null = null;
  const palette = computed(() => palettes[current.value]);
  const gradients = {
    forest: 'linear-gradient(180deg,#16302A 0%,#2F6B52 50%,#0F1E18 100%)',
    turquoise: 'linear-gradient(180deg,#16302F 0%,#2C8C82 50%,#0F1D1F 100%)',
    paper: 'linear-gradient(180deg,#F7F5EF 0%,#E5EDE5 50%,#F7F5EF 100%)',
  };
  const cssVars = computed(() => Object.entries(palette.value).filter(([k]) => k !== 'name').map(([k, v]) => `--${k}:${v}`).join(';') + `;--page-gradient:${gradients[current.value]}`);
  function applyNative() {
    const p = palette.value;
    const tabs = ['home','meditation','courses','practice','profile'];
    const pages = getCurrentPages();
    const route = pages[pages.length - 1]?.route;
    if (tabs.some(name => route === `pages/${name}/index`) && appliedTabTheme !== current.value) {
      // Native tab styling is shared by all tabs. Reassigning five icon pairs
      // on every onShow needlessly redraws the bar during a page switch.
      const applying = current.value;
      appliedTabTheme = applying;
      const retry = () => { if (appliedTabTheme === applying) appliedTabTheme = null; };
      uni.setTabBarStyle({ color: p.muted, selectedColor: p.accent, backgroundColor: p.bar, borderStyle: applying === 'paper' ? 'white' : 'black', fail: retry });
      tabs.forEach((name, index) => {
        uni.setTabBarItem({ index, iconPath: `/static/tabs/${applying}-${name}.png`, selectedIconPath: `/static/tabs/${applying}-${name}-active.png`, fail: retry });
      });
    }
    uni.setBackgroundColor({ backgroundColor: p.bg, backgroundColorTop: p.bg, backgroundColorBottom: p.bg });
    // #ifdef H5
    document.documentElement.style.setProperty('--app-background', p.bg);
    // #endif
  }
  function setTheme(value: Theme) { current.value = value; uni.setStorageSync('cxj.theme', value); applyNative(); }
  function restore() {
    // The PDF is now the visual baseline. Migrate earlier forest previews once.
    if (uni.getStorageSync('cxj.visualBaseline') !== 'pdf-v2') {
      uni.setStorageSync('cxj.theme', 'turquoise');
      uni.setStorageSync('cxj.visualBaseline', 'pdf-v2');
    }
    const value: unknown = uni.getStorageSync('cxj.theme');
    if (typeof value === 'string' && Object.prototype.hasOwnProperty.call(palettes, value)) current.value = value as Theme;
    reducedMotion.value = uni.getStorageSync('cxj.reducedMotion') === true;
  }
  function setReducedMotion(value: boolean) { reducedMotion.value = value; uni.setStorageSync('cxj.reducedMotion', value); }
  return { current, palette, cssVars, reducedMotion, setTheme, restore, applyNative, setReducedMotion };
});
