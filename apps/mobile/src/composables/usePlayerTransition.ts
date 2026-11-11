import { computed, onUnmounted, reactive, ref } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import { useCardExpansion } from './useCardExpansion';
import { usePlayerPreviewStore, type PlayerPreviewCourse } from '../stores/player-preview';

export interface PlayerTransitionState {
  expansion: { image: string } | null;
  expanded: boolean;
  frameStyle: string;
  coverStyle: string;
  backgroundStyle: string;
  nativeOpen: boolean;
  ready: boolean;
  course: PlayerPreviewCourse | null;
  sourceLabel: string;
}

export function usePlayerTransition(tab = false) {
  const animation = useCardExpansion();
  const preview = usePlayerPreviewStore();
  const nativeOpen = ref(false);
  const ready = ref(false);
  const pending = ref(false);
  const course = ref<PlayerPreviewCourse | null>(null);
  const sourceLabel = ref('');
  let version = 0;
  const { expansion, expanded, frameStyle, coverStyle, backgroundStyle } = animation;
  const state = reactive({ expansion, expanded, frameStyle, coverStyle, backgroundStyle, nativeOpen, ready, course, sourceLabel });
  const busy = computed(() => pending.value || nativeOpen.value);

  function close() {
    version++;
    const restoreTab = nativeOpen.value && tab;
    nativeOpen.value = false;
    ready.value = false;
    pending.value = false;
    animation.reset();
    // #ifdef MP-WEIXIN
    if (restoreTab) uni.showTabBar({ animation: false });
    // #endif
  }
  function open(item: PlayerPreviewCourse, options: {
    from: 'home' | 'course' | 'meditation' | 'replay' | 'practice'; label: string;
    selector: string; artwork?: boolean; fallbackSelector?: string;
  }) {
    if (busy.value) return;
    const current = ++version;
    pending.value = true;
    course.value = { ...item };
    sourceLabel.value = options.label;
    preview.select(item);
    function finish() {
      if (current !== version) return;
      // #ifdef MP-WEIXIN
      ready.value = true;
      pending.value = false;
      // #endif
      // #ifndef MP-WEIXIN
      uni.navigateTo({
        url: `/pages/player/index?courseId=${encodeURIComponent(item.id)}&from=${options.from}`,
        fail: () => { close(); uni.showToast({ title: '暂时无法打开，请重试', icon: 'none' }); },
      });
      // #endif
    }
    function start() {
      if (current !== version) return;
      animation.start(options.selector, item.coverUrl, finish, options);
    }
    // #ifdef MP-WEIXIN
    nativeOpen.value = true;
    ready.value = false;
    if (tab) uni.hideTabBar({ animation: false, complete: start });
    else start();
    // #endif
    // #ifndef MP-WEIXIN
    start();
    // #endif
  }
  onShow(() => {
    if (nativeOpen.value) {
      if (tab) uni.hideTabBar({ animation: false });
    } else close();
  });
  onHide(() => {
    // Preserve an interactive native player across mixer navigation/backgrounding,
    // but cancel unfinished animation so it cannot finish on an inactive page.
    if (!nativeOpen.value || !ready.value) close();
  });
  onUnmounted(close);
  return { state, busy, open, close };
}
