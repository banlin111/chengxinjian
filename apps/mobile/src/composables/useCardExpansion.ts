import { computed, getCurrentInstance, nextTick, onUnmounted, ref } from 'vue';
import { onHide } from '@dcloudio/uni-app';
import { useThemeStore } from '../stores/theme';

type Rect = { left: number; top: number; width: number; height: number };
type Expansion = { image: string; origin: Rect; target: Rect; artworkTop: number; artworkOrigin: boolean };
type OriginOptions = { artwork?: boolean; fallbackSelector?: string };
const DURATION = 340;

export function useCardExpansion() {
  const instance = getCurrentInstance();
  const theme = useThemeStore();
  const expansion = ref<Expansion | null>(null);
  const expanded = ref(false);
  const timers = new Set<ReturnType<typeof setTimeout>>();
  let version = 0;
  function later(callback: () => void, ms: number) {
    const timer = setTimeout(() => { timers.delete(timer); callback(); }, ms);
    timers.add(timer);
    return timer;
  }
  function reset() {
    version++;
    timers.forEach(clearTimeout); timers.clear();
    expansion.value = null; expanded.value = false;
  }
  function start(selector: string, image: string, navigate: () => void, options: OriginOptions = {}) {
    reset();
    const current = version;
    let reduce = theme.reducedMotion;
    // #ifdef H5
    reduce ||= window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    // #endif
    if (reduce) { navigate(); return; }
    let measured = false;
    // If native measurement is unavailable, keep the destination reachable.
    const fallback = later(() => { measured = true; if (current === version) navigate(); }, 800);
    uni.createSelectorQuery().in(instance?.proxy)
      .select(selector).boundingClientRect()
      .select('.page-shell').boundingClientRect()
      .select(options.fallbackSelector || selector).boundingClientRect()
      .exec(async (result: [Rect | null, Rect | null, Rect | null]) => {
        if (measured || current !== version) return;
        measured = true; clearTimeout(fallback); timers.delete(fallback);
        const [source, measuredShell, fallbackOrigin] = result;
        const system = uni.getSystemInfoSync();
        let shell = measuredShell;
        let height = system.windowHeight;
        // MP queries cannot cross PageShell's component boundary. The native page
        // spans the window, so there is no need to measure that private element.
        // #ifdef MP-WEIXIN
        shell = { left: 0, top: 0, width: system.windowWidth, height };
        // #endif
        // #ifdef H5
        height = window.innerHeight;
        // #endif
        // A scrolled-off cover must not fly in from outside the viewport.
        const sourceVisible = source && source.width > 0 && source.height > 0 && source.top >= 0 && source.top + source.height <= height;
        const origin = sourceVisible ? source : fallbackOrigin;
        const artworkOrigin = Boolean(sourceVisible && options.artwork !== false);
        if (!origin?.width || origin.height <= 0 || origin.top + origin.height <= 0 || origin.top >= height || !shell?.width || !height) { navigate(); return; }
        let contentTop = 64;
        // #ifdef MP-WEIXIN
        contentTop = Math.max(64, uni.getMenuButtonBoundingClientRect().bottom + 16);
        // #endif
        expansion.value = { image, origin, artworkOrigin, artworkTop: contentTop + 72, target: { left: shell.left, top: 0, width: shell.width, height } };
        await nextTick();
        if (current !== version) return;
        // A Vue tick does not guarantee the WeChat view layer has painted setData.
        // Wait for its selector-query acknowledgement before changing the target.
        // #ifdef MP-WEIXIN
        await new Promise<void>(resolve => {
          // A missing native query callback must not leave the interaction locked.
          const acknowledgement = setTimeout(resolve, 120);
          uni.createSelectorQuery().in(instance?.proxy).select('.expansion-frame')
            .boundingClientRect(() => { clearTimeout(acknowledgement); resolve(); }).exec();
        });
        if (current !== version) return;
        // #endif
        // Let both H5 and the mini-program render the origin before setting the target.
        later(() => {
          if (current !== version) return;
          expanded.value = true;
          later(() => { if (current === version) navigate(); }, DURATION);
        }, 32);
      });
  }
  const frameStyle = computed(() => {
    if (!expansion.value) return '';
    const rect = expanded.value ? expansion.value.target : expansion.value.origin;
    return `left:${rect.left}px;top:${rect.top}px;width:${rect.width}px;height:${rect.height}px;border-radius:${expanded.value ? 0 : 16}px`;
  });
  const coverStyle = computed(() => {
    if (!expansion.value) return '';
    const { origin, target, artworkTop, artworkOrigin } = expansion.value;
    if (!artworkOrigin) {
      const rect = expanded.value ? target : origin;
      return `left:${target.left + (target.width - 168) / 2 - rect.left}px;top:${artworkTop - rect.top}px;width:168px;height:168px;border-radius:84px;opacity:${expanded.value ? 1 : 0}`;
    }
    // Match the source thumbnail to the actual player artwork, not a temporary banner.
    return expanded.value
      ? `left:${(target.width - 168) / 2}px;top:${artworkTop}px;width:168px;height:168px;border-radius:84px`
      : `left:0px;top:0px;width:${origin.width}px;height:${origin.height}px;border-radius:16px`;
  });
  const backgroundStyle = computed(() => {
    if (!expansion.value) return '';
    const { target, origin } = expansion.value;
    const rect = expanded.value ? target : origin;
    // Counter the frame's movement: the real destination remains in final screen coordinates.
    return `left:${target.left - rect.left}px;top:${-rect.top}px;width:${target.width}px;height:${target.height}px`;
  });
  // Native home owns a persistent player surface; hiding the app or opening a
  // player subpage must not remove it. Home clears it on explicit dismissal.
  // #ifndef MP-WEIXIN
  onHide(reset);
  // #endif
  onUnmounted(reset);
  return { expansion, expanded, frameStyle, coverStyle, backgroundStyle, start, reset };
}
